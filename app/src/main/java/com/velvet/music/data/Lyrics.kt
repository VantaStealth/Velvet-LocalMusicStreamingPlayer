package com.velvet.music.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

object Lyrics {
    private val stamp = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val tag = Regex("""\[[^\]]*]""")

    fun parse(body: String): List<LyricLine> = buildList {
        for (raw in body.lineSequence()) {
            val stamps = stamp.findAll(raw).toList()
            if (stamps.isEmpty()) continue
            val text = tag.replace(raw, "").trim()
            if (text.isEmpty()) continue
            for (match in stamps) {
                val minutes = match.groupValues[1].toIntOrNull() ?: continue
                val seconds = match.groupValues[2].toIntOrNull() ?: continue
                val fraction = match.groupValues[3]
                    .ifEmpty { "0" }
                    .padEnd(3, '0')
                    .take(3)
                    .toIntOrNull() ?: 0
                add(LyricLine(minutes * 60_000L + seconds * 1_000L + fraction, text))
            }
        }
    }.sortedBy { it.timeMs }

    /**
     * Sidecar and disk cache only. This is instant, so the player can show local
     * lyrics before any network work starts.
     */
    suspend fun loadLocal(
        context: Context,
        mediaId: String,
        title: String,
        artist: String,
        durationMs: Long
    ): List<LyricLine> = withContext(Dispatchers.IO) {
        if (mediaId.isBlank()) return@withContext emptyList()

        sidecar(context, mediaId)?.let { body ->
            val lines = parse(body)
            if (lines.isNotEmpty()) return@withContext lines
        }

        readCache(context, cacheKey(title, artist, durationMs))?.let { body ->
            val lines = parse(body)
            if (lines.isNotEmpty()) return@withContext lines
        }
        emptyList()
    }

    /**
     * Cloud sidecar first, then a lyrics lookup. The whole pass shares one
     * deadline so a slow catalogue can never leave the pane waiting.
     */
    suspend fun loadRemote(
        context: Context,
        mediaId: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ): List<LyricLine> = withContext(Dispatchers.IO) {
        val key = cacheKey(title, artist, durationMs)
        val deadline = System.nanoTime() / 1_000_000L + 8_000L

        cloudSidecar(context, mediaId, deadline)?.let { body ->
            writeCache(context, key, body)
            val lines = parse(body)
            if (lines.isNotEmpty()) return@withContext lines
        }

        val online = fetchOnline(title, artist, album, durationMs, deadline)
            ?: return@withContext emptyList()
        writeCache(context, key, online)
        parse(online)
    }

    suspend fun load(
        context: Context,
        mediaId: String,
        title: String,
        artist: String,
        album: String,
        durationMs: Long
    ): List<LyricLine> {
        val local = loadLocal(context, mediaId, title, artist, durationMs)
        if (local.isNotEmpty()) return local
        return loadRemote(context, mediaId, title, artist, album, durationMs)
    }

    /** Drops a cached match so "Search again" can pick a better source. */
    suspend fun forget(
        context: Context,
        title: String,
        artist: String,
        durationMs: Long
    ) = withContext(Dispatchers.IO) {
        runCatching {
            File(cacheDir(context), "${cacheKey(title, artist, durationMs)}.lrc").delete()
        }.let { }
    }

    private fun sidecar(context: Context, mediaId: String): String? = runCatching {
        val uri = Uri.parse(mediaId)
        if (uri.scheme != "content") return@runCatching null
        val track = DocumentFile.fromSingleUri(context, uri) ?: return@runCatching null
        val parent = track.parentFile ?: return@runCatching null
        val base = track.name?.substringBeforeLast('.') ?: return@runCatching null
        val lyricFile = parent.findFile("$base.lrc") ?: parent.findFile("$base.LRC")
            ?: return@runCatching null
        context.contentResolver.openInputStream(lyricFile.uri)
            ?.bufferedReader()
            ?.use { it.readText() }
    }.getOrNull()

