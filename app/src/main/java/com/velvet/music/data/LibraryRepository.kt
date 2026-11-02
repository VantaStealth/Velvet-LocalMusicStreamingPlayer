package com.velvet.music.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.documentfile.provider.DocumentFile
import com.velvet.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min

class LibraryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val artworkDir = File(appContext.filesDir, "artwork").apply { mkdirs() }
    private val libraryFile = File(appContext.filesDir, "library.json")
    private val coverSerif: Typeface by lazy {
        ResourcesCompat.getFont(appContext, R.font.instrument_serif_regular) ?: Typeface.SERIF
    }
    private val coverSans: Typeface by lazy {
        ResourcesCompat.getFont(appContext, R.font.poppins_medium)
            ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    suspend fun loadLibrary(): List<Track> = withContext(Dispatchers.IO) {
        runCatching {
            if (!libraryFile.exists()) return@runCatching emptyList()
            val array = JSONArray(libraryFile.readText())
            (0 until array.length()).mapNotNull { fromJson(array.optJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    suspend fun saveLibrary(tracks: List<Track>) = withContext(Dispatchers.IO) {
        runCatching {
            val array = JSONArray()
            tracks.forEach { array.put(toJson(it)) }
            val tmp = File(appContext.filesDir, "library.json.tmp")
            tmp.writeText(array.toString())
            if (libraryFile.exists()) libraryFile.delete()
            tmp.renameTo(libraryFile)
        }
    }

    suspend fun scan(
        treeUri: Uri,
        onProgress: (done: Int, total: Int, name: String) -> Unit
    ): List<Track> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(appContext, treeUri)
            ?: error("This folder can't be opened any more.")
        if (!root.canRead()) error("Velvet lost access to this folder.")

        val files = mutableListOf<DocumentFile>()
        collectAudioFiles(root, files, 0)
        val total = files.size
        if (total == 0) return@withContext emptyList()

        val previous = loadLibrary().associateBy { it.uri }
        val catalog = CloudApi.catalog(appContext)
        val done = AtomicInteger(0)
        val gate = Semaphore(4)

        val scanned = coroutineScope {
            files.map { doc ->
                async {
                    gate.withPermit {
                        val key = doc.uri.toString()
                        val cached = previous[key]?.takeIf { track ->
                            track.artPath == null || File(track.artPath).exists()
                        }
                        val track = cached ?: extractTrack(doc)
                        val resolved = track?.let { applyCloudTags(it, doc.name, catalog) }
                        val count = done.incrementAndGet()
                        onProgress(count, total, doc.name ?: resolved?.title ?: "")
                        resolved
                    }
                }
            }.awaitAll()
        }.filterNotNull()

        scanned.sortedWith(
            compareBy<Track> { it.title.lowercase() }
                .thenBy { it.artist.lowercase() }
        )
    }

    suspend fun backfillArtwork(tracks: List<Track>): List<Track> = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext tracks
        val needsWork = tracks.any { track ->
            track.artPath?.let { path -> File(path).exists() } != true
        }
        if (!needsWork) return@withContext tracks
        val gate = Semaphore(4)
        val updated = coroutineScope {
            tracks.map { track ->
                async {
                    gate.withPermit { ensureCover(track) }
                }
            }.awaitAll()
        }
        saveLibrary(updated)
        updated
    }

    /**
     * Some devices hand back no tags for a SAF document, which left cloud
     * tracks labelled "Unknown artist" even though the manifest knows better.
     * A freshly saved catalogue is applied without waiting for the next scan.
     */
    suspend fun retagFromCatalog(tracks: List<Track>): List<Track> = withContext(Dispatchers.IO) {
        if (tracks.isEmpty()) return@withContext tracks
        val catalog = CloudApi.catalog(appContext)
        if (catalog.isEmpty()) return@withContext tracks
        val updated = tracks.map { track ->
            val name = runCatching {
                Uri.parse(track.uri).lastPathSegment?.substringAfterLast('/')
            }.getOrNull()
            applyCloudTags(track, name, catalog)
        }
        if (updated != tracks) saveLibrary(updated)
        updated
    }

    private fun applyCloudTags(
        track: Track,
        fileName: String?,
        catalog: Map<String, CloudEntry>
    ): Track {
        val name = fileName ?: return track
        val entry = catalog[CloudApi.key(name)] ?: return track
        val unknownArtist = track.artist.isBlank() ||
            track.artist.equals("Unknown artist", ignoreCase = true)
        val rawTitle = name.substringBeforeLast('.').trim()
        val titleIsRaw = track.title.isBlank() ||
            track.title.equals(rawTitle, ignoreCase = true) ||
            track.title.equals(name, ignoreCase = true)
        if (!unknownArtist && !titleIsRaw) return track
        return track.copy(
            title = if (titleIsRaw && !entry.title.isNullOrBlank()) entry.title else track.title,
            artist = if (unknownArtist && !entry.artist.isNullOrBlank()) entry.artist else track.artist,
            album = if (track.album.isBlank() ||
                track.album.equals("Unknown album", ignoreCase = true)
            ) entry.album ?: track.album else track.album
        )
    }

    private fun ensureCover(track: Track): Track {
        if (track.artPath?.let { File(it).exists() } == true) return track
        val seed = trackSeed(track.title, track.artist)
        val cover = generateCover(
            seed = seed,
            coverColor = paletteColor(seed),
            monogram = monogram(track.title)
        ) ?: return track
        return track.copy(
            artPath = cover.first,
            artUri = cover.second,
            color = paletteColor(seed)
        )
    }

    private fun trackSeed(title: String, artist: String) =
        "track:${title.trim().lowercase()}|${artist.trim().lowercase()}"

    private fun collectAudioFiles(dir: DocumentFile, out: MutableList<DocumentFile>, depth: Int) {
        if (depth > 24) return
        val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            if (child.isDirectory) {
                collectAudioFiles(child, out, depth + 1)
            } else if (child.isFile && isAudio(child.name.orEmpty())) {
                out.add(child)
            }
        }
    }

    private fun extractTrack(doc: DocumentFile): Track? {
        val uri = doc.uri
        val fileName = doc.name ?: return null
        val rawName = fileName.substringBeforeLast('.').trim().ifEmpty { fileName }
        val decorated = rawName.replace(Regex("^\\d{1,3}[\\s._-]+"), "")
        val segments = decorated.split(" - ").map { it.trim() }.filter { it.isNotEmpty() }
        val fallbackTitle = when {
            segments.size >= 2 -> segments.last()
            else -> decorated.ifEmpty { rawName }
        }
        val fallbackArtist = if (segments.size >= 2) segments.first() else "Unknown artist"
        val folderAlbum = runCatching { doc.parentFile?.name?.trim() }
            .getOrNull()?.takeIf { it.isNotEmpty() } ?: "Unknown album"
        val retriever = MediaMetadataRetriever()
        var title = fallbackTitle
        var artist = fallbackArtist
        var album = folderAlbum
        var duration = 0L
        var trackNumber = 0
        var year = 0
        var artPath: String? = null
        var artUri: String? = null
        var color = paletteColor(fallbackTitle)

        try {
            retriever.setDataSource(appContext, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.clean()?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.clean()?.let { artist = it }
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?.clean()?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.clean()?.let { album = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.let { duration = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull()?.let { trackNumber = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.trim()?.take(4)?.toIntOrNull()?.let { year = it }

            val embedded = retriever.embeddedPicture
            if (embedded != null && embedded.isNotEmpty()) {
                val artwork = storeArtwork(uri.toString(), embedded)
                if (artwork != null) {
                    artPath = artwork.first
                    artUri = artwork.second
                    color = artwork.third
                }
            }
        } catch (_: Exception) {
            // Keep the filename based fallbacks – unreadable tags should not hide the file.
        } finally {
            runCatching { retriever.release() }
        }

        if (artPath == null) {
            val seed = trackSeed(title, artist)
            color = paletteColor(seed)
            generateCover(
                seed = seed,
                coverColor = color,
                monogram = monogram(title)
            )?.let { cover ->
                artPath = cover.first
                artUri = cover.second
            }
        }

        return Track(
            id = uri.toString(),
            uri = uri.toString(),
            title = title,
            artist = artist,
            album = album,
            durationMs = duration,
            trackNumber = trackNumber,
            year = year,
            artUri = artUri,
            artPath = artPath,
            color = color,
            addedAt = System.currentTimeMillis()
        )
    }

    private fun storeArtwork(key: String, bytes: ByteArray): Triple<String, String, Int>? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return null

            val largest = max(decoded.width, decoded.height)
            val scaled = if (largest > 640) {
                val ratio = 640f / largest
                Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).toInt(), (decoded.height * ratio).toInt(), true)
                    .also { if (it != decoded) decoded.recycle() }
            } else decoded

            val color = dominantColor(scaled)
            val outFile = File(artworkDir, sha1(key) + ".jpg")
            val jpeg = ByteArrayOutputStream().use { stream ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, stream)
                stream.toByteArray()
            }
            outFile.writeBytes(jpeg)
            scaled.recycle()
            val uri = FileProvider.getUriForFile(
                appContext, "${appContext.packageName}.fileprovider", outFile
            )
            Triple(outFile.absolutePath, uri.toString(), color)
        }.getOrNull()
    }

    private fun dominantColor(bitmap: Bitmap): Int {
        val size = 24
        val small = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = IntArray(size * size)
        small.getPixels(pixels, 0, size, 0, 0, size, size)
        if (small != bitmap) small.recycle()

        var r = 0.0
        var g = 0.0
        var b = 0.0
        var weightSum = 0.0
        var fallbackR = 0.0
        var fallbackG = 0.0
        var fallbackB = 0.0

        for (pixel in pixels) {
            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)
            fallbackR += red
            fallbackG += green
            fallbackB += blue
            val maxChannel = maxOf(red, green, blue)
            val minChannel = minOf(red, green, blue)
            val saturation = (maxChannel - minChannel) / 255.0
            val luminance = (0.299 * red + 0.587 * green + 0.114 * blue) / 255.0
            if (luminance < 0.06 || luminance > 0.94) continue
            val weight = 0.2 + saturation * saturation * 2.2
            r += red * weight
            g += green * weight
            b += blue * weight
            weightSum += weight
        }

        val colorInt = if (weightSum > 0) {
            Color.rgb(
                (r / weightSum).toInt().coerceIn(0, 255),
                (g / weightSum).toInt().coerceIn(0, 255),
                (b / weightSum).toInt().coerceIn(0, 255)
            )
        } else {
            val count = pixels.size.toDouble()
            Color.rgb(
                (fallbackR / count).toInt().coerceIn(0, 255),
                (fallbackG / count).toInt().coerceIn(0, 255),
                (fallbackB / count).toInt().coerceIn(0, 255)
            )
        }

        val hsv = FloatArray(3)
        Color.colorToHSV(colorInt, hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.42f)
        hsv[2] = hsv[2].coerceIn(0.62f, 0.92f)
        return Color.HSVToColor(hsv)
    }

    private fun generateCover(seed: String, coverColor: Int, monogram: String): Pair<String, String>? =
        runCatching {
            val output = File(artworkDir, "cover_${sha1(seed)}.jpg")
            if (output.exists() && output.length() > 0L) {
                return@runCatching output.absolutePath to fileUri(output)
            }
            val size = 560
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                val hsv = FloatArray(3)
                Color.colorToHSV(coverColor, hsv)
                val deep = Color.HSVToColor(
                    floatArrayOf(hsv[0], (hsv[1] * 1.05f).coerceIn(0.35f, 1f), 0.19f)
                )
                val mid = Color.HSVToColor(
                    floatArrayOf(hsv[0], (hsv[1] * 0.9f).coerceIn(0.30f, 1f), 0.36f)
                )
                val base = Color.rgb(7, 6, 10)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                paint.shader = LinearGradient(
                    0f, 0f, size.toFloat(), size.toFloat(),
                    intArrayOf(mid, deep, base), floatArrayOf(0f, 0.55f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
                paint.shader = null

                val glowX = size * 0.78f
                val glowY = size * 0.20f
                paint.shader = RadialGradient(
                    glowX, glowY, size * 0.64f,
                    intArrayOf(
                        Color.argb(
                            155,
                            Color.red(coverColor),
                            Color.green(coverColor),
                            Color.blue(coverColor)
                        ),
                        Color.TRANSPARENT
                    ),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawCircle(glowX, glowY, size * 0.64f, paint)
                paint.shader = null

                paint.shader = RadialGradient(
                    size * 0.06f, size * 0.96f, size * 0.50f,
                    intArrayOf(Color.argb(46, 232, 201, 122), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawCircle(size * 0.06f, size * 0.96f, size * 0.50f, paint)
                paint.shader = null

                val discCx = size * 0.80f
                val discCy = size * 0.46f
                val discR = size * 0.365f
                val discPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                discPaint.shader = RadialGradient(
                    discCx - discR * 0.30f, discCy - discR * 0.30f, discR * 1.3f,
                    intArrayOf(
                        Color.rgb(31, 26, 34),
                        Color.rgb(10, 8, 13),
                        Color.rgb(4, 3, 6)
                    ),
                    floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
                )
                canvas.drawCircle(discCx, discCy, discR, discPaint)
                discPaint.shader = null

                val groove = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.1f
                }
                for (ring in 0 until 22) {
                    groove.color = Color.argb(8 + (ring % 4) * 4, 255, 255, 255)
                    canvas.drawCircle(discCx, discCy, discR * (0.14f + ring * 0.038f), groove)
                }

                val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    shader = LinearGradient(
                        discCx - discR, discCy - discR, discCx + discR, discCy + discR,
                        intArrayOf(
                            Color.argb(165, 250, 237, 199),
                            Color.argb(55, 212, 175, 55),
                            Color.TRANSPARENT
                        ),
                        null, Shader.TileMode.CLAMP
                    )
                }
                val discBounds = RectF(discCx - discR, discCy - discR, discCx + discR, discCy + discR)
                canvas.drawCircle(discCx, discCy, discR, rim)
                canvas.drawArc(discBounds, 196f, 118f, false, rim)
                rim.shader = null

                val gloss = Paint(Paint.ANTI_ALIAS_FLAG)
                gloss.shader = LinearGradient(
                    discCx - discR, discCy - discR, discCx + discR * 0.45f, discCy + discR,
                    intArrayOf(Color.argb(42, 255, 255, 255), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawCircle(discCx, discCy, discR, gloss)
                gloss.shader = null

                val labelR = discR * 0.34f
                val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = coverColor }
                canvas.drawCircle(discCx, discCy, labelR, label)
                label.style = Paint.Style.STROKE
                label.strokeWidth = 2.4f
                label.color = Color.argb(115, 250, 237, 199)
                canvas.drawCircle(discCx, discCy, labelR, label)
                label.style = Paint.Style.FILL
                label.color = Color.argb(220, 7, 6, 10)
                canvas.drawCircle(discCx, discCy, discR * 0.035f, label)

                val type = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = coverSerif
                    textSize = size * 0.30f
                    letterSpacing = -0.012f
                }
                val textX = size * 0.075f
                val textBaseline = size * 0.555f
                type.color = Color.argb(110, 0, 0, 0)
                canvas.drawText(monogram, textX + 2.5f, textBaseline + 3f, type)
                type.color = Color.argb(240, 247, 240, 227)
                canvas.drawText(monogram, textX, textBaseline, type)

                val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(175, 232, 201, 122)
                    strokeWidth = 1.7f
                }
                canvas.drawLine(textX, size * 0.605f, textX + size * 0.17f, size * 0.605f, rule)

                val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    typeface = coverSans
                    textSize = size * 0.026f
                    letterSpacing = 0.34f
                    color = Color.argb(190, 232, 201, 122)
                }
                canvas.drawText("VELVET PRESSING", textX, size * 0.668f, caption)

                val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(215, 247, 240, 227)
                }
                val star = android.graphics.Path()
                val starX = size * 0.90f
                val starY = size * 0.11f
                val starR = size * 0.018f
                star.moveTo(starX, starY - starR * 2.2f)
                star.lineTo(starX + starR * 0.6f, starY - starR * 0.6f)
                star.lineTo(starX + starR * 2.2f, starY)
                star.lineTo(starX + starR * 0.6f, starY + starR * 0.6f)
                star.lineTo(starX, starY + starR * 2.2f)
                star.lineTo(starX - starR * 0.6f, starY + starR * 0.6f)
                star.lineTo(starX - starR * 2.2f, starY)
                star.lineTo(starX - starR * 0.6f, starY - starR * 0.6f)
                star.close()
                canvas.drawPath(star, starPaint)

                val random = kotlin.random.Random(seed.hashCode())
                val grain = Paint().apply { strokeWidth = 1.3f }
                repeat(760) {
                    grain.color = if (random.nextBoolean()) {
                        Color.argb(6, 255, 255, 255)
                    } else {
                        Color.argb(7, 0, 0, 0)
                    }
                    canvas.drawPoint(random.nextFloat() * size, random.nextFloat() * size, grain)
                }

                val vignette = Paint(Paint.ANTI_ALIAS_FLAG)
                vignette.shader = RadialGradient(
                    size * 0.5f, size * 0.47f, size * 0.74f,
                    intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(170, 0, 0, 0)),
                    floatArrayOf(0f, 0.58f, 1f), Shader.TileMode.CLAMP
                )
                canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), vignette)

                val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    color = Color.argb(26, 255, 255, 255)
                }
                canvas.drawRect(13f, 13f, size - 13f, size - 13f, edge)

                val jpeg = ByteArrayOutputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 86, stream)
                    stream.toByteArray()
                }
                output.writeBytes(jpeg)
            } finally {
                bitmap.recycle()
            }
            output.absolutePath to fileUri(output)
        }.getOrNull()

    private fun monogram(source: String): String {
        val cleaned = source.replace(Regex("\\(.*?\\)|\\[.*?\\]"), " ").trim()
        val ignored = setOf(
            "the", "a", "an", "and", "of", "for", "to", "in", "on",
            "feat", "ft", "with", "unknown", "album", "untitled"
        )
        val words = cleaned.split(Regex("[\\s._\\-/]+")).filter {
            it.isNotBlank() && it.lowercase() !in ignored
        }
        val initials = words.mapNotNull { word ->
            word.firstOrNull { it.isLetterOrDigit() }
        }.take(2)
        return when {
            initials.size >= 2 -> initials.joinToString("").uppercase()
            initials.size == 1 -> cleaned.filter { it.isLetterOrDigit() }
                .take(2).uppercase().ifEmpty { "V" }
            else -> "V"
        }
    }

    private fun fileUri(file: File): String = FileProvider.getUriForFile(
        appContext, "${appContext.packageName}.fileprovider", file
    ).toString()

    private fun paletteColor(seedText: String): Int {
        val palettes = intArrayOf(
            0xFF8B5CF6.toInt(), 0xFFA78BFA.toInt(), 0xFF6D28D9.toInt(),
            0xFFD4AF37.toInt(), 0xFFE8C97A.toInt(), 0xFFB8863B.toInt(),
            0xFFE11D48.toInt(), 0xFFFB7185.toInt(), 0xFF9F1239.toInt(),
            0xFF3B82F6.toInt(), 0xFF60A5FA.toInt(), 0xFF1D4ED8.toInt(),
            0xFF0EA5E9.toInt(), 0xFF14B8A6.toInt(), 0xFF0F766E.toInt(),
            0xFFF97316.toInt(), 0xFFF59E0B.toInt(), 0xFFC2410C.toInt(),
            0xFF84CC16.toInt(), 0xFF22C55E.toInt(), 0xFF15803D.toInt(),
            0xFFEC4899.toInt(), 0xFFF472B6.toInt(), 0xFFBE185D.toInt(),
            0xFF6366F1.toInt(), 0xFF818CF8.toInt(), 0xFF4338CA.toInt()
        )
        return palettes[Math.floorMod(seedText.hashCode(), palettes.size)]
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun String?.clean(): String? {
        val text = this?.trim() ?: return null
        if (text.isEmpty() || text.equals("<unknown>", ignoreCase = true)) return null
        return text
    }

    private fun isAudio(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in audioExtensions
    }

    fun displayFolderName(treeUri: Uri): String =
        runCatching { DocumentFile.fromTreeUri(appContext, treeUri)?.name }
            .getOrNull() ?: "Music"

    suspend fun listAudio(treeUri: Uri, recursive: Boolean = false): List<AudioDoc> =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = DocumentFile.fromTreeUri(appContext, treeUri) ?: return@runCatching emptyList()
                val files = mutableListOf<DocumentFile>()
                if (recursive) {
                    collectAudioFiles(root, files, 0)
                } else {
                    root.listFiles()
                        .filter { it.isFile && isAudio(it.name.orEmpty()) }
                        .forEach(files::add)
                }
                files.mapNotNull { doc ->
                    val name = doc.name ?: return@mapNotNull null
                    AudioDoc(name, doc.uri, doc.length())
                }
            }.getOrDefault(emptyList())
        }

    private fun toJson(track: Track): JSONObject = JSONObject().apply {
        put("id", track.id)
        put("uri", track.uri)
        put("title", track.title)
        put("artist", track.artist)
        put("album", track.album)
        put("duration", track.durationMs)
        put("trackNumber", track.trackNumber)
        put("year", track.year)
        put("artUri", track.artUri ?: JSONObject.NULL)
        put("artPath", track.artPath ?: JSONObject.NULL)
        put("color", track.color)
        put("addedAt", track.addedAt)
    }

    private fun fromJson(json: JSONObject?): Track? {
        json ?: return null
        val uri = json.optString("uri", "")
        if (uri.isEmpty()) return null
        return Track(
            id = json.optString("id", uri),
            uri = uri,
            title = json.optString("title", "Untitled"),
            artist = json.optString("artist", "Unknown artist"),
            album = json.optString("album", "Unknown album"),
            durationMs = json.optLong("duration", 0L),
            trackNumber = json.optInt("trackNumber", 0),
            year = json.optInt("year", 0),
            artUri = if (json.isNull("artUri")) null else json.optString("artUri"),
            artPath = if (json.isNull("artPath")) null else json.optString("artPath"),
            color = json.optInt("color", 0xFF8B5CF6.toInt()),
            addedAt = json.optLong("addedAt", 0L)
        )
    }

    private companion object {
        val audioExtensions = setOf(
            "mp3", "m4a", "aac", "flac", "wav", "ogg", "oga", "opus", "m4b", "mka", "mp4a"
        )
    }
}

data class AudioDoc(
    val name: String,
    val uri: Uri,
    val sizeBytes: Long
)
