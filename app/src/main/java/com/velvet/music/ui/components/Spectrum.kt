package com.velvet.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

val SpectrumBandCount = 48

/**
 * Radial spectrum drawn around album art. Bands are mirrored left/right and rotate
 * slowly; sparks shed from the bar tips and drift outward. Values arrive from the
 * playback service FFT when live capture is available.
 */
@Composable
fun SpectrumRing(
    bands: State<FloatArray>,
    color: Color,
    bright: Color,
    active: Boolean,
    sensitivity: Float,
    modifier: Modifier = Modifier,
    bandCount: Int = SpectrumBandCount,
    rotationDegreesPerSecond: Float = 7f,
    innerFraction: Float = 0.40f,
    lengthFraction: Float = 0.08f
) {
    val sensitivityState = rememberUpdatedState(sensitivity)
    val activeState = rememberUpdatedState(active)
    val smooth = remember { FloatArray(SpectrumBandCount) }
    val sparks = remember {
        val random = Random(7)
        Array(26) {
            Spark(
                angle = random.nextFloat() * (PI * 2).toFloat(),
                phase = random.nextFloat(),
                size = 0.9f + random.nextFloat() * 1.2f
            )
        }
    }
    var clock by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last).coerceAtMost(80_000_000L)) / 1_000_000_000f
                last = now
                clock += dt
            }
            if (!activeState.value) delay(80)
        }
    }

    Canvas(modifier) {
        val t = clock
        val minSide = size.minDimension
        if (minSide <= 0f) return@Canvas
        val center = Offset(size.width / 2f, size.height / 2f)
        val source = bands.value
        val gain = sensitivityState.value
        val playing = activeState.value
        val innerRadius = minSide * innerFraction
        // Raising sensitivity lifts the outer limit too, so the ring itself
        // widens instead of only amplifying bands into the same ceiling.
        val reach = 0.55f + 0.45f * gain.coerceIn(0.2f, 3f)
        val available = (minSide * 0.487f - innerRadius).coerceAtLeast(minSide * 0.012f)
        val maxLength = (minSide * lengthFraction * reach).coerceIn(
            minSide * 0.008f,
            available
        )
        val step = (PI * 2 / bandCount).toFloat()
        val half = bandCount / 2
        val perHalf = SpectrumBandCount / half.toFloat()

        val rotation = t * rotationDegreesPerSecond * if (playing) 1f else 0.12f

        // Halo behind the ring
        val haloRadius = minOf(innerRadius + maxLength * 1.9f, minSide * 0.49f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = 0.16f),
                    color.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = center,
                radius = haloRadius
            ),
            radius = haloRadius,
            center = center
        )

        // Tick ring
        rotate(rotation * -0.6f, center) {
            for (i in 0 until bandCount) {
                val angle = i * step - PI.toFloat() / 2f
                val major = i % 4 == 0
                val r1 = innerRadius + maxLength + minSide * (if (major) 0.014f else 0.009f)
                val r2 = r1 + minSide * (if (major) 0.012f else 0.006f)
                if (r2 > minSide * 0.494f) continue
                drawLine(
                    color = bright.copy(alpha = if (major) 0.30f else 0.14f),
                    start = center + Offset(cos(angle) * r1, sin(angle) * r1),
                    end = center + Offset(cos(angle) * r2, sin(angle) * r2),
                    strokeWidth = if (major) 1.6.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        rotate(rotation, center) {
            for (i in 0 until bandCount) {
                val angle = i * step - PI.toFloat() / 2f
                val sourceIndex = ((i % half) * perHalf).toInt().coerceIn(0, SpectrumBandCount - 1)
                val target = if (playing) {
                    (source.getOrElse(sourceIndex) { 0f } * gain).coerceIn(0f, 1f)
                } else {
                    0.035f + 0.02f * (sin(t * 1.1f + i * 0.55f) * 0.5f + 0.5f)
                }
                val previous = smooth[i]
                smooth[i] = if (target >= previous) {
                    previous * 0.42f + target * 0.58f
                } else {
                    previous * 0.86f + target * 0.14f
                }
                val value = smooth[i]
                val length = (maxLength * value).coerceAtLeast(minSide * 0.006f)
                val direction = Offset(cos(angle), sin(angle))
                val start = center + direction * innerRadius
                val tip = center + direction * (innerRadius + length)

                drawLine(
                    color = color.copy(alpha = 0.22f + 0.35f * value),
                    start = start,
                    end = tip,
                    strokeWidth = 4.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = bright.copy(alpha = 0.55f + 0.4f * value),
                    start = start,
                    end = tip,
                    strokeWidth = 1.9.dp.toPx(),
                    cap = StrokeCap.Round
                )
                if (value > 0.58f) {
                    drawCircle(
                        color = bright.copy(alpha = 0.55f),
                        radius = 1.7.dp.toPx(),
                        center = tip
                    )
                }
            }
        }

        // Sparks shed from the bar tips
        if (playing) {
            val sparkLimit = minSide * 0.487f
            sparks.forEachIndexed { index, spark ->
                val phase = (t * 0.35f + spark.phase) % 1f
                val angle = spark.angle + sin(t * 0.7f + index) * 0.08f
                val radius = innerRadius + maxLength * (1.02f + phase * 0.6f)
                if (radius <= sparkLimit) {
                    drawCircle(
                        color = bright.copy(alpha = (1f - phase) * 0.55f),
                        radius = spark.size.dp.toPx(),
                        center = center + Offset(cos(angle) * radius, sin(angle) * radius)
                    )
                }
            }
        }
    }
}

/**
 * Linear spectrum strip with a mirrored envelope and glowing tip particles.
 */
@Composable
fun SpectrumStrip(
    bands: State<FloatArray>,
    color: Color,
    bright: Color,
    active: Boolean,
    sensitivity: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 40
) {
    val sensitivityState = rememberUpdatedState(sensitivity)
    val activeState = rememberUpdatedState(active)
    val smooth = remember { FloatArray(barCount) }
    var clock by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last).coerceAtMost(80_000_000L)) / 1_000_000_000f
                last = now
                clock += dt
            }
            if (!activeState.value) delay(80)
        }
    }

    Canvas(modifier) {
        val source = bands.value
        val playing = activeState.value
        val gain = sensitivityState.value
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val gap = size.width / barCount
        val barWidth = (gap * 0.42f).coerceAtLeast(1.4f)
        val half = barCount / 2
        val perHalf = SpectrumBandCount / half.toFloat()

        for (index in 0 until barCount) {
            val mirrored = if (index < half) index else barCount - 1 - index
            val sourceIndex = (mirrored * perHalf).toInt().coerceIn(0, SpectrumBandCount - 1)
            val target = if (playing) {
                (source.getOrElse(sourceIndex) { 0f } * gain).coerceIn(0f, 1f)
            } else {
                0.05f + 0.03f * (sin(clock * 1.2f + index * 0.4f) * 0.5f + 0.5f)
            }
            val previous = smooth[index]
            smooth[index] = if (target >= previous) {
                previous * 0.4f + target * 0.6f
            } else {
                previous * 0.85f + target * 0.15f
            }
            val value = smooth[index]
            val barHeight = (size.height * (0.12f + value * 0.88f)).coerceAtLeast(1.5f)
            val topLeft = Offset(
                x = index * gap + (gap - barWidth) / 2f,
                y = size.height / 2f - barHeight / 2f
            )
            drawRoundRect(
                color = color.copy(alpha = 0.30f + 0.5f * value),
                topLeft = topLeft,
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
            val cap = (barHeight * 0.24f).coerceAtLeast(1.5f)
            drawRoundRect(
                color = bright.copy(alpha = 0.85f),
                topLeft = topLeft,
                size = Size(barWidth, cap),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f)
            )
            if (playing && value > 0.6f) {
                drawCircle(
                    color = bright.copy(alpha = 0.35f),
                    radius = 1.2.dp.toPx(),
                    center = Offset(topLeft.x + barWidth / 2f, topLeft.y - 2.dp.toPx())
                )
            }
        }
    }
}

