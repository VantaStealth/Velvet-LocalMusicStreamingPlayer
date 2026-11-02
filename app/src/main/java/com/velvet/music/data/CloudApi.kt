package com.velvet.music.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

data class CloudEntry(
    val name: String,
    val url: String,
    val sizeBytes: Long
)

object CloudApi {

    const val MANIFEST_URL =
        "https://github.com/VantaStealth/Velvet-LocalMusicStreaming/releases/download/velvet-cloud/manifest.json?v=4"

    private var cache: List<CloudEntry> = emptyList()
    private var cacheAt = 0L

    fun key(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() }

    suspend fun manifest(force: Boolean = false): List<CloudEntry> = withContext(Dispatchers.IO) {
        if (!force && cache.isNotEmpty() && System.currentTimeMillis() - cacheAt < 120_000L) {
            return@withContext cache
        }
        val connection = (URI(MANIFEST_URL).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Velvet-Mobile")
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode !in 200..299) {
                error("Velvet Cloud answered ${connection.responseCode}.")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val tracks = root.optJSONArray("tracks") ?: return@withContext emptyList()
            val entries = buildList {
                for (index in 0 until tracks.length()) {
                    val item = tracks.optJSONObject(index) ?: continue
                    val name = item.optString("name")
                    val url = item.optString("url")
                    if (name.isBlank() || url.isBlank()) continue
                    add(CloudEntry(name, url, item.optLong("size", 0L)))
                }
            }
            cache = entries
            cacheAt = System.currentTimeMillis()
            entries
        } finally {
            connection.disconnect()
        }
    }

    suspend fun download(
        context: Context,
        entry: CloudEntry,
        destTreeUri: Uri,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val destination = DocumentFile.fromTreeUri(context, destTreeUri)
                ?: error("Your music folder is not available any more.")
            val mime = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(entry.name.substringAfterLast('.', "").lowercase())
                ?: "audio/mpeg"
            val target = destination.findFile(entry.name) ?: destination.createFile(mime, entry.name)
                ?: error("Could not create ${entry.name}.")

            val connection = (URI(entry.url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Velvet-Mobile")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    error("Velvet Cloud answered ${connection.responseCode}.")
                }
                val total = entry.sizeBytes.takeIf { it > 0 } ?: connection.contentLengthLong
                connection.inputStream.use { source ->
                    val output = runCatching {
                        context.contentResolver.openOutputStream(target.uri, "wt")
                    }.getOrNull() ?: context.contentResolver.openOutputStream(target.uri, "w")
                        ?: error("Could not write ${entry.name}.")
                    output.use { sink ->
                        val buffer = ByteArray(256 * 1024)
                        var done = 0L
                        while (true) {
                            val read = source.read(buffer)
                            if (read <= 0) break
                            sink.write(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                        sink.flush()
                    }
                }
                onProgress(1f)
            } catch (error: Exception) {
                runCatching { target.delete() }
                throw error
            } finally {
                connection.disconnect()
            }
        }
    }
}
