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
        val deadline = System.nanoTime() / 1_000_000L + 10_000L

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
            val body = requestJson(search, deadline)
            runCatching {
                val results = org.json.JSONArray(body ?: return@runCatching)
                for (index in 0 until results.length()) {
                    val item = results.optJSONObject(index) ?: continue
                    val synced = item.optString("syncedLyrics")
                    if (synced.isNotBlank()) return synced
                    if (plain == null) {
                        plain = item.optString("plainLyrics").takeIf { it.isNotBlank() }
                    }
                }
            }
        }

        return plainToPseudoLrc(plain, durationMs) ?: fetchOvh(title, artist, durationMs, deadline)
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
        for (attempt in 1..2) {
            val remaining = deadline - nowMs()
            if (remaining < 800L) return null
            val result = runCatching {
                val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                    connectTimeout = minOf(4_000L, remaining).toInt()
                    readTimeout = minOf(6_000L, remaining).toInt()
                    instanceFollowRedirects = true
                    setRequestProperty(
                        "User-Agent",
                        "Velvet/1.10.0 (https://github.com/VantaStealth/Velvet-LocalMusicStreaming)"
                    )
                    setRequestProperty("Accept", "application/json")
                }
                try {
                    val code = connection.responseCode
                    if (code == 429 || code >= 500) return@runCatching null
                    if (code !in 200..299) return@runCatching ""
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
            if (result != null) return result.ifEmpty { null }
            if (attempt < 2) Thread.sleep(260L)
        }
        return null
    }

    private fun downloadText(url: String, deadline: Long): String? {
        val remaining = deadline - nowMs()
        if (remaining < 800L) return null
        return runCatching {
            val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = minOf(4_000L, remaining).toInt()
                readTimeout = minOf(6_000L, remaining).toInt()
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Velvet-Mobile")
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun nowMs(): Long = System.nanoTime() / 1_000_000L

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun cacheKey(title: String, artist: String, durationMs: Long): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$title|$artist|${durationMs / 1000}".toByteArray())
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
