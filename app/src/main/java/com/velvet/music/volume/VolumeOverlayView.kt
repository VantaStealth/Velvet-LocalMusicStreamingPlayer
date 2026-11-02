package com.velvet.music.volume

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.roundToInt

class VolumeOverlayView(context: Context) : View(context) {

    var fraction: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var accent: Int = 0xFFA78BFA.toInt()
        set(value) {
            field = value
            invalidate()
        }

    var bright: Int = 0xFFC9BCFF.toInt()
        set(value) {
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private fun dp(value: Float) = value * density

    private val card = Paint(Paint.ANTI_ALIAS_FLAG)
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val hairline = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val rail = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(dp(88f).toInt(), dp(306f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = dp(30f)
        val bounds = RectF(0f, 0f, w, h)

        card.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(0xF0171220.toInt(), 0xF7080610.toInt()),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(bounds, radius, radius, card)
        card.shader = null

        border.color = accent.withAlpha(92)
        border.strokeWidth = dp(1f)
        canvas.drawRoundRect(
            RectF(dp(0.5f), dp(0.5f), w - dp(0.5f), h - dp(0.5f)),
            radius,
            radius,
            border
        )

        hairline.strokeWidth = dp(1.2f)
        hairline.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(Color.TRANSPARENT, bright.withAlpha(190), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawLine(w * 0.15f, dp(1.2f), w * 0.85f, dp(1.2f), hairline)
        hairline.shader = null

        text.letterSpacing = 0.30f
        text.textSize = dp(8.5f)
        text.color = accent.withAlpha(215)
        canvas.drawText("VELVET", w / 2f, dp(42f), text)

        val railTop = dp(66f)
        val railBottom = h - dp(70f)
        val cx = w / 2f

        rail.strokeWidth = dp(6f)
        rail.color = Color.argb(38, 255, 255, 255)
        canvas.drawLine(cx, railTop, cx, railBottom, rail)

        val top = railBottom - (railBottom - railTop) * fraction
        if (fraction > 0.001f) {
            fill.strokeWidth = dp(6f)
            fill.shader = LinearGradient(
                0f, railBottom, 0f, railTop,
                intArrayOf(accent, bright),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawLine(cx, railBottom, cx, top.coerceAtMost(railBottom - dp(1f)), fill)
            fill.shader = null
        }

        dot.color = accent.withAlpha(58)
        canvas.drawCircle(cx, top, dp(11f), dot)
        dot.color = accent.withAlpha(170)
        canvas.drawCircle(cx, top, dp(6.5f), dot)
        dot.color = Color.WHITE
        canvas.drawCircle(cx, top, dp(3f), dot)

        text.letterSpacing = 0f
        text.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        text.textSize = dp(21f)
        text.color = 0xFFF6F3FA.toInt()
        canvas.drawText("${(fraction * 100).roundToInt()}", w / 2f, h - dp(34f), text)

        text.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        text.letterSpacing = 0.24f
        text.textSize = dp(7.5f)
        text.color = 0xFF6F677B.toInt()
        canvas.drawText("MEDIA", w / 2f, h - dp(16f), text)
    }

    private fun Int.withAlpha(alpha: Int): Int =
        (this and 0x00FFFFFF) or ((alpha.coerceIn(0, 255)) shl 24)
}
