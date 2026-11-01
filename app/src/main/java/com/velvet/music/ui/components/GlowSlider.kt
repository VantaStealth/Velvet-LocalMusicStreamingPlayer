package com.velvet.music.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp

/** Slim gradient slider with a glowing thumb, used for sensitivity and mixers. */
@Composable
fun GlowSlider(
    value: Float,
    onChange: (Float) -> Unit,
    color: Color,
    bright: Color,
    modifier: Modifier = Modifier
) {
    var width by remember { mutableFloatStateOf(1f) }
    val emit: (Float) -> Unit = { x ->
        val fraction = ((x - width * 0.04f) / (width * 0.92f)).coerceIn(0f, 1f)
        onChange(fraction)
    }
    Canvas(
        modifier
            .fillMaxWidth()
            .height(30.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { emit(it.x) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    emit(change.position.x)
                }
            }
    ) {
        val fraction = value.coerceIn(0f, 1f)
        val inset = size.width * 0.04f
        val startX = inset
        val endX = size.width - inset
        val centerY = size.height / 2f
        val thumbX = startX + (endX - startX) * fraction

        drawLine(
            color = Color.White.copy(alpha = 0.09f),
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = 4.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        if (thumbX > startX) {
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(color.copy(alpha = 0.7f), bright),
                    startX = startX,
                    endX = thumbX.coerceAtLeast(startX + 1f)
                ),
                start = Offset(startX, centerY),
                end = Offset(thumbX, centerY),
                strokeWidth = 4.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }
        drawCircle(
            color = color.copy(alpha = 0.18f),
            radius = 9.dp.toPx(),
            center = Offset(thumbX, centerY)
        )
        drawCircle(
            color = bright.copy(alpha = 0.55f),
            radius = 5.5.dp.toPx(),
            center = Offset(thumbX, centerY)
        )
        drawCircle(
            color = Color.White,
            radius = 2.6.dp.toPx(),
            center = Offset(thumbX, centerY)
        )
    }
}

/** Vertical glass rail with a liquid fill, for the volume HUD. */
@Composable
fun VerticalGlowRail(
    value: Float,
    onChange: (Float) -> Unit,
    color: Color,
    bright: Color,
    modifier: Modifier = Modifier
) {
    var height by remember { mutableFloatStateOf(1f) }
    val emit: (Float) -> Unit = { y ->
        onChange((1f - y / height).coerceIn(0f, 1f))
    }
    Box(
        modifier
            .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) { detectTapGestures { emit(it.y) } }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, _ ->
                    change.consume()
                    emit(change.position.y)
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val fraction = value.coerceIn(0f, 1f)
            val radius = size.width * 0.5f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.055f),
                cornerRadius = CornerRadius(radius, radius)
            )
            val fillTop = size.height * (1f - fraction)
            if (fraction > 0.001f) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(bright.copy(alpha = 0.95f), color),
                        startY = fillTop,
                        endY = size.height
                    ),
                    topLeft = Offset(0f, fillTop),
                    size = Size(size.width, size.height - fillTop),
                    cornerRadius = CornerRadius(radius, radius)
                )
            }
            drawRoundRect(
                color = Color.White.copy(alpha = 0.14f),
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = 1.dp.toPx())
            )
            repeat(4) { index ->
                val y = size.height * (index + 1) / 5f
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = Offset(size.width * 0.24f, y),
                    end = Offset(size.width * 0.76f, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            val handleY = (fillTop).coerceIn(size.width * 0.3f, size.height - size.width * 0.3f)
            drawCircle(
                color = Color.White.copy(alpha = 0.94f),
                radius = size.width * 0.16f,
                center = Offset(size.width / 2f, handleY)
            )
            drawCircle(
                color = bright.copy(alpha = 0.35f),
                radius = size.width * 0.32f,
                center = Offset(size.width / 2f, handleY)
            )
        }
    }
}
