package com.velvet.music.volume

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioManager
import android.view.MotionEvent
import android.view.View
import com.velvet.music.data.Prefs
import java.util.Random
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The global volume surface drawn over other apps. Collapsed it echoes the
 * original Velvet pill: liquid orb, big reading, glowing rail and MIX pill.
 * Tapping MIX opens the full stream mixer without leaving the current app.
 */
class VolumeOverlayView(context: Context) : View(context) {

    var fraction: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var accent: Int = 0xFFC21E2C.toInt()
        set(value) {
            field = value
            invalidate()
        }

    var bright: Int = 0xFFFF5B5B.toInt()
        set(value) {
            field = value
            invalidate()
        }

    val isExpanded: Boolean get() = expanded

    var onInteraction: (() -> Unit)? = null
    var onModeChange: ((Boolean) -> Unit)? = null

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val prefs = Prefs(context)

    private val streamValues = IntArray(velvetStreams.size)
    private val streamMax = IntArray(velvetStreams.size) { 15 }
    private var appVolume = 1f
    private var muted = false
    private var expanded = false

    private var draggingMusic = false
    private var draggingKind = -1
    private var draggingIndex = -1

    private val mixRect = RectF()
    private val railRect = RectF()
    private val chevronRect = RectF()
    private val sliderHits = mutableListOf<SliderHit>()

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val hairlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val veinGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val veinLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orbBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val railPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val railBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sliderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val sliderActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sans = Typeface.create("sans-serif", Typeface.NORMAL)
    private val sansMedium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val sansBold = Typeface.create("sans-serif", Typeface.BOLD)

    init {
        isClickable = true
        isFocusable = false
    }

    fun refresh() {
        val manager = audioManager ?: return
        for (index in velvetStreams.indices) {
            val stream = velvetStreams[index].stream
            streamMax[index] = runCatching { manager.getStreamMaxVolume(stream) }
                .getOrDefault(15).coerceAtLeast(1)
            streamValues[index] = runCatching { manager.getStreamVolume(stream) }
                .getOrDefault(0)
        }
        fraction = streamValues[0].toFloat() / streamMax[0]
        muted = runCatching { manager.isStreamMute(AudioManager.STREAM_MUSIC) }
            .getOrDefault(false)
        appVolume = prefs.appVolume.coerceIn(0f, 1.5f)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            dp(if (expanded) 304f else 96f).toInt(),
            dp(if (expanded) 584f else 352f).toInt()
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onInteraction?.invoke()
                if (!expanded) {
                    if (mixRect.contains(x, y)) {
                        setExpanded(true)
                        return true
                    }
                    if (railRect.contains(x, y)) {
                        draggingMusic = true
                        applyRailTouch(y)
                        return true
                    }
                } else {
                    if (chevronRect.contains(x, y)) {
                        setExpanded(false)
                        return true
                    }
                    val hit = sliderHits.firstOrNull { it.bounds.contains(x, y) }
                    if (hit != null) {
                        draggingKind = hit.kind
                        draggingIndex = hit.index
                        applySlider(hit, x)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingMusic) {
                    applyRailTouch(y)
                    onInteraction?.invoke()
                    return true
                }
                if (draggingKind >= 0) {
                    val hit = sliderHits.firstOrNull {
                        it.kind == draggingKind && it.index == draggingIndex
                    }
                    if (hit != null) {
                        applySlider(hit, x)
                        onInteraction?.invoke()
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingMusic = false
                draggingKind = -1
                draggingIndex = -1
                onInteraction?.invoke()
                return true
            }
        }
        return true
    }

    private fun setExpanded(value: Boolean) {
        if (expanded == value) return
        expanded = value
        draggingMusic = false
        draggingKind = -1
        requestLayout()
        invalidate()
        onModeChange?.invoke(value)
    }

    private fun applyRailTouch(y: Float) {
        if (railRect.height() <= 0f) return
        val level = ((railRect.bottom - y) / railRect.height()).coerceIn(0f, 1f)
        applyStream(0, level)
    }

    private fun applySlider(hit: SliderHit, x: Float) {
        if (hit.trackWidth <= 0f) return
        val level = ((x - hit.trackLeft) / hit.trackWidth).coerceIn(0f, 1f)
        if (hit.kind == KIND_APP) {
            appVolume = level
            prefs.appVolume = level
            context.sendBroadcast(
                Intent(ACTION_APP_VOLUME)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_VOLUME, level)
            )
            invalidate()
        } else {
            applyStream(hit.index, level)
        }
    }

