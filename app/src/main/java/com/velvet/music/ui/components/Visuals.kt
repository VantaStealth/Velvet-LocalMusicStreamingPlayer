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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

private const val DiscoParticleCap = 300
private const val DiscoFlashCap = 10
private const val SparkKind = 0
private const val EmberKind = 1
private const val BeamBaseWidth = 420f
private const val BeamBaseHeight = 140f

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

/**
 * Beat-reactive stage. One frame clock drives a single draw pass; particles and
 * flashes are pooled, gradients are created once, and the wave paths are reused
 * so a steady 60 FPS does not depend on the track or the device.
 */
@Composable
fun DiscoLights(
    active: Boolean,
    playing: Boolean,
    spectrum: State<FloatArray>,
    primary: Color,
    bright: Color,
    deep: Color,
    modifier: Modifier = Modifier
) {
    if (!active) return
    val palette = remember(primary, bright, deep) {
        listOf(primary, bright, deep, Color(0xFFE8C97A))
    }
    val brushes = remember(palette) { DiscoBrushes(palette) }
    val scene = remember { DiscoScene() }
    val latestSpectrum = rememberUpdatedState(spectrum)
    val latestPlaying = rememberUpdatedState(playing)
    val tick = remember { mutableLongStateOf(0L) }
    val waves = remember { Array(3) { Path() } }
    val ship = remember { ShipPaths() }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.05f)
                    val bands = latestSpectrum.value.value
                    val count = minOf(8, bands.size)
                    var sum = 0f
                    for (index in 0 until count) sum += bands[index]
                    val bass = if (count == 0) 0f else (sum / count).coerceIn(0f, 1f)
                    scene.step(dt, bass, latestPlaying.value, palette.size)
                }
                last = now
                tick.longValue = tick.longValue + 1
            }
        }
    }

    Canvas(modifier) {
        tick.longValue
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val width = size.width
        val height = size.height
        val minSide = minOf(width, height)
        val level = scene.level
        val energy = 0.55f + level * 0.45f
        val baseColor = scene.colorIndex

        withTransform({
            if (scene.shake > 0.4f) {
                translate(
                    sin(scene.time * 57f) * scene.shake,
                    cos(scene.time * 71f) * scene.shake
                )
            }
        }) {
            // Soft corner light, each with a source.
            val cornerRadius = minSide * 0.58f
            for (index in 0 until 4) {
                val tint = brushes.corner[(baseColor + index) % palette.size]
                val x = if (index % 2 == 0) 0f else width
                val y = if (index < 2) 0f else height
                drawCircle(
                    brush = tint,
                    radius = cornerRadius,
                    center = Offset(
                        x + sin(scene.time * 0.21f + index * 1.7f) * minSide * 0.018f,
                        y + cos(scene.time * 0.17f + index * 2.3f) * minSide * 0.014f
                    ),
                    alpha = energy * 0.9f
                )
            }

            drawBeam(
                origin = Offset(-width * 0.02f, -height * 0.06f),
                angle = 0.46f + sin(scene.time * 0.34f) * 0.22f,
                length = height * (1.03f + level * 0.1f),
                halfWidth = height * (0.085f + level * 0.022f),
                brush = brushes.beam[baseColor],
                lamp = brushes.lamp[baseColor],
                alpha = energy * (0.6f + level * 0.35f),
                level = level
            )
            drawBeam(
                origin = Offset(width * 1.02f, -height * 0.06f),
                angle = -0.46f + sin(scene.time * 0.29f + 2f) * 0.18f,
                length = height * (1.03f + level * 0.1f),
                halfWidth = height * (0.072f + level * 0.02f),
                brush = brushes.beam[(baseColor + 1) % palette.size],
                lamp = brushes.lamp[(baseColor + 1) % palette.size],
                alpha = energy * (0.55f + level * 0.3f),
                level = level
            )
            drawBeam(
                origin = Offset(-width * 0.03f, height * 1.04f),
                angle = -0.62f + sin(scene.time * 0.24f + 4f) * 0.16f,
                length = height * (0.92f + level * 0.08f),
                halfWidth = height * (0.058f + level * 0.016f),
                brush = brushes.beam[(baseColor + 2) % palette.size],
                lamp = brushes.lamp[(baseColor + 2) % palette.size],
                alpha = energy * (0.45f + level * 0.25f),
                level = level
            )

            drawLiquid(waves, palette, scene, width, height)
            drawShip(ship, palette, scene, width, height)
            drawFlashes(brushes, scene, minSide)
            drawParticles(palette, scene, minSide)
        }
    }
}

