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
    val sizeBytes: Long,
    val mirrors: List<String> = emptyList(),
    val lyrics: String? = null
) {
    val sources: List<String>
        get() = (listOf(url) + mirrors).filter { it.isNotBlank() }
}

object CloudApi {

    const val MANIFEST_URL =
        "https://github.com/VantaStealth/Velvet-LocalMusicStreamingPlayer/releases/download/velvet-cloud/manifest.json?v=8"

    // The same manifest is committed to the repository, so a removed release
    // asset does not strand clients that still have working backup mirrors.
    private val manifestUrls = listOf(
        MANIFEST_URL,
        "https://raw.githubusercontent.com/VantaStealth/Velvet-LocalMusicStreamingPlayer/main/tools/velvet-cloud-manifest.json?v=8",
        "https://cdn.jsdelivr.net/gh/VantaStealth/Velvet-LocalMusicStreamingPlayer@main/tools/velvet-cloud-manifest.json?v=8"
    )

    private var cache: List<CloudEntry> = emptyList()
    private var cacheAt = 0L

    fun key(name: String): String =
        name.lowercase().filter { it.isLetterOrDigit() }

    suspend fun manifest(
        force: Boolean = false,
        budgetMs: Long = 20_000L
    ): List<CloudEntry> = withContext(Dispatchers.IO) {
        if (!force && cache.isNotEmpty() && System.currentTimeMillis() - cacheAt < 120_000L) {
            return@withContext cache
        }

        val deadline = System.nanoTime() / 1_000_000L + budgetMs.coerceAtLeast(1_000L)
        var lastError: Exception? = null
        for (source in manifestUrls) {
            val remaining = deadline - System.nanoTime() / 1_000_000L
            if (remaining < 700L) break
            val connection = (URI(source).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = minOf(4_000L, remaining).toInt()
                readTimeout = minOf(6_000L, remaining).toInt()
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Velvet-Mobile")
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    error("Velvet Cloud answered ${connection.responseCode}.")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val tracks = JSONObject(body).optJSONArray("tracks") ?: continue
                val entries = buildList {
                    for (index in 0 until tracks.length()) {
                        val item = tracks.optJSONObject(index) ?: continue
                        val name = item.optString("name")
                        val url = item.optString("url")
                        if (name.isBlank() || url.isBlank()) continue
                        val mirrors = item.optJSONArray("mirrors")?.let { array ->
                            (0 until array.length()).mapNotNull { position ->
                                array.optString(position).takeIf { it.isNotBlank() }
                            }
                        }.orEmpty()
                        add(
                            CloudEntry(
                                name,
                                url,
                                item.optLong("size", 0L),
                                mirrors,
                                item.optString("lyrics").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
                cache = entries
                cacheAt = System.currentTimeMillis()
                return@withContext entries
            } catch (exception: Exception) {
                lastError = exception
            } finally {
                connection.disconnect()
            }
        }
        // A previous manifest is still better than no cloud at all.
        if (cache.isNotEmpty()) return@withContext cache
        throw lastError ?: IllegalStateException("Velvet Cloud is unreachable right now.")
    }

    suspend fun download(
        context: Context,
        entry: CloudEntry,
        destTreeUri: Uri,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val mime = android.webkit.MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(entry.name.substringAfterLast('.', "").lowercase())
                ?: "audio/mpeg"
            var lastError: Exception? = null

            for (source in entry.sources) {
                var connection: HttpURLConnection? = null
                val destination = DocumentFile.fromTreeUri(context, destTreeUri)
                    ?: error("Your music folder is not available any more.")
                val target = destination.findFile(entry.name)
                    ?: destination.createFile(mime, entry.name)
                    ?: error("Could not create ${entry.name}.")

                try {
                    connection = (URI(source).toURL().openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20_000
                        readTimeout = 60_000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "Velvet-Mobile")
                    }
                    if (connection.responseCode !in 200..299) {
                        error("Velvet Cloud answered ${connection.responseCode}.")
                    }
                    val total = entry.sizeBytes.takeIf { it > 0 } ?: connection.contentLengthLong
                    connection.inputStream.use { input ->
                        val output = runCatching {
                            context.contentResolver.openOutputStream(target.uri, "wt")
                        }.getOrNull() ?: context.contentResolver.openOutputStream(target.uri, "w")
                            ?: error("Could not write ${entry.name}.")
                        output.use { sink ->
                            val buffer = ByteArray(256 * 1024)
                            var done = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                sink.write(buffer, 0, read)
                                done += read
                                if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                            }
                            sink.flush()
                        }
                    }
                    onProgress(1f)
                    writeLyricsSidecar(context, destination, entry)
                    return@runCatching
                } catch (exception: Exception) {
                    lastError = exception
                    runCatching { target.delete() }
                } finally {
                    connection?.disconnect()
                }
            }

            throw lastError ?: IllegalStateException("No source is available for ${entry.name}.")
        }
    }

    private fun writeLyricsSidecar(
        context: Context,
        folder: DocumentFile,
        entry: CloudEntry
    ) {
        val url = entry.lyrics?.takeIf { it.isNotBlank() } ?: return
        val lyricName = entry.name.substringBeforeLast('.') + ".lrc"
        runCatching {
            val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Velvet-Mobile")
            }
            try {
                if (connection.responseCode !in 200..299) return
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val target = folder.findFile(lyricName)
                    ?: folder.createFile("text/plain", lyricName)
                    ?: return
                context.contentResolver.openOutputStream(target.uri, "wt")?.use { sink ->
                    sink.write(body.toByteArray())
                }
            } finally {
                connection.disconnect()
            }
        }
    }
}
