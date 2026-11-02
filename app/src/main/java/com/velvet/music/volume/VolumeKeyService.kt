package com.velvet.music.volume

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.graphics.toArgb
import com.velvet.music.data.Prefs
import com.velvet.music.ui.theme.paletteFor

class VolumeKeyService : AccessibilityService() {

    private var overlay: VolumeOverlayView? = null
    private var audioManager: AudioManager? = null
    private var windowManager: WindowManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hide = Runnable { fadeOut() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val key = event.keyCode
        if (key != KeyEvent.KEYCODE_VOLUME_UP && key != KeyEvent.KEYCODE_VOLUME_DOWN) return false

        if (event.action == KeyEvent.ACTION_DOWN) {
            val direction = if (key == KeyEvent.KEYCODE_VOLUME_UP) {
                AudioManager.ADJUST_RAISE
            } else {
                AudioManager.ADJUST_LOWER
            }
            runCatching {
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
            }
            showHud()
        }
        return true
    }

    private fun showHud() {
        val wm = windowManager ?: return
        val manager = audioManager ?: return
        val max = runCatching { manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
            .getOrDefault(15).coerceAtLeast(1)
        val current = runCatching { manager.getStreamVolume(AudioManager.STREAM_MUSIC) }
            .getOrDefault(0)
        val palette = paletteFor(Prefs(this).accent)

        val view = overlay ?: VolumeOverlayView(this).also { created ->
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                x = (-14 * resources.displayMetrics.density).toInt()
            }
            runCatching { wm.addView(created, params) }
            created.alpha = 0f
            overlay = created
        }

        view.accent = palette.primary.toArgb()
        view.bright = palette.bright.toArgb()
        view.fraction = current.toFloat() / max

        handler.removeCallbacks(hide)
        view.animate().cancel()
        view.animate().alpha(1f).setDuration(140L).start()
        handler.postDelayed(hide, 2200L)
    }

    private fun fadeOut() {
        val view = overlay ?: return
        view.animate().alpha(0f).setDuration(200L).start()
    }

    override fun onDestroy() {
        handler.removeCallbacks(hide)
        overlay?.let { view -> runCatching { windowManager?.removeView(view) } }
        overlay = null
        super.onDestroy()
    }
}
