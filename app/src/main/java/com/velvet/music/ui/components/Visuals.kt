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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
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
fun DiscoLights(
    active: Boolean,
    playing: Boolean,
    bass: Float,
    modifier: Modifier = Modifier
) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "disco")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(11000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "discoDrift"
    )
    val shipDrift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(43000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "discoShip"
    )
    val colors = listOf(
        Color(0xFFB026FF),
        Color(0xFF00E5FF),
        Color(0xFFFF2B8A),
        Color(0xFFFFC44D)
    )
    val level = if (playing) bass.coerceIn(0f, 1f) else 0f
    val spin = (drift * 2f * PI).toFloat()
    val baseColor = (drift * 8f).toInt() % colors.size

    Canvas(modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val width = size.width
        val height = size.height
        val energy = 0.55f + level * 0.45f
        val shake = level * 3.dp.toPx()
        val color = { offset: Int -> colors[(baseColor + offset) % colors.size] }

        // Border and corner lights: every glow has a visible source.
        val cornerRadius = width * (0.30f + level * 0.06f)
        listOf(
            Offset.Zero,
            Offset(width, 0f),
            Offset(width, height),
            Offset(0f, height)
        ).forEachIndexed { index, corner ->
            val tint = color(index)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tint.copy(alpha = 0.17f * energy),
                        tint.copy(alpha = 0.05f * energy),
                        Color.Transparent
                    ),
                    center = corner,
                    radius = cornerRadius
                ),
                radius = cornerRadius,
                center = corner
            )
        }

        val edgeAlpha = 0.35f + level * 0.45f
        val edgeBrush = Brush.horizontalGradient(
            listOf(Color.Transparent, color(0).copy(alpha = edgeAlpha), Color.Transparent)
        )
        drawLine(edgeBrush, Offset(0f, 1.dp.toPx()), Offset(width, 1.dp.toPx()), 2.dp.toPx())
        drawLine(edgeBrush, Offset(0f, height - 1.dp.toPx()), Offset(width, height - 1.dp.toPx()), 2.dp.toPx())
        val sideBrush = Brush.verticalGradient(
            listOf(Color.Transparent, color(2).copy(alpha = edgeAlpha), Color.Transparent)
        )
        drawLine(sideBrush, Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), height), 2.dp.toPx())
        drawLine(sideBrush, Offset(width - 1.dp.toPx(), 0f), Offset(width - 1.dp.toPx(), height), 2.dp.toPx())

        // Two sweeping spotlights, each with its own lamp and floor pool.
        val pools = mutableListOf<Offset>()
        listOf(
            Offset(width * 0.04f, -height * 0.02f),
            Offset(width * 0.96f, -height * 0.02f)
        ).forEachIndexed { index, origin ->
            val sway = sin(spin * (0.55f + index * 0.12f) + index * 2.1f) * 0.30f
            val angle = if (index == 0) 0.52f + sway else -0.52f + sway
            val jitter = sin(spin * 34f + index) * shake
            val lamp = Offset(origin.x + jitter, origin.y)
            val end = Offset(
                lamp.x + sin(angle) * height * 1.02f,
                lamp.y + cos(angle) * height * 1.02f
            )
            val half = height * (0.065f + level * 0.018f)
            val dx = end.x - lamp.x
            val dy = end.y - lamp.y
            val length = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
            val px = -dy / length * half
            val py = dx / length * half
            val tint = color(index * 2)
            val cone = Path().apply {
                moveTo(lamp.x, lamp.y)
                lineTo(end.x + px, end.y + py)
                lineTo(end.x - px, end.y - py)
                close()
            }
            drawPath(
                path = cone,
                brush = Brush.linearGradient(
                    colors = listOf(
                        tint.copy(alpha = 0.24f * energy),
                        tint.copy(alpha = 0.07f * energy),
                        Color.Transparent
                    ),
                    start = lamp,
                    end = end
                )
            )
            val pool = Offset(
                end.x + sin(spin * 1.7f + index) * shake,
                end.y
            )
            pools += pool
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        tint.copy(alpha = 0.22f * energy),
                        Color.Transparent
                    ),
                    center = pool,
                    radius = height * 0.16f
                ),
                radius = height * 0.16f,
                center = pool
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f),
                        tint.copy(alpha = 0.55f),
                        Color.Transparent
                    ),
                    center = lamp,
                    radius = 16.dp.toPx()
                ),
                radius = 16.dp.toPx(),
                center = lamp
            )
            drawCircle(Color.White.copy(alpha = 0.9f), 2.4.dp.toPx(), lamp)
        }

        // Flowing theme liquid along the floor.
        for (layer in 0..2) {
            val floor = height * (0.90f + layer * 0.014f)
            val amplitude = (8 + layer * 5).dp.toPx() + level * 10.dp.toPx()
            val wave = Path().apply {
                moveTo(0f, height)
                var x = 0f
                while (x <= width + 2f) {
                    val y = floor +
                        sin(x / width * 7.4f + spin * (1.1f + layer * 0.3f) + layer) * amplitude +
                        sin(x / width * 21f - spin * 0.8f) * 3.dp.toPx()
                    lineTo(x, y)
                    x += width / 26f
                }
                lineTo(width, height)
                close()
            }
            drawPath(
                path = wave,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        color(layer).copy(alpha = 0.20f + level * 0.10f),
                        Color.Transparent
                    ),
                    startY = floor - height * 0.06f,
                    endY = height
                )
            )
        }

        // Beat bursts: local shockwaves and sparks, never a full-screen wash.
        if (level > 0.28f) {
            pools.forEachIndexed { index, pool ->
                for (ring in 0 until 3) {
                    val progress = ((drift * 5.5f + ring / 3f + index * 0.25f) % 1f)
                    val fade = (1f - progress) * (0.18f + level * 0.42f)
                    drawCircle(
                        color = color(index * 2).copy(alpha = fade),
                        radius = height * (0.03f + progress * 0.20f),
                        center = pool,
                        style = Stroke(width = 1.2f + level * 2.4f)
                    )
                }
                for (spark in 0 until 14) {
                    val progress = ((drift * 6f + spark / 14f) % 1f)
                    val angle = spark / 14f * 2f * PI.toFloat() + spin
                    val radius = progress * height * 0.19f
                    drawCircle(
                        color = color(index + 1).copy(alpha = (1f - progress) * level * 0.55f),
                        radius = (1f + (spark % 3)) * 0.9.dp.toPx(),
                        center = Offset(
                            pool.x + cos(angle) * radius,
                            pool.y + sin(angle) * radius * 0.7f
                        )
                    )
                }
            }
        }

        // Embers drifting up from the floor.
        for (index in 0 until 48) {
            val progress = (drift * (1.6f + (index % 4) * 0.22f) + index / 48f) % 1f
            val x = width * ((index * 0.618f) % 1f) + sin(spin + index) * 9.dp.toPx()
            val y = height * (0.94f - progress * 0.58f)
            val alpha = sin(progress * PI.toFloat()) * (0.16f + level * 0.42f)
            drawCircle(
                color = color(index).copy(alpha = alpha.coerceIn(0f, 0.7f)),
                radius = (0.7f + (index % 3) * 0.7f).dp.toPx(),
                center = Offset(x, y)
            )
        }

        // A pirate ship crosses the lower third occasionally.
        if (shipDrift in 0.86f..0.985f) {
            val progress = (shipDrift - 0.86f) / 0.125f
            val shipX = width * (-0.22f + progress * 1.44f)
            val shipY = height * 0.74f + sin(spin * 1.4f) * 4.dp.toPx()
            val shipScale = (height / 620f).coerceAtLeast(0.5f)
            val rim = color(3).copy(alpha = 0.7f)
            withTransform({
                translate(left = shipX, top = shipY)
                scale(scaleX = shipScale, scaleY = shipScale, pivot = Offset.Zero)
            }) {
                drawOval(
                    color = color(3).copy(alpha = 0.10f),
                    topLeft = Offset(-92f, 28f),
                    size = Size(184f, 16f)
                )
                val hull = Path().apply {
                    moveTo(-94f, -6f)
                    lineTo(-66f, 24f)
                    lineTo(58f, 24f)
                    lineTo(98f, -6f)
                    lineTo(70f, -12f)
                    lineTo(-72f, -12f)
                    close()
                }
                drawPath(hull, Color(0xF2080510))
                drawPath(hull, rim, style = Stroke(width = 1.4f))
                drawLine(rim, Offset(-36f, -12f), Offset(-36f, -88f), 1.4f)
                drawLine(rim, Offset(6f, -12f), Offset(6f, -98f), 1.4f)
                drawLine(rim, Offset(44f, -12f), Offset(44f, -74f), 1.4f)
                val sailA = Path().apply {
                    moveTo(-32f, -84f)
                    lineTo(-32f, -26f)
                    quadraticBezierTo(-70f, -40f, -64f, -66f)
                    close()
                }
                drawPath(sailA, Color(0xE0120A1C))
                drawPath(sailA, rim.copy(alpha = 0.35f), style = Stroke(width = 1f))
                val sailB = Path().apply {
                    moveTo(10f, -94f)
                    lineTo(10f, -24f)
                    quadraticBezierTo(48f, -36f, 42f, -74f)
                    close()
                }
                drawPath(sailB, Color(0xD80C0712))
                drawPath(sailB, rim.copy(alpha = 0.35f), style = Stroke(width = 1f))
                val jib = Path().apply {
                    moveTo(48f, -70f)
                    lineTo(48f, -22f)
                    quadraticBezierTo(76f, -32f, 66f, -56f)
                    close()
                }
                drawPath(jib, Color(0xE0120A1C))
                drawPath(jib, rim.copy(alpha = 0.35f), style = Stroke(width = 1f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFE3B0), Color.Transparent),
                        center = Offset(70f, -8f),
                        radius = 16f
                    ),
                    radius = 16f,
                    center = Offset(70f, -8f)
                )
                drawCircle(Color.White, 2.2f, Offset(70f, -8f))
            }
        }
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