    private suspend fun cloudSidecar(context: Context, mediaId: String, deadline: Long): String? =
        runCatching {
            val uri = Uri.parse(mediaId)
            val name = DocumentFile.fromSingleUri(context, uri)?.name ?: return@runCatching null
            val entry = CloudApi.manifest(budgetMs = deadline - nowMs())
                .firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: return@runCatching null
            val url = entry.lyrics?.takeIf { it.isNotBlank() } ?: return@runCatching null
            downloadText(url, deadline)
        }.getOrNull()

    private fun fetchOnline(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        deadline: Long
    ): String? {
        if (title.isBlank() || artist.isBlank()) return null
        val seconds = (durationMs / 1000L).toInt()
        val direct = buildString {
            append("https://lrclib.net/api/get?track_name=")
            append(encode(title))
            append("&artist_name=")
            append(encode(artist))
            if (album.isNotBlank()) {
                append("&album_name=")
                append(encode(album))
            }
            if (seconds > 0) append("&duration=$seconds")
        }
        var plain: String? = null
        requestJson(direct, deadline)?.let { body ->
            val item = runCatching { JSONObject(body) }.getOrNull()
            if (item != null) {
                val synced = item.optString("syncedLyrics")
                if (synced.isNotBlank()) return synced
                plain = item.optString("plainLyrics").takeIf { it.isNotBlank() }
            }
        }

        if (nowMs() < deadline - 500L) {
            val search = "https://lrclib.net/api/search?q=" + encode("$artist $title")
            val best = pickBest(requestJson(search, deadline), title, artist, durationMs)
            if (best != null) {
                val synced = best.optString("syncedLyrics")
                if (synced.isNotBlank()) return synced
                if (plain == null) {
                    plain = best.optString("plainLyrics").takeIf { it.isNotBlank() }
                }
            }
        }

        return plainToPseudoLrc(plain, durationMs) ?: fetchOvh(title, artist, durationMs, deadline)
    }