private fun DrawScope.drawBeam(
    origin: Offset,
    angle: Float,
    length: Float,
    halfWidth: Float,
    brush: Brush,
    lamp: Brush,
    alpha: Float,
    level: Float
) {
    val dir = Offset(sin(angle), cos(angle))
    val center = origin + dir * (length * 0.5f)
    val theta = atan2(dir.y, dir.x) * 180f / PI.toFloat()
    val scaleX = length / BeamBaseWidth
    val scaleY = (halfWidth * 2f) / BeamBaseHeight

    withTransform({
        translate(center.x - BeamBaseWidth / 2f, center.y - BeamBaseHeight / 2f)
        rotate(theta, Offset(BeamBaseWidth / 2f, BeamBaseHeight / 2f))
        scale(scaleX, scaleY, Offset(BeamBaseWidth / 2f, BeamBaseHeight / 2f))
    }) {
        drawOval(brush, topLeft = Offset.Zero, size = Size(BeamBaseWidth, BeamBaseHeight), alpha = alpha)
    }

    drawCircle(lamp, radius = 15.dp.toPx(), center = origin, alpha = 0.55f + level * 0.35f)
    drawCircle(Color.White, radius = (1.8f + level * 1.2f).dp.toPx(), center = origin, alpha = 0.9f)
}

