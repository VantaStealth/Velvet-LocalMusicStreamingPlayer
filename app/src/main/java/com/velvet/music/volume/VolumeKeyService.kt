package com.velvet.music.volume

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
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
        if (key != KeyEvent.KEYCODE_VOLUME_UP &&
            key != KeyEvent.KEYCODE_VOLUME_DOWN &&
            key != KeyEvent.KEYCODE_VOLUME_MUTE
        ) {
            return false
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val manager = audioManager
            runCatching {
                when (key) {
                    KeyEvent.KEYCODE_VOLUME_UP ->
                        manager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                    KeyEvent.KEYCODE_VOLUME_DOWN ->
                        manager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                    else ->
                        manager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
                }
            }
            showHud()
        }
        return true
    }

    private fun showHud() {
        val wm = windowManager ?: return
        val palette = paletteFor(Prefs(this).accent)
        val existing = overlay

        val view = existing ?: VolumeOverlayView(this).also { created ->
            created.onInteraction = { scheduleHide() }
            created.onModeChange = { scheduleHide() }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                x = (-10 * resources.displayMetrics.density).toInt()
            }
            created.alpha = 0f
            created.translationX = 18 * resources.displayMetrics.density
            runCatching { wm.addView(created, params) }
            overlay = created
        }

        view.accent = palette.primary.toArgb()
        view.bright = palette.bright.toArgb()
        view.refresh()

        if (existing == null) {
            view.animate().alpha(1f).translationX(0f).setDuration(160L).start()
        } else {
            view.animate().cancel()
            view.alpha = 1f
            view.translationX = 0f
        }
        scheduleHide()
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hide)
        val expanded = overlay?.isExpanded == true
        handler.postDelayed(hide, if (expanded) 7000L else 2600L)
    }

    private fun fadeOut() {
        val view = overlay ?: return
        overlay = null
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationX(18 * resources.displayMetrics.density)
            .setDuration(190L)
            .withEndAction {
                runCatching { windowManager?.removeView(view) }
            }
            .start()
    }

    override fun onDestroy() {
        handler.removeCallbacks(hide)
        overlay?.let { view ->
            view.animate().cancel()
            runCatching { windowManager?.removeView(view) }
        }
        overlay = null
        super.onDestroy()
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE)
                as? AccessibilityManager ?: return false
            return manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            ).any { info ->
                val service = info.resolveInfo?.serviceInfo ?: return@any false
                service.packageName == context.packageName &&
                    service.name == VolumeKeyService::class.java.name
            }
        }
    }
}
