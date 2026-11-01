package com.velvet.music.playback

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat

/**
 * Taps the player's audio session with the platform Visualizer and publishes log-spaced
 * bands to [SpectrumBus]. Android requires RECORD_AUDIO for this, so the caller keeps a
 * simulated spectrum as the fallback when capture is unavailable.
 */
class SpectrumCapture(private val context: Context) {

    private var visualizer: Visualizer? = null
    private var attachedSession = 0

    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (attachedSession == audioSessionId && visualizer != null) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            SpectrumBus.setLive(false)
            return
        }
        release()
        val instance = runCatching { Visualizer(audioSessionId) }.getOrNull() ?: run {
            SpectrumBus.setLive(false)
            return
        }
        val captureSize = runCatching {
            val range = Visualizer.getCaptureSizeRange()
            range[1].let { max -> if (max > 1024) 1024 else max }
        }.getOrDefault(1024)
        val rate = runCatching { Visualizer.getMaxCaptureRate() }.getOrDefault(20_000)
        val listener = object : Visualizer.OnDataCaptureListener {
            override fun onWaveFormDataCapture(
                visualizer: Visualizer?,
                waveform: ByteArray?,
                samplingRate: Int
            ) = Unit

            override fun onFftDataCapture(
                visualizer: Visualizer?,
                fft: ByteArray?,
                samplingRate: Int
            ) {
                if (fft == null) return
                SpectrumBus.push(FftBands.fromFft(fft, captureSize, samplingRate))
            }
        }
        val enabled = runCatching {
            instance.captureSize = captureSize
            instance.setDataCaptureListener(listener, rate, false, true)
            instance.enabled = true
            instance.enabled
        }.getOrDefault(false)
        if (!enabled) {
            runCatching { instance.release() }
            SpectrumBus.setLive(false)
            return
        }
        visualizer = instance
        attachedSession = audioSessionId
        SpectrumBus.setLive(true)
    }

    fun release() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        attachedSession = 0
        SpectrumBus.silence()
    }
}