    /**
     * LRCLIB search can return covers, karaoke tracks or same-titled songs.
     * Only accept a result that matches the title, the artist and the duration.
     */
    private fun pickBest(
        body: String?,
        title: String,
        artist: String,
        durationMs: Long
    ): JSONObject? {
        if (body.isNullOrBlank()) return null
        val results = runCatching { org.json.JSONArray(body) }.getOrNull() ?: return null
        val wantTitle = normalize(title)
        val wantArtist = normalize(artist)
        var best: JSONObject? = null
        var bestScore = 0.0
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val titleScore = similarity(wantTitle, normalize(item.optString("trackName")))
            val artistScore = similarity(wantArtist, normalize(item.optString("artistName")))
            if (titleScore < 0.55) continue
            if (artistScore < 0.30) continue
            var score = titleScore * 0.5 + artistScore * 0.5
            val seconds = item.optLong("duration")
            if (durationMs > 0 && seconds > 0) {
                val delta = abs(durationMs / 1000.0 - seconds.toDouble())
                if (delta > 12.0) continue
                score += when {
                    delta <= 2.0 -> 0.22
                    delta <= 5.0 -> 0.12
                    else -> 0.03
                }
            }
            if (item.optString("syncedLyrics").isBlank()) score -= 0.18
            if (score > bestScore) {
                bestScore = score
                best = item
            }
        }
        return if (bestScore >= 0.55) best else null
    }

    private fun similarity(left: String, right: String): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        if (left == right) return 1.0
        if (left.contains(right) || right.contains(left)) return 0.9
        val leftTokens = left.split(' ').filter { it.isNotBlank() }.toSet()
        val rightTokens = right.split(' ').filter { it.isNotBlank() }.toSet()
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0.0
        val shared = leftTokens.count { it in rightTokens }
        return shared.toDouble() / maxOf(leftTokens.size, rightTokens.size)
    }

    private fun normalize(value: String): String {
        var text = value.lowercase()
        text = text.replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
        text = text.replace(Regex("""\b(feat|ft|featuring|with)\b\.?.*$"""), " ")
        text = text.replace(
            Regex("""\b(remaster(ed)?|radio edit|explicit|deluxe|bonus track|version)\b"""),
            " "
        )
        text = text.replace(Regex("""[^a-z0-9]+"""), " ")
        return text.trim().replace(Regex("""\s+"""), " ")
    }

    private fun fetchOvh(
        title: String,
        artist: String,
        durationMs: Long,
        deadline: Long
    ): String? {
        if (nowMs() >= deadline - 500L) return null
        val body = requestJson(
            "https://api.lyrics.ovh/v1/${encode(artist)}/${encode(title)}",
            deadline
        ) ?: return null
        val lyrics = runCatching { JSONObject(body).optString("lyrics") }.getOrNull()
        return plainToPseudoLrc(lyrics?.takeIf { it.isNotBlank() }, durationMs)
    }

    private fun plainToPseudoLrc(plain: String?, durationMs: Long): String? {
        val lines = plain?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(260)
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val totalMs = if (durationMs > 0) durationMs else lines.size * 3200L
        val start = minOf(8_000.0, totalMs * 0.05)
        val end = totalMs * 0.94
        val step = maxOf(900.0, (end - start) / lines.size)
        return lines.mapIndexed { index, line ->
            val atMs = (start + step * index).toLong()
            val minutes = atMs / 60_000
            val seconds = (atMs % 60_000) / 1_000
            val millis = atMs % 1_000
            "[%02d:%02d.%03d]%s".format(Locale.US, minutes, seconds, millis, line)
        }.joinToString("\n")
    }

    private fun requestJson(url: String, deadline: Long): String? {
        for (attempt in 0..1) {
            val remaining = deadline - nowMs()
            if (remaining < 800L) return null
            val userAgent =
                "Velvet/2.0.0 (https://github.com/VantaStealth/Velvet-LocalMusicStreamingPlayer)"
            val result = runCatching {
                val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                    connectTimeout = minOf(4_000L, remaining).toInt()
                    readTimeout = minOf(2_500L, remaining).toInt()
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", userAgent)
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val code = connection.responseCode
                    if (code == 429 || code >= 500) null
                    else if (code !in 200..299) ""
                    else readBody(connection, deadline)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
            if (result != null) return result.ifEmpty { null }
            if (attempt == 0) Thread.sleep(220L)
        }
        return null
    }

    /**
     * Reads in chunks and gives up at the shared deadline, so a server that
     * trickles bytes cannot keep the lyrics pane waiting forever.
     */
    private fun readBody(connection: HttpURLConnection, deadline: Long): String? {
        val remaining = (deadline - nowMs()).coerceAtLeast(1_000L)
        connection.connectTimeout = minOf(4_000L, remaining).toInt()
        connection.readTimeout = minOf(2_500L, remaining).toInt()
        val reader = connection.inputStream.bufferedReader()
        try {
            val buffer = CharArray(8_192)
            val body = StringBuilder()
            while (true) {
                if (nowMs() >= deadline) return null
                val read = reader.read(buffer)
                if (read < 0) break
                body.append(buffer, 0, read)
                if (body.length > 400_000) break
            }
            return body.toString()
        } finally {
            runCatching { reader.close() }
        }
    }

    private fun downloadText(url: String, deadline: Long): String? {
        val remaining = deadline - nowMs()
        if (remaining < 800L) return null
        return runCatching {
            val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = minOf(4_000L, remaining).toInt()
                readTimeout = minOf(2_500L, remaining).toInt()
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Velvet-Mobile")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                readBody(connection, deadline)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun nowMs(): Long = System.nanoTime() / 1_000_000L

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun cacheKey(title: String, artist: String, durationMs: Long): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("v2|$title|$artist|${durationMs / 1000}".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun cacheDir(context: Context): File =
        File(context.filesDir, "lyrics").apply { mkdirs() }

    private fun readCache(context: Context, key: String): String? = runCatching {
        val file = File(cacheDir(context), "$key.lrc")
        if (file.isFile) file.readText() else null
    }.getOrNull()

    private fun writeCache(context: Context, key: String, body: String) = runCatching {
        File(cacheDir(context), "$key.lrc").writeText(body)
    }.let { }
}