private fun DrawScope.drawLiquid(
    waves: Array<Path>,
    palette: List<Color>,
    scene: DiscoScene,
    width: Float,
    height: Float
) {
    val baseY = height * 0.875f
    val points = 36
    val stepX = width / (points - 1)
    val swellSpread = maxOf(90f, width * 0.17f)

    for (layer in 2 downTo 0) {
        val path = waves[layer]
        path.reset()
        val amplitude = 6.dp.toPx() + scene.level * 24.dp.toPx() + layer * 3.dp.toPx()
        val drift = scene.time * (0.95f + layer * 0.34f + scene.level * 0.7f) + layer * 1.9f
        path.moveTo(0f, height)
        for (index in 0 until points) {
            val x = index * stepX
            val dx = (x - scene.swellX * width) / swellSpread
            val swell = exp(-dx * dx) * scene.swellAmp * density
            val y = baseY + layer * 9.dp.toPx() +
                sin(x * 0.0068f + drift) * amplitude +
                sin(x * 0.0195f - scene.time * 1.35f) * (2.dp.toPx() + scene.level * 5.dp.toPx()) +
                swell
            path.lineTo(x, y)
        }
        path.lineTo(width, height)
        path.close()

        val tint = palette[(scene.colorIndex + layer) % palette.size]
        drawPath(path, tint.copy(alpha = 0.20f + scene.level * 0.12f - layer * 0.04f))
    }

    // A lit meniscus on the top layer, not a geometric edge.
    val top = waves[0]
    drawPath(
        path = top,
        color = palette[scene.colorIndex].copy(alpha = 0.30f + scene.level * 0.35f),
        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawShip(
    paths: ShipPaths,
    palette: List<Color>,
    scene: DiscoScene,
    width: Float,
    height: Float
) {
    val age = scene.time - scene.shipStart
    if (age < 0f || age > 10f) return
    val progress = age / 10f
    val x = if (scene.shipDirection > 0f) {
        width * (-0.14f + progress * 1.28f)
    } else {
        width * (1.14f - progress * 1.28f)
    }
    val y = height * 0.868f + sin(scene.time * 1.5f) * 3.dp.toPx()
    val scale = (height / 1450f).coerceIn(0.34f, 0.56f)
    val tint = palette[3]
    val rim = palette[1].copy(alpha = 0.72f)
    val deep = Color(0xF2080510)

    withTransform({
        translate(x, y)
        scale(scale * scene.shipDirection, scale, Offset.Zero)
        rotate(sin(scene.time * 1.25f) * 2f, Offset.Zero)
    }) {
        drawOval(tint.copy(alpha = 0.08f), topLeft = Offset(-92f, 28f), size = Size(184f, 16f))
        drawPath(paths.hull, deep)
        drawPath(paths.hull, rim, style = Stroke(width = 1.4f))
        drawLine(rim, Offset(-36f, -12f), Offset(-36f, -88f), 1.4f)
        drawLine(rim, Offset(6f, -12f), Offset(6f, -98f), 1.4f)
        drawLine(rim, Offset(44f, -12f), Offset(44f, -74f), 1.4f)
        drawPath(paths.sailMain, Color(0xE0120A1C))
        drawPath(paths.sailMain, rim.copy(alpha = 0.35f), style = Stroke(width = 1f))
        drawPath(paths.sailFore, Color(0xD80C0712))
        drawPath(paths.sailFore, rim.copy(alpha = 0.35f), style = Stroke(width = 1f))
        drawPath(paths.jib, Color(0xE0120A1C))
        drawPath(paths.jib, rim.copy(alpha = 0.35f), style = Stroke(width = 1f))
        drawCircle(tint.copy(alpha = 0.55f), radius = 9f, center = Offset(70f, -8f))
        drawCircle(Color.White, radius = 2.2f, center = Offset(70f, -8f))
    }
}

private fun DrawScope.drawFlashes(
    brushes: DiscoBrushes,
    scene: DiscoScene,
    minSide: Float
) {
    val radius = minSide * 0.5f
    for (index in 0 until scene.flashCount) {
        val flash = scene.flashes[index]
        val progress = (flash.age / flash.duration).coerceIn(0f, 1f)
        val fade = (1f - progress) * (1f - progress)
        val scale = flash.size * (0.30f + progress * 0.9f)
        val tint = brushes.bloom[flash.color]
        withTransform({
            translate(flash.x, flash.y)
            scale(scale, scale, Offset.Zero)
            translate(-flash.x, -flash.y)
        }) {
            drawCircle(
                brush = tint,
                radius = radius,
                center = Offset(flash.x, flash.y),
                alpha = fade * 0.8f
            )
        }
    }
}

private fun DrawScope.drawParticles(
    palette: List<Color>,
    scene: DiscoScene,
    minSide: Float
) {
    for (index in 0 until scene.particleCount) {
        val particle = scene.particles[index]
        val ratio = (particle.life / particle.maxLife).coerceIn(0f, 1f)
        if (particle.kind == EmberKind) {
            val tint = palette[particle.color % palette.size]
            drawCircle(
                color = tint.copy(alpha = 0.10f + ratio * 0.45f),
                radius = particle.size * minSide,
                center = Offset(particle.x, particle.y)
            )
            continue
        }

        val tint = if (particle.color < 0) Color.White else palette[particle.color % palette.size]
        drawLine(
            color = tint.copy(alpha = 0.12f + ratio * 0.85f),
            start = Offset(particle.px, particle.py),
            end = Offset(particle.x, particle.y),
            strokeWidth = (0.7f + ratio * 1.2f).dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private class DiscoScene {
    val random = Random(System.nanoTime().toInt())
    val particles = Array(DiscoParticleCap) { Particle() }
    val flashes = Array(DiscoFlashCap) { Flash() }
    var particleCount = 0
    var flashCount = 0

    var time = 0f
    var level = 0f
    var baseline = 0f
    var input = 0f
    var lastBurst = -10f
    var shake = 0f
    var colorIndex = random.nextInt(4)
    var colorClock = 0f
    var bursts = 0
    var swellX = 0.5f
    var swellAmp = 0f
    var swellSpeed = 0f
    var shipStart = -100f
    var nextShip = 9f
    var shipDirection = 1f

    fun step(dt: Float, bass: Float, playing: Boolean, paletteSize: Int) {
        time += dt
        val value = if (playing) bass.coerceIn(0f, 1f) else 0f
        input = value

        if (value > level) {
            level = value
        } else {
            level += (value - level) * (1f - exp(-9f * dt))
        }

        shake *= exp(-9f * dt)
        swellAmp *= exp(-2.4f * dt)
        swellX += swellSpeed * dt
        baseline += (value - baseline) * (1f - exp(-2.6f * dt))

        // Onsets are measured against a slow baseline: per-frame deltas are tiny,
        // so this is what makes the fireworks land on the kick.
        val onset = value - baseline
        if (playing && value > 0.28f && onset > 0.095f && time - lastBurst > 0.22f) {
            lastBurst = time
            val strength = (0.35f + onset * 2.4f + value * 0.55f).coerceIn(0.35f, 1f)
            burst(strength)
            shake = minOf(11f, 3f + strength * 8f)
            swellAmp = 8f + strength * 20f
            swellX = 0.15f + random.nextFloat() * 0.7f
            swellSpeed = (0.12f + strength * 0.85f) * if (random.nextBoolean()) 1f else -1f
            bursts++
            if (bursts % 3 == 0) {
                colorIndex = (colorIndex + 1 + random.nextInt(2)) % paletteSize
                colorClock = time
            }
        }

        if (time - colorClock > 6.5f) {
            colorIndex = (colorIndex + 1) % paletteSize
            colorClock = time
        }

        if (playing && random.nextFloat() < (8f + level * 34f) * dt && particleCount < DiscoParticleCap) {
            spawn(
                x = random.nextFloat(),
                y = 0.86f + random.nextFloat() * 0.14f,
                vx = (random.nextFloat() - 0.5f) * 0.04f,
                vy = -0.02f - random.nextFloat() * 0.06f,
                life = 2.2f + random.nextFloat() * 2.2f,
                size = 0.0012f + random.nextFloat() * 0.0014f,
                gravity = -0.002f,
                drag = 0.35f,
                color = (colorIndex + random.nextInt(3)) % paletteSize,
                kind = EmberKind
            )
        }

        updateParticles(dt)
        updateFlashes(dt)

        if (time >= nextShip) {
            shipStart = time
            shipDirection = if (random.nextBoolean()) 1f else -1f
            nextShip = time + 21f + random.nextFloat() * 17f
        }
    }

    private fun burst(strength: Float) {
        val x = 0.12f + random.nextFloat() * 0.76f
        val y = 0.13f + random.nextFloat() * 0.38f
        val pattern = random.nextInt(4)
        val speed = 0.12f + strength * 0.30f
        val count = (40 + strength * 52f).toInt()

        repeat(count) { index ->
            if (particleCount >= DiscoParticleCap) return@repeat
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val ring = if (pattern == 1) {
                if (index % 2 == 0) 1f else 0.58f
            } else {
                1f
            }
            val velocity = speed * ring * (0.55f + random.nextFloat() * 0.65f)
            val color = if (pattern == 3 && index % 5 == 0) -1 else colorIndex
            spawn(
                x = x,
                y = y,
                vx = cos(angle) * velocity,
                vy = sin(angle) * velocity,
                life = 0.65f + random.nextFloat() * if (pattern == 2) 1.35f else 0.85f,
                size = 0.0018f,
                gravity = if (pattern == 2) 0.03f else 0.10f,
                drag = if (pattern == 2) 0.6f else 1.5f,
                color = color,
                kind = SparkKind,
                maxLife = if (pattern == 2) 2.0f else 1.5f
            )
        }

        repeat(8) {
            if (particleCount >= DiscoParticleCap) return@repeat
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val velocity = speed * (0.9f + random.nextFloat() * 0.5f)
            spawn(
                x = x,
                y = y,
                vx = cos(angle) * velocity,
                vy = sin(angle) * velocity,
                life = 0.28f + random.nextFloat() * 0.22f,
                size = 0.0025f,
                gravity = 0.06f,
                drag = 2.4f,
                color = -1,
                kind = SparkKind,
                maxLife = 0.55f
            )
        }

        if (flashCount < DiscoFlashCap) {
            val flash = flashes[flashCount++]
            flash.x = x
            flash.y = y
            flash.age = 0f
            flash.duration = 0.42f
            flash.size = 0.35f + strength * 0.55f
            flash.color = colorIndex
        }
    }

    private fun spawn(
        x: Float,
        y: Float,
        vx: Float,
        vy: Float,
        life: Float,
        size: Float,
        gravity: Float,
        drag: Float,
        color: Int,
        kind: Int,
        maxLife: Float = life
    ) {
        if (particleCount >= DiscoParticleCap) return
        val particle = particles[particleCount++]
        particle.x = x
        particle.y = y
        particle.px = x
        particle.py = y
        particle.vx = vx
        particle.vy = vy
        particle.life = life
        particle.maxLife = maxLife
        particle.size = size
        particle.gravity = gravity
        particle.drag = drag
        particle.color = color
        particle.kind = kind
    }

    private fun updateParticles(dt: Float) {
        var index = particleCount - 1
        while (index >= 0) {
            val particle = particles[index]
            particle.life -= dt
            if (particle.life <= 0f) {
                val last = particleCount - 1
                val removed = particles[index]
                particles[index] = particles[last]
                particles[last] = removed
                particleCount--
                index--
                continue
            }

            particle.px = particle.x
            particle.py = particle.y
            val drag = exp(-particle.drag * dt)
            particle.vx *= drag
            particle.vy = particle.vy * drag + particle.gravity * dt
            if (particle.kind == EmberKind) {
                particle.vx += sin(time * 1.9f + particle.y * 9f) * 0.03f * dt
            }
            particle.x += particle.vx * dt
            particle.y += particle.vy * dt
            index--
        }
    }

    private fun updateFlashes(dt: Float) {
        var index = flashCount - 1
        while (index >= 0) {
            val flash = flashes[index]
            flash.age += dt
            if (flash.age >= flash.duration) {
                val last = flashCount - 1
                val removed = flashes[index]
                flashes[index] = flashes[last]
                flashes[last] = removed
                flashCount--
            }
            index--
        }
    }
}

private class Particle {
    var x = 0f
    var y = 0f
    var px = 0f
    var py = 0f
    var vx = 0f
    var vy = 0f
    var life = 0f
    var maxLife = 1f
    var size = 0f
    var gravity = 0f
    var drag = 0f
    var color = 0
    var kind = 0
}

private class Flash {
    var x = 0f
    var y = 0f
    var age = 0f
    var duration = 0.4f
    var size = 0.5f
    var color = 0
}

private class DiscoBrushes(palette: List<Color>) {
    val corner = Array(palette.size) { index ->
        val tint = palette[index]
        Brush.radialGradient(
            colors = listOf(
                tint.copy(alpha = 0.26f),
                tint.copy(alpha = 0.07f),
                Color.Transparent
            )
        )
    }
    val beam = Array(palette.size) { index ->
        val tint = palette[index]
        Brush.radialGradient(
            colors = listOf(
                tint.copy(alpha = 0.34f),
                tint.copy(alpha = 0.09f),
                Color.Transparent
            ),
            center = Offset(0f, BeamBaseHeight / 2f),
            radius = BeamBaseWidth * 0.96f
        )
    }
    val lamp = Array(palette.size) { index ->
        val tint = palette[index]
        Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.85f),
                tint.copy(alpha = 0.50f),
                Color.Transparent
            )
        )
    }
    val bloom = Array(palette.size) { index ->
        val tint = palette[index]
        Brush.radialGradient(
            colors = listOf(
                tint.copy(alpha = 0.70f),
                tint.copy(alpha = 0.16f),
                Color.Transparent
            )
        )
    }
}

private class ShipPaths {
    val hull = Path().apply {
        moveTo(-94f, -6f)
        lineTo(-66f, 24f)
        lineTo(58f, 24f)
        lineTo(98f, -6f)
        lineTo(70f, -12f)
        lineTo(-72f, -12f)
        close()
    }
    val sailMain = Path().apply {
        moveTo(-32f, -84f)
        lineTo(-32f, -26f)
        quadraticBezierTo(-70f, -40f, -64f, -66f)
        close()
    }
    val sailFore = Path().apply {
        moveTo(10f, -94f)
        lineTo(10f, -24f)
        quadraticBezierTo(48f, -36f, 42f, -74f)
        close()
    }
    val jib = Path().apply {
        moveTo(48f, -70f)
        lineTo(48f, -22f)
        quadraticBezierTo(76f, -32f, 66f, -56f)
        close()
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
