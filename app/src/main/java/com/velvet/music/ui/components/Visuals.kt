package com.velvet.music.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun AuroraBackdrop(
    primary: Color,
    secondary: Color = Color(0xFFD4AF37),
    modifier: Modifier = Modifier,
    intensity: Float = 1f
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(19000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraDrift"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(7200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "auroraBreathe"
    )

    Canvas(modifier) {
        val width = size.width
        val height = size.height

        val top = Offset(width * (0.86f - drift * 0.18f), height * (0.05f + drift * 0.10f))
        val topRadius = width * 0.82f * breathe
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.26f * intensity), Color.Transparent),
                center = top,
                radius = topRadius
            ),
            radius = topRadius,
            center = top
        )

        val side = Offset(width * (0.04f + drift * 0.16f), height * (0.44f - drift * 0.08f))
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(secondary.copy(alpha = 0.13f * intensity), Color.Transparent),
                center = side,
                radius = width * 0.62f
            ),
            radius = width * 0.62f,
            center = side
        )

        val bottom = Offset(width * (0.70f - drift * 0.10f), height * 0.84f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(primary.copy(alpha = 0.10f * intensity), Color.Transparent),
                center = bottom,
                radius = width * 0.66f
            ),
            radius = width * 0.66f,
            center = bottom
        )
    }
}

@Composable
fun FilmGrain(modifier: Modifier = Modifier, alpha: Float = 0.045f) {
    Canvas(modifier) {
        val random = Random(31)
        val light = List(720) {
            Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
        }
        val dark = List(520) {
            Offset(random.nextFloat() * size.width, random.nextFloat() * size.height)
        }
        drawPoints(
            points = light,
            pointMode = PointMode.Points,
            color = Color.White.copy(alpha = alpha * 0.55f),
            strokeWidth = 1.2f,
            cap = StrokeCap.Round
        )
        drawPoints(
            points = dark,
            pointMode = PointMode.Points,
            color = Color.Black.copy(alpha = alpha),
            strokeWidth = 1.2f,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun BreathingGlow(
    color: Color,
    modifier: Modifier = Modifier,
    active: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "breathingGlow")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    Canvas(modifier) {
        val factor = if (active) pulse else 1f
        val radius = size.minDimension * 0.56f * factor
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.30f),
                    color.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}

@Composable
fun BeatWave(
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    bars: Int = 46,
    height: Dp = 32.dp
) {
    val transition = rememberInfiniteTransition(label = "beat")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 42).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "beatPhase"
    )

    Canvas(modifier.height(height)) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val gap = size.width / bars
        val barWidth = (gap * 0.40f).coerceAtLeast(1.6f)
        val cycle = (phase / (2f * PI.toFloat())) * 2f
        val beatPosition = cycle - kotlin.math.floor(cycle)
        val beat = if (playing) exp(-beatPosition * 5.2f).toFloat() else 0f

        val waveBrush = Brush.horizontalGradient(
            colors = listOf(
                color.copy(alpha = 0.22f),
                color.copy(alpha = 0.86f),
                Color.White.copy(alpha = 0.88f),
                color.copy(alpha = 0.86f),
                color.copy(alpha = 0.22f)
            ),
            startX = 0f,
            endX = size.width
        )

        for (index in 0 until bars) {
            val position = index / (bars - 1f)
            val envelope = sin(position * PI).toFloat().coerceIn(0.12f, 1f)
            val wave = sin(phase * 0.85f + index * 0.44f) * 0.34f +
                sin(phase * 1.6f + index * 0.21f) * 0.24f +
                sin(phase * 2.7f + index * 0.79f) * 0.14f
            val amplitude = if (playing) {
                (0.08f + (wave + 0.72f) * 0.44f * envelope + beat * 0.16f * envelope)
                    .coerceIn(0.07f, 1f)
            } else {
                0.10f + (sin(index * 0.84f).toFloat() + 1f) * 0.055f
            }
            val barHeight = (size.height * amplitude).coerceAtLeast(1.5f)
            drawRoundRect(
                brush = waveBrush,
                topLeft = Offset(
                    x = index * gap + (gap - barWidth) / 2f,
                    y = size.height / 2f - barHeight / 2f
                ),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
fun VinylDisc(
    spin: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF241D2B), Color(0xFF0C0910), Color(0xFF030205)),
                center = Offset(center.x - radius * 0.28f, center.y - radius * 0.32f),
                radius = radius * 1.25f
            ),
            radius = radius,
            center = center
        )

        for (ring in 0 until 28) {
            drawCircle(
                color = Color.White.copy(alpha = 0.028f + (ring % 3) * 0.008f),
                radius = radius * (0.22f + ring * 0.0275f),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        rotate(spin) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.075f),
                        Color.White.copy(alpha = 0.02f),
                        Color.Transparent
                    ),
                    center = center
                ),
                startAngle = -40f,
                sweepAngle = 78f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.9f, center.y - radius * 0.9f),
                size = Size(radius * 1.8f, radius * 1.8f),
                style = Stroke(width = radius * 0.16f, cap = StrokeCap.Round)
            )
            drawArc(
                color = Color(0xFFE8C97A).copy(alpha = 0.30f),
                startAngle = 126f,
                sweepAngle = 26f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.92f, center.y - radius * 0.92f),
                size = Size(radius * 1.84f, radius * 1.84f),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        drawCircle(color = color.copy(alpha = 0.92f), radius = radius * 0.30f, center = center)
        drawCircle(
            color = Color(0xFFF3DCA0).copy(alpha = 0.5f),
            radius = radius * 0.30f,
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )
        drawCircle(color = Color(0xFF08060C), radius = radius * 0.045f, center = center)
        drawCircle(
            color = Color.White.copy(alpha = 0.06f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}
