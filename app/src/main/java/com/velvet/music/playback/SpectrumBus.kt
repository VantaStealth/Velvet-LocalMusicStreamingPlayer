package com.velvet.music.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max

/** Raw normalised spectrum shared from the playback service to Compose. */
object SpectrumBus {

    const val BAND_COUNT = 48

    private val _bands = MutableStateFlow(FloatArray(BAND_COUNT))
    val bands: StateFlow<FloatArray> = _bands

    private val _live = MutableStateFlow(false)
    val live: StateFlow<Boolean> = _live

    fun push(values: FloatArray) {
        _bands.value = values
    }

    fun setLive(value: Boolean) {
        _live.value = value
    }

    fun silence() {
        _bands.value = FloatArray(BAND_COUNT)
        _live.value = false
    }
}

/**
 * Maps the platform Visualizer FFT onto [SpectrumBus.BAND_COUNT] log bands.
 *
 * The byte layout is Rf0, Rf(n/2), Rf1, If1, Rf2, If2 … so frequency bin 0 is the
 * DC term, bin [size/2] is Nyquist, and everything between is an interleaved
 * real/imaginary pair. Values are signed 8-bit and [sampleRateMilliHz] is reported
 * in milliHertz, matching Visualizer.OnDataCaptureListener.
 */
internal object FftBands {

    private const val MIN_HZ = 40.0
    private const val MAX_HZ = 16_000.0
    private const val FLOOR_DB = 58.0
    private const val SILENCE_MAGNITUDE = 1e-5

    private var gain = 1f

    fun fromFft(fft: ByteArray, captureSize: Int, sampleRateMilliHz: Int = 44_100_000): FloatArray {
        if (fft.size < 8 || captureSize < 8) return FloatArray(SpectrumBus.BAND_COUNT)

        val sampleHz = (if (sampleRateMilliHz > 4_000) sampleRateMilliHz / 1000.0 else sampleRateMilliHz.toDouble())
            .takeIf { it in 4_000.0..384_000.0 }
            ?: 44_100.0
        val binHz = sampleHz / captureSize
        val maxBin = minOf(captureSize / 2, (fft.size - 2) / 2 + 1)
        if (maxBin < 2 || binHz <= 0.0) return FloatArray(SpectrumBus.BAND_COUNT)

        val result = FloatArray(SpectrumBus.BAND_COUNT)
        val logMin = ln(MIN_HZ)
        val logSpan = ln(MAX_HZ) - logMin
        var loudest = 0f

        for (band in 0 until SpectrumBus.BAND_COUNT) {
            val low = exp(logMin + logSpan * band / SpectrumBus.BAND_COUNT) / binHz
            val high = exp(logMin + logSpan * (band + 1) / SpectrumBus.BAND_COUNT) / binHz
            val start = low.toInt().coerceIn(1, maxBin - 1)
            val end = high.toInt().coerceIn(start + 1, maxBin)

            var peakDb = -120.0
            var totalDb = 0.0
            var count = 0
            for (bin in start..end) {
                val magnitudeValue = magnitude(fft, bin, maxBin).toDouble().coerceAtLeast(SILENCE_MAGNITUDE)
                val db = 20 * log10(magnitudeValue)
                if (db > peakDb) peakDb = db
                totalDb += db
                count++
            }
            if (count == 0) continue

            val mixedDb = peakDb * 0.62 + (totalDb / count) * 0.38
            result[band] = (((mixedDb + FLOOR_DB) / FLOOR_DB).coerceIn(0.0, 1.0)).toFloat()
            if (result[band] > loudest) loudest = result[band]
        }

        // Auto-gain keeps quiet masters visible without amplifying true silence.
        val desired = 1f / max(0.24f, loudest)
        gain = if (loudest > 0.10f) {
            (gain * 0.88f + desired * 0.12f).coerceIn(1f, 3.4f)
        } else {
            gain * 0.90f + 1f * 0.10f
        }

        for (band in 0 until SpectrumBus.BAND_COUNT) {
            val tilt = 1f + 0.18f * (1f - band / SpectrumBus.BAND_COUNT.toFloat())
            result[band] = (result[band] * gain * tilt).coerceIn(0f, 1f)
        }
        return result
    }

    private fun magnitude(fft: ByteArray, bin: Int, maxBin: Int): Float {
        if (bin <= 0) return abs(fft[0].toInt()) / 128f
        if (bin >= maxBin) return abs(fft[1].toInt()) / 128f
        val offset = 2 + (bin - 1) * 2
        if (offset + 1 >= fft.size) return 0f
        val real = fft[offset].toInt()
        val imaginary = fft[offset + 1].toInt()
        return (hypot(real.toDouble(), imaginary.toDouble()) / 128.0).toFloat()
    }
}
