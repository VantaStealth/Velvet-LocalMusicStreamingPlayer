package com.velvet.music.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.velvet.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val artworkCache = object : LruCache<String, Bitmap>(32 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
}

private suspend fun loadArtwork(context: Context, uri: String?, requestSize: Int): Bitmap? {
    if (uri.isNullOrBlank()) return null
    val key = "$uri|$requestSize"
    artworkCache.get(key)?.let { return it }
    return withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                val bytes = stream.readBytes()
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= requestSize) sample *= 2
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.size,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )?.also { artworkCache.put(key, it) }
            }
        }.getOrNull()
    }
}

fun placeholderBrush(color: Int): Brush {
    val base = Color(color)
    return Brush.linearGradient(
        listOf(
            base.copy(alpha = 0.90f),
            base.copy(alpha = 0.36f),
            Color(0xFF120E18)
        ),
        start = Offset.Zero,
        end = Offset.Infinite
    )
}

@Composable
fun Artwork(
    uri: String?,
    color: Int,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    requestSize: Int = 256,
    overlayNote: Boolean = true
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(corner)
    val bitmap by produceState<ImageBitmap?>(initialValue = null, uri, requestSize) {
        value = loadArtwork(context, uri, requestSize)?.asImageBitmap()
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(placeholderBrush(color)),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = bitmap, animationSpec = tween(320), label = "artwork") { image ->
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else if (overlayNote) {
                Icon(
                    painter = painterResource(R.drawable.ic_note),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxSize(0.32f)
                )
            }
        }
    }
}