/**
 * Soft organic veins used behind accent surfaces, echoing the desktop app texture.
 */
@Composable
fun VeinOverlay(
    color: Color,
    modifier: Modifier = Modifier,
    alpha: Float = 0.16f,
    veinCount: Int = 7,
    seed: Int = 97
) {
    Canvas(modifier) {
        drawVeins(color, alpha, veinCount, seed)
    }
}

/**
 * Veins whose brightness rides the low end, without recomposing the player.
 */
@Composable
fun ReactiveVeinOverlay(
    spectrum: State<FloatArray>,
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    veinCount: Int = 7,
    seed: Int = 97
) {
    Canvas(modifier) {
        val bands = spectrum.value
        val count = minOf(8, bands.size)
        var sum = 0f
        for (index in 0 until count) sum += bands[index]
        val bass = if (count == 0) 0f else (sum / count).coerceIn(0f, 1f)
        drawVeins(color, 0.10f + if (playing) bass * 0.10f else 0f, veinCount, seed)
    }
}

private fun DrawScope.drawVeins(
    color: Color,
    alpha: Float,
    veinCount: Int,
    seed: Int
) {
    if (size.width <= 0f || size.height <= 0f) return
    val random = Random(seed)
    repeat(veinCount) {
        val path = androidx.compose.ui.graphics.Path()
        var x = -size.width * 0.06f
        var y = size.height * (random.nextFloat() * 1.08f - 0.04f)
        path.moveTo(x, y)
        val segments = 4 + random.nextInt(3)
        val stepX = size.width / segments * (0.7f + random.nextFloat() * 0.55f)
        repeat(segments) {
            val nextX = x + stepX
            val nextY = y + (random.nextFloat() - 0.5f) * size.height * 0.42f
            val c1x = x + stepX * 0.4f
            val c1y = y + (random.nextFloat() - 0.5f) * size.height * 0.3f
            val c2x = x + stepX * 0.78f
            val c2y = nextY + (random.nextFloat() - 0.5f) * size.height * 0.2f
            path.cubicTo(c1x, c1y, c2x, c2y, nextX, nextY)
            x = nextX
            y = nextY
        }
        drawPath(
            path = path,
            color = color.copy(alpha = alpha * 0.22f),
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            path = path,
            color = color.copy(alpha = alpha),
            style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/**
 * Liquid volume orb: a wave-filled disc with rising bubbles and a rotating sheen.
 */
@Composable
fun LiquidOrb(
    fraction: Float,
    color: Color,
    bright: Color,
    modifier: Modifier = Modifier,
    active: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "liquidOrb")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave"
    )
    val bubbles by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubbles"
    )

    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val level = fraction.coerceIn(0f, 1f)

        drawCircle(color = Color(0xFF120D18), radius = radius, center = center)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.22f), Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )

        val surfaceY = center.y + radius - (radius * 2f * level)
        val wavePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(center.x - radius, surfaceY)
            var x = center.x - radius
            while (x <= center.x + radius) {
                val y = surfaceY + sin((x - center.x) / radius * 2.2f + wave) * radius * 0.05f
                lineTo(x, y)
                x += radius / 18f
            }
            lineTo(center.x + radius, center.y + radius)
            lineTo(center.x - radius, center.y + radius)
            close()
        }
        drawPath(
            path = wavePath,
            brush = Brush.verticalGradient(
                colors = listOf(bright.copy(alpha = 0.95f), color.copy(alpha = 0.75f)),
                startY = surfaceY,
                endY = center.y + radius
            )
        )
        drawPath(
            path = wavePath,
            color = Color.White.copy(alpha = 0.18f),
            style = Stroke(width = 1.2.dp.toPx())
        )

        if (active) {
            repeat(6) { index ->
                val phase = (bubbles + index / 6f) % 1f
                val bx = center.x + (Random(index * 31 + 7).nextFloat() - 0.5f) * radius * 1.2f
                val by = center.y + radius - phase * radius * 1.9f
                if (by > surfaceY + radius * 0.1f) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.22f * (1f - phase)),
                        radius = (1.2f + index % 3).dp.toPx(),
                        center = Offset(bx + sin(phase * 6f + index) * radius * 0.05f, by)
                    )
                }
            }
        }

        drawCircle(
            color = bright.copy(alpha = 0.35f),
            radius = radius,
            center = center,
            style = Stroke(width = 1.4.dp.toPx())
        )
        drawCircle(
            brush = Brush.sweepGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.10f),
                    Color.Transparent
                ),
                center = center
            ),
            radius = radius * 0.92f,
            center = center,
            style = Stroke(width = radius * 0.12f)
        )
    }
}

private class Spark(
    val angle: Float,
    val phase: Float,
    val size: Float
)