    private fun applyStream(index: Int, level: Float) {
        val manager = audioManager ?: return
        val max = streamMax[index].coerceAtLeast(1)
        val value = (level * max).roundToInt()
        runCatching { manager.setStreamVolume(velvetStreams[index].stream, value, 0) }
        streamValues[index] = value
        if (index == 0) {
            fraction = value.toFloat() / max
            muted = false
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (expanded) drawExpanded(canvas) else drawCollapsed(canvas)
    }

    // ---------------------------------------------------------------- collapsed

    private fun drawCollapsed(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val card = RectF(dp(6f), dp(6f), w - dp(6f), h - dp(6f))
        drawCard(canvas, card, dp(42f))

        val cx = w / 2f
        val orb = RectF(cx - dp(24f), card.top + dp(26f), cx + dp(24f), card.top + dp(88f))
        drawOrb(canvas, orb, dp(24f))

        val percent = (fraction * 100f).roundToInt()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = sansBold
        textPaint.letterSpacing = 0f
        textPaint.color = 0xFFF8F5FA.toInt()
        if (muted) {
            textPaint.textSize = dp(20f)
            canvas.drawText("MUTE", cx, card.top + dp(126f), textPaint)
        } else {
            textPaint.textSize = dp(30f)
            canvas.drawText("$percent", cx, card.top + dp(132f), textPaint)
        }

        textPaint.typeface = sansMedium
        textPaint.textSize = dp(9f)
        textPaint.letterSpacing = 0.30f
        textPaint.color = bright.withAlpha(225)
        canvas.drawText("MEDIA", cx, card.top + dp(152f), textPaint)
        textPaint.letterSpacing = 0f

        val railTop = card.top + dp(170f)
        val railBottom = card.bottom - dp(54f)
        val railHalf = dp(15f)
        val rail = RectF(cx - railHalf, railTop, cx + railHalf, railBottom)
        val radius = railHalf
        railRect.set(cx - dp(24f), railTop - dp(6f), cx + dp(24f), railBottom + dp(6f))

        railPaint.strokeWidth = dp(30f)
        railPaint.color = 0xFF17121D.toInt()
        canvas.drawLine(cx, railTop + radius, cx, railBottom - radius, railPaint)
        canvas.drawCircle(cx, railTop + radius, radius, railPaint)
        canvas.drawCircle(cx, railBottom - radius, radius, railPaint)

        railBorderPaint.strokeWidth = dp(1f)
        railBorderPaint.color = 0x33FFFFFF
        canvas.drawRoundRect(rail, radius, radius, railBorderPaint)

        tickPaint.strokeWidth = dp(1f)
        tickPaint.color = 0x2EFFFFFF
        for (step in 1..2) {
            val ty = railTop + (railBottom - railTop) * step / 3f
            canvas.drawLine(cx - dp(6f), ty, cx + dp(6f), ty, tickPaint)
        }

        val level = if (muted) 0f else fraction
        val knobY = railBottom - (railBottom - railTop) * level
        if (level > 0.004f) {
            val fillRect = RectF(cx - dp(11.5f), knobY, cx + dp(11.5f), railBottom)
            fillPaint.shader = LinearGradient(
                fillRect.left, knobY, fillRect.right, railBottom,
                intArrayOf(bright, accent, darken(accent, 0.45f)),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(fillRect, dp(11.5f), dp(11.5f), fillPaint)
            fillPaint.shader = null
        }

        knobPaint.color = accent.withAlpha(54)
        canvas.drawCircle(cx, knobY, dp(11f), knobPaint)
        knobPaint.color = bright.withAlpha(210)
        canvas.drawCircle(cx, knobY, dp(6.4f), knobPaint)
        knobPaint.color = 0xFFFFF3EC.toInt()
        canvas.drawCircle(cx, knobY, dp(4.2f), knobPaint)

        val mix = RectF(cx - dp(28f), card.bottom - dp(36f), cx + dp(28f), card.bottom - dp(12f))
        mixRect.set(mix)
        val mixRadius = mix.height() / 2f
        cardPaint.color = accent.withAlpha(28)
        canvas.drawRoundRect(mix, mixRadius, mixRadius, cardPaint)
        borderPaint.strokeWidth = dp(1f)
        borderPaint.color = accent.withAlpha(115)
        canvas.drawRoundRect(mix, mixRadius, mixRadius, borderPaint)
        textPaint.typeface = sansMedium
        textPaint.textSize = dp(8.5f)
        textPaint.letterSpacing = 0.32f
        textPaint.color = bright.withAlpha(230)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("MIX", cx, mix.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
        textPaint.letterSpacing = 0f
    }

    // ----------------------------------------------------------------- expanded

    private fun drawExpanded(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val card = RectF(dp(6f), dp(6f), w - dp(6f), h - dp(6f))
        drawCard(canvas, card, dp(28f))

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = sansMedium
        textPaint.textSize = dp(9f)
        textPaint.letterSpacing = 0.30f
        textPaint.color = bright.withAlpha(220)
        canvas.drawText("VOLUME", card.left + dp(20f), card.top + dp(30f), textPaint)
        textPaint.letterSpacing = 0f

        chevronRect.set(card.right - dp(46f), card.top + dp(10f), card.right - dp(14f), card.top + dp(42f))
        knobPaint.color = 0x0FFFFFFF
        canvas.drawCircle(chevronRect.centerX(), chevronRect.centerY(), chevronRect.width() / 2f, knobPaint)
        borderPaint.strokeWidth = dp(1f)
        borderPaint.color = 0x24FFFFFF
        canvas.drawCircle(chevronRect.centerX(), chevronRect.centerY(), chevronRect.width() / 2f, borderPaint)
        val chevron = Path().apply {
            moveTo(chevronRect.centerX() - dp(6f), chevronRect.centerY() - dp(2.5f))
            lineTo(chevronRect.centerX(), chevronRect.centerY() + dp(3.5f))
            lineTo(chevronRect.centerX() + dp(6f), chevronRect.centerY() - dp(2.5f))
        }
        tickPaint.strokeWidth = dp(1.4f)
        tickPaint.color = 0xFFB9B1C4.toInt()
        canvas.drawPath(chevron, tickPaint)

        val orb = RectF(
            card.left + dp(20f), card.top + dp(44f),
            card.left + dp(74f), card.top + dp(108f)
        )
        drawOrb(canvas, orb, dp(24f))

        textPaint.typeface = sansMedium
        textPaint.textSize = dp(19f)
        textPaint.color = 0xFFF8F5FA.toInt()
        canvas.drawText("Media", orb.right + dp(16f), card.top + dp(72f), textPaint)
        bodyPaint.textAlign = Paint.Align.LEFT
        bodyPaint.typeface = sans
        bodyPaint.textSize = dp(11f)
        bodyPaint.color = 0x9FA69DB2.toInt()
        canvas.drawText("Music and podcasts", orb.right + dp(16f), card.top + dp(92f), bodyPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = sansBold
        textPaint.textSize = dp(24f)
        textPaint.color = bright.withAlpha(240)
        canvas.drawText(
            if (muted) "MUTE" else "${(fraction * 100f).roundToInt()}",
            card.right - dp(20f), card.top + dp(78f), textPaint
        )
        textPaint.textAlign = Paint.Align.LEFT

        sliderHits.clear()
        var rowTop = card.top + dp(124f)
        velvetStreams.forEachIndexed { index, stream ->
            drawStreamRow(canvas, card, rowTop, index, stream)
            rowTop += dp(60f)
        }

        tickPaint.strokeWidth = dp(1f)
        tickPaint.color = 0x14FFFFFF
        canvas.drawLine(
            card.left + dp(20f), rowTop + dp(4f),
            card.right - dp(20f), rowTop + dp(4f), tickPaint
        )

        val channelTop = rowTop + dp(26f)
        knobPaint.color = accent
        canvas.drawCircle(card.left + dp(24f), channelTop - dp(4f), dp(4f), knobPaint)
        textPaint.typeface = sansMedium
        textPaint.textSize = dp(13f)
        textPaint.color = 0xFFF6F3FA.toInt()
        canvas.drawText("Velvet channel", card.left + dp(36f), channelTop, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = dp(11f)
        textPaint.color = bright.withAlpha(230)
        canvas.drawText(
            "${(appVolume * 100f).roundToInt()}%",
            card.right - dp(20f), channelTop, textPaint
        )
        textPaint.textAlign = Paint.Align.LEFT

        bodyPaint.textSize = dp(10.5f)
        bodyPaint.color = 0x8FA69DB2.toInt()
        canvas.drawText(
            "Trims or boosts only Velvet, independent of the",
            card.left + dp(20f), channelTop + dp(18f), bodyPaint
        )
        canvas.drawText(
            "media stream.",
            card.left + dp(20f), channelTop + dp(33f), bodyPaint
        )

        val channelY = channelTop + dp(55f)
        val trackLeft = card.left + dp(20f)
        val trackRight = card.right - dp(20f)
        drawSlider(canvas, trackLeft, trackRight, channelY, appVolume)
        sliderHits += sliderHit(KIND_APP, 0, trackLeft, trackRight, channelY)

        bodyPaint.textSize = dp(10f)
        bodyPaint.color = 0x766F677B
        canvas.drawText(
            "Slide past 100% to push Velvet above a game",
            card.left + dp(20f), channelY + dp(30f), bodyPaint
        )
        canvas.drawText(
            "or video. Android shares one media stream",
            card.left + dp(20f), channelY + dp(44f), bodyPaint
        )
        canvas.drawText(
            "across apps, so lowering other apps needs root.",
            card.left + dp(20f), channelY + dp(58f), bodyPaint
        )
    }

    private fun drawStreamRow(
        canvas: Canvas,
        card: RectF,
        top: Float,
        index: Int,
        stream: VelvetStream
    ) {
        val left = card.left + dp(20f)
        val right = card.right - dp(20f)
        val active = index == 0
        val value = streamValues[index].toFloat() / streamMax[index]

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = sansMedium
        textPaint.textSize = dp(12.5f)
        textPaint.color = if (active) bright.withAlpha(245) else 0xFFF1EDF7.toInt()
        canvas.drawText(stream.label, left, top + dp(14f), textPaint)
        val labelWidth = textPaint.measureText(stream.label)

        bodyPaint.textAlign = Paint.Align.LEFT
        bodyPaint.typeface = sans
        bodyPaint.textSize = dp(10.5f)
        bodyPaint.color = 0x8FA69DB2.toInt()
        canvas.drawText(stream.detail, left + labelWidth + dp(9f), top + dp(14f), bodyPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.textSize = dp(11f)
        textPaint.color = if (active) bright.withAlpha(235) else 0xFFA69DB2.toInt()
        canvas.drawText(
            "${(value * 100f).roundToInt()}%",
            right, top + dp(14f), textPaint
        )
        textPaint.textAlign = Paint.Align.LEFT

        val sliderY = top + dp(35f)
        drawSlider(canvas, left, right, sliderY, value)
        sliderHits += sliderHit(KIND_STREAM, index, left, right, sliderY)
    }

    // -------------------------------------------------------------------- pieces

    private fun drawCard(canvas: Canvas, card: RectF, radius: Float) {
        glowPaint.strokeWidth = dp(6f)
        glowPaint.color = accent.withAlpha(16)
        val outer = RectF(card)
        outer.inset(-dp(2.4f), -dp(2.4f))
        canvas.drawRoundRect(outer, radius + dp(2.4f), radius + dp(2.4f), glowPaint)

        cardPaint.shader = LinearGradient(
            card.left, card.top, card.left, card.bottom,
            intArrayOf(0xF21A0F1F.toInt(), 0xF60C0810.toInt(), 0xF708060C.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(card, radius, radius, cardPaint)
        cardPaint.shader = null

        val clip = Path().apply { addRoundRect(card, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        drawVeins(canvas, card)
        canvas.restore()

        borderPaint.strokeWidth = dp(1.1f)
        borderPaint.color = accent.withAlpha(95)
        canvas.drawRoundRect(card, radius, radius, borderPaint)

        hairlinePaint.strokeWidth = dp(1.4f)
        hairlinePaint.shader = LinearGradient(
            card.left, card.top, card.right, card.top,
            intArrayOf(
                Color.TRANSPARENT,
                bright.withAlpha(200),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.12f, 0.5f, 0.88f),
            Shader.TileMode.CLAMP
        )
        canvas.drawLine(
            card.left + card.width() * 0.14f, card.top + dp(1.2f),
            card.right - card.width() * 0.14f, card.top + dp(1.2f),
            hairlinePaint
        )
        hairlinePaint.shader = null
    }

    private fun drawVeins(canvas: Canvas, card: RectF) {
        val random = Random(41)
        val count = if (expanded) 8 else 5
        veinGlowPaint.strokeWidth = dp(5f)
        veinGlowPaint.color = accent.withAlpha(13)
        veinLinePaint.strokeWidth = dp(1f)
        veinLinePaint.color = accent.withAlpha(46)
        repeat(count) {
            val path = Path()
            var x = card.left - card.width() * 0.06f
            var y = card.top + card.height() * random.nextFloat()
            path.moveTo(x, y)
            val segments = 4 + random.nextInt(3)
            val stepX = card.width() / segments * (0.72f + random.nextFloat() * 0.55f)
            repeat(segments) {
                val nextX = x + stepX
                val nextY = y + (random.nextFloat() - 0.5f) * card.height() * 0.46f
                path.cubicTo(
                    x + stepX * 0.4f, y + (random.nextFloat() - 0.5f) * card.height() * 0.3f,
                    x + stepX * 0.78f, nextY + (random.nextFloat() - 0.5f) * card.height() * 0.2f,
                    nextX, nextY
                )
                x = nextX
                y = nextY
            }
            canvas.drawPath(path, veinGlowPaint)
            canvas.drawPath(path, veinLinePaint)
        }
    }

    private fun drawOrb(canvas: Canvas, orb: RectF, radius: Float) {
        cardPaint.color = 0xFF150D19.toInt()
        canvas.drawRoundRect(orb, radius, radius, cardPaint)

        val level = if (muted) 0f else fraction.coerceIn(0f, 1f)
        val surface = orb.bottom - orb.height() * level
        val clip = Path().apply { addRoundRect(orb, radius, radius, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)

        canvas.drawRect(orb.left, surface, orb.right, orb.bottom, radialHaze(orb))
        if (level > 0.001f) {
            liquidPaint.shader = LinearGradient(
                orb.left, surface, orb.left, orb.bottom,
                intArrayOf(bright, accent, darken(accent, 0.32f)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(orb.left, surface, orb.right, orb.bottom, liquidPaint)
            liquidPaint.shader = null

            val wave = Path()
            wave.moveTo(orb.left, surface)
            var x = orb.left
            while (x <= orb.right) {
                val y = surface + sin((x - orb.left) / orb.width() * 2.4f + 0.7f) * dp(1.6f)
                wave.lineTo(x, y)
                x += dp(3f)
            }
            wavePaint.strokeWidth = dp(1f)
            wavePaint.color = 0x59FFFFFF
            canvas.drawPath(wave, wavePaint)

            knobPaint.color = 0x3DFFFFFF
            canvas.drawCircle(orb.centerX() + dp(7f), surface + dp(20f), dp(3f), knobPaint)
            canvas.drawCircle(orb.centerX() - dp(5f), surface + dp(34f), dp(2f), knobPaint)
        }

        canvas.drawRect(orb.left, orb.top, orb.right, orb.top + orb.height() * 0.42f, topShade(orb))
        canvas.restore()

        orbBorderPaint.strokeWidth = dp(1f)
        orbBorderPaint.color = 0x38FFFFFF
        canvas.drawRoundRect(orb, radius, radius, orbBorderPaint)
        orbBorderPaint.strokeWidth = dp(1f)
        orbBorderPaint.color = accent.withAlpha(72)
        canvas.drawRoundRect(
            RectF(orb.left + dp(1f), orb.top + dp(1f), orb.right - dp(1f), orb.bottom - dp(1f)),
            radius, radius, orbBorderPaint
        )
    }

    private fun radialHaze(orb: RectF): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            orb.centerX(), orb.top + orb.height() * 0.55f, orb.width() * 0.75f,
            intArrayOf(accent.withAlpha(28), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        return paint
    }

    private fun topShade(orb: RectF): Paint {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        return paint.apply {
            shader = LinearGradient(
                orb.left, orb.top, orb.left, orb.top + orb.height() * 0.5f,
                intArrayOf(0xB308060C.toInt(), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
        }
    }

    private fun drawSlider(
        canvas: Canvas,
        left: Float,
        right: Float,
        y: Float,
        value: Float
    ) {
        val level = value.coerceIn(0f, 1f)
        sliderPaint.strokeWidth = dp(2.4f)
        sliderPaint.color = 0x3AFFFFFF
        canvas.drawLine(left, y, right, y, sliderPaint)

        val tip = left + (right - left) * level
        sliderActivePaint.strokeWidth = dp(2.8f)
        sliderActivePaint.shader = LinearGradient(
            left, y, right, y,
            intArrayOf(accent.withAlpha(190), bright),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawLine(left, y, tip, y, sliderActivePaint)
        sliderActivePaint.shader = null

        knobPaint.color = accent.withAlpha(58)
        canvas.drawCircle(tip, y, dp(8.5f), knobPaint)
        knobPaint.color = 0xFFFFF7F1.toInt()
        canvas.drawCircle(tip, y, dp(4.6f), knobPaint)
        knobPaint.color = accent.withAlpha(200)
        canvas.drawCircle(tip, y, dp(2f), knobPaint)
    }

    private fun darken(color: Int, amount: Float): Int {
        val keep = (1f - amount).coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(color) * keep).roundToInt(),
            (Color.green(color) * keep).roundToInt(),
            (Color.blue(color) * keep).roundToInt()
        )
    }

    private fun Int.withAlpha(alpha: Int): Int =
        (this and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)

    private fun sliderHit(
        kind: Int,
        index: Int,
        left: Float,
        right: Float,
        centerY: Float
    ) = SliderHit(
        kind,
        index,
        left,
        right,
        RectF(
            left - dp(12f), centerY - dp(17f),
            right + dp(12f), centerY + dp(17f)
        )
    )

    private class SliderHit(
        val kind: Int,
        val index: Int,
        val trackLeft: Float,
        val trackRight: Float,
        val bounds: RectF
    ) {
        val trackWidth: Float get() = trackRight - trackLeft
    }

    private companion object {
        const val KIND_STREAM = 0
        const val KIND_APP = 1
    }
}
