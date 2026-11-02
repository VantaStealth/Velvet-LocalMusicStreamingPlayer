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
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

object Lyrics {
    private val stamp = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val tag = Regex("""\[[^\]]*]""")

    fun parse(body: String): List<LyricLine> {
        // [offset:+250] delays every line by 250 ms; a negative value makes the
        // lines arrive earlier. Ignoring it silently pushed whole songs out of
        // sync, so apply it once to the resolved timestamps.
        val offset = Regex("""\[offset:\s*([+-]?\d+)]""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
            ?: 0L
        return buildList {
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
                    val at = minutes * 60_000L + seconds * 1_000L + fraction + offset
                    add(LyricLine(at.coerceAtLeast(0L), text))
                }
            }
        }.sortedBy { it.timeMs }
    }

    /**
     * Older cloud downloads could carry a sidecar whose lines were spread
     * evenly from 8 s across the track. Those files are not real sync data and
     * would show every lyric at the wrong moment.
     */
    private fun looksFabricated(body: String): Boolean {
        val times = mutableListOf<Long>()
        for (line in body.lineSequence()) {
            val match = stamp.find(line) ?: continue
            val minutes = match.groupValues[1].toIntOrNull() ?: continue
            val seconds = match.groupValues[2].toIntOrNull() ?: continue
            val fraction = match.groupValues[3]
                .ifEmpty { "0" }
                .padEnd(3, '0')
                .take(3)
                .toIntOrNull() ?: 0
            times += minutes * 60_000L + seconds * 1_000L + fraction
        }
        if (times.size < 3) return false
        val steps = times.zipWithNext { first, second -> second - first }.filter { it > 0 }
        if (steps.isEmpty()) return false
        return steps.max() - steps.min() <= 2 && kotlin.math.abs(times.first() - 8_000L) <= 2
    }

    /**
     * A sidecar written for another cut of the song (a 34 s clip, a shortened
     * edit or a slowed remix) can never line up with this audio. Reject lyrics
     * whose last line falls far outside the track instead of letting them drift.
     */
    private fun fitsDuration(lines: List<LyricLine>, durationMs: Long): Boolean {
        if (durationMs < 30_000L || lines.isEmpty()) return true
        val last = lines.last().timeMs
        if (last > durationMs + 4_000L) return false
        return durationMs < 90_000L || last >= durationMs / 2L
    }

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

        bundled(context, mediaId, title)?.let { body ->
            val lines = parse(body)
            if (lines.isNotEmpty() && fitsDuration(lines, durationMs)) return@withContext lines
        }

        sidecar(context, mediaId)?.let { body ->
            val lines = parse(body)
            if (lines.isNotEmpty() && fitsDuration(lines, durationMs)) return@withContext lines
        }

        readCache(context, cacheKey(title, artist, durationMs))?.let { body ->
            val lines = parse(body)
            if (lines.isNotEmpty() && fitsDuration(lines, durationMs)) return@withContext lines
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
        val deadline = System.nanoTime() / 1_000_000L + 12_000L

        cloudSidecar(context, mediaId, deadline)?.let { body ->
            val lines = parse(body)
            if (lines.isNotEmpty() && fitsDuration(lines, durationMs)) {
                writeCache(context, key, body)
                return@withContext lines
            }
        }

        val online = fetchOnline(title, artist, album, durationMs, deadline)
            ?: return@withContext emptyList()
        val lines = parse(online)
        if (!fitsDuration(lines, durationMs)) return@withContext emptyList()
        writeCache(context, key, online)
        lines
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

    /**
     * Accurate sidecars ship inside the APK, so a cloud track shows synced
     * lyrics the moment playback starts, even with no network and no download.
     */
    private fun bundled(context: Context, mediaId: String, title: String): String? {
        val uri = Uri.parse(mediaId)
        val name = runCatching { DocumentFile.fromSingleUri(context, uri)?.name }.getOrNull()
            ?: uri.lastPathSegment
        val keys = buildList {
            if (!name.isNullOrBlank()) add(CloudApi.key(name.substringBeforeLast('.')))
            if (title.isNotBlank()) add(CloudApi.key(title))
        }
        for (key in keys.distinct()) {
            if (key.isBlank()) continue
            val body = runCatching {
                context.assets.open("lyrics/$key.lrc").bufferedReader().use { it.readText() }
            }.getOrNull()
            if (!body.isNullOrBlank()) return body
        }
        return null
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
            ?.takeIf { !looksFabricated(it) }
    }.getOrNull()

    private suspend fun cloudSidecar(context: Context, mediaId: String, deadline: Long): String? =
        runCatching {
            val uri = Uri.parse(mediaId)
            val name = DocumentFile.fromSingleUri(context, uri)?.name ?: return@runCatching null
            val entry = CloudApi.manifest(budgetMs = deadline - nowMs())
                .firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: return@runCatching null
            val url = entry.lyrics?.takeIf { it.isNotBlank() } ?: return@runCatching null
            downloadText(url, deadline)?.takeIf { !looksFabricated(it) }
        }.getOrNull()

    private fun fetchOnline(
        title: String,
        artist: String,
        album: String,
        durationMs: Long,
        deadline: Long
    ): String? {
        if (title.isBlank() || artist.isBlank()) return null
        if (artist.equals("Unknown artist", ignoreCase = true)) return null
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
        requestJson(direct, deadline)?.let { body ->
            val item = runCatching { JSONObject(body) }.getOrNull()
            if (item != null && matches(item, title, artist, durationMs)) {
                val synced = item.optString("syncedLyrics")
                if (synced.isNotBlank()) return synced
            }
        }

        if (nowMs() < deadline - 500L) {
            val search = "https://lrclib.net/api/search?q=" + encode("$artist $title")
            pickBest(requestJson(search, deadline), title, artist, durationMs)?.let { return it }
        }
        return null
    }

    /**
     * The direct endpoint can answer with a different recording. Validate the
     * response before trusting it, otherwise a cover's timings get shown over
     * the wrong audio.
     */
    private fun matches(
        item: JSONObject,
        title: String,
        artist: String,
        durationMs: Long
    ): Boolean {
        val titleScore = similarity(normalize(title), normalize(item.optString("trackName")))
        val artistScore = similarity(normalize(artist), normalize(item.optString("artistName")))
        if (titleScore < 0.70 || artistScore < 0.50) return false
        val seconds = item.optLong("duration")
        if (durationMs > 0 && seconds > 0) {
            if (abs(durationMs / 1000.0 - seconds.toDouble()) > 5.0) return false
        }
        return true
    }

    /**
     * LRCLIB search can return covers, karaoke tracks or same-titled songs.
     * Only synced results that match the title, artist and duration survive.
     */
    private fun pickBest(
        body: String?,
        title: String,
        artist: String,
        durationMs: Long
    ): String? {
        if (body.isNullOrBlank()) return null
        val results = runCatching { org.json.JSONArray(body) }.getOrNull() ?: return null
        val wantTitle = normalize(title)
        val wantArtist = normalize(artist)
        var best: String? = null
        var bestScore = 0.0
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val titleScore = similarity(wantTitle, normalize(item.optString("trackName")))
            val artistScore = similarity(wantArtist, normalize(item.optString("artistName")))
            if (titleScore < 0.70) continue
            if (artistScore < 0.50) continue
            val synced = item.optString("syncedLyrics")
            if (synced.isBlank()) continue
            var score = titleScore * 0.5 + artistScore * 0.5
            val seconds = item.optLong("duration")
            if (durationMs > 0 && seconds > 0) {
                val delta = abs(durationMs / 1000.0 - seconds.toDouble())
                if (delta > 5.0) continue
                score += if (delta <= 2.0) 0.22 else 0.10
            }
            if (score > bestScore) {
                bestScore = score
                best = synced
            }
        }
        return if (bestScore >= 0.68) best else null
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

    private fun requestJson(url: String, deadline: Long): String? {
        for (attempt in 0..1) {
            val remaining = deadline - nowMs()
            if (remaining < 800L) return null
            val userAgent =
                "Velvet/2.0.1 (https://github.com/VantaStealth/Velvet-LocalMusicStreamingPlayer)"
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
            .digest("v3|$title|$artist|${durationMs / 1000}".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun cacheDir(context: Context): File =
        File(context.filesDir, "lyrics").apply { mkdirs() }

    private fun readCache(context: Context, key: String): String? = runCatching {
        val file = File(cacheDir(context), "$key.lrc")
        if (file.isFile) file.readText() else null
    }.getOrNull()?.takeIf { !looksFabricated(it) }

    private fun writeCache(context: Context, key: String, body: String) = runCatching {
        File(cacheDir(context), "$key.lrc").writeText(body)
    }.let { }
}
