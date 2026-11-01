using System.Runtime.InteropServices;
using NAudio.Dsp;
using NAudio.Wave;

namespace Velvet.Desktop.Services;

/// <summary>
/// Captures whatever the default output device is playing (WASAPI loopback) and
/// reduces it to 48 logarithmic bands. The Windows.MediaPlayer used for playback
/// renders through WASAPI shared mode, so its own audio shows up here too.
/// </summary>
public sealed class AudioSpectrum : IDisposable
{
    public const int BandCount = 48;
    private const int FftLength = 1024;
    private const int FftExponent = 10;
    private const float Gain = 6.5f;
    private const double FloorDb = 58;

    private readonly float[] _bands = new float[BandCount];
    private readonly float[] _working = new float[BandCount];
    private readonly float[] _samples = new float[FftLength];
    private readonly float[] _window = new float[FftLength];
    private readonly object _gate = new();
    private readonly Complex[] _fftBuffer = new Complex[FftLength];

    private WasapiLoopbackCapture? _capture;
    private int _sampleCount;
    private float _autoGain = 2.1f;
    private bool _disposed;

    public AudioSpectrum()
    {
        for (var i = 0; i < FftLength; i++)
            _window[i] = (float)(0.5 - 0.5 * Math.Cos(2 * Math.PI * i / (FftLength - 1)));
    }

    public bool Running => _capture is not null;
    public string? Error { get; private set; }

    public void Start()
    {
        if (_disposed || _capture is not null) return;
        try
        {
            var capture = new WasapiLoopbackCapture();
            capture.DataAvailable += OnData;
            capture.RecordingStopped += (_, _) => { };
            capture.StartRecording();
            _capture = capture;
            Error = null;
        }
        catch (Exception exception)
        {
            Error = exception.Message;
            _capture?.Dispose();
            _capture = null;
        }
    }

    public void Stop()
    {
        var capture = _capture;
        _capture = null;
        if (capture is null) return;
        try
        {
            capture.DataAvailable -= OnData;
            capture.StopRecording();
        }
        catch
        {
            // A device change can kill the capture mid-stop.
        }
        capture.Dispose();
        lock (_gate) Array.Clear(_bands);
    }

    public void CopyBands(float[] destination)
    {
        lock (_gate)
        {
            var count = Math.Min(destination.Length, BandCount);
            for (var i = 0; i < count; i++) destination[i] = _bands[i];
        }
    }

    private void OnData(object? sender, WaveInEventArgs e)
    {
        var capture = _capture;
        if (capture is null || e.BytesRecorded == 0) return;

        var format = capture.WaveFormat;
        if (format.Encoding != WaveFormatEncoding.IeeeFloat || format.BitsPerSample != 32) return;

        var frames = MemoryMarshal.Cast<byte, float>(e.Buffer.AsSpan(0, e.BytesRecorded));
        var channels = Math.Max(1, format.Channels);
        for (var i = 0; i + channels <= frames.Length; i += channels)
        {
            var mixed = 0f;
            for (var channel = 0; channel < channels; channel++) mixed += frames[i + channel];
            _samples[_sampleCount++] = mixed / channels;
            if (_sampleCount < FftLength) continue;
            Analyze(format.SampleRate);
            _sampleCount = 0;
        }
    }

    private void Analyze(int sampleRate)
    {
        for (var i = 0; i < FftLength; i++)
            _fftBuffer[i] = new Complex { X = _samples[i] * _window[i], Y = 0 };

        FastFourierTransform.FFT(true, FftExponent, _fftBuffer);

        var nyquist = sampleRate / 2.0;
        const double lowHz = 40;
        var highHz = Math.Min(16500, nyquist * 0.96);
        var ratio = highHz / lowHz;
        var peakBand = 0f;

        for (var band = 0; band < BandCount; band++)
        {
            var startHz = lowHz * Math.Pow(ratio, band / (double)BandCount);
            var endHz = lowHz * Math.Pow(ratio, (band + 1) / (double)BandCount);
            var firstBin = Math.Max(1, (int)(startHz / nyquist * FftLength / 2));
            var lastBin = Math.Clamp((int)(endHz / nyquist * FftLength / 2), firstBin, FftLength / 2 - 1);

            var peak = 0f;
            for (var bin = firstBin; bin <= lastBin; bin++)
            {
                var magnitude = (float)Math.Sqrt(
                    _fftBuffer[bin].X * _fftBuffer[bin].X + _fftBuffer[bin].Y * _fftBuffer[bin].Y);
                if (magnitude > peak) peak = magnitude;
            }

            // NAudio's FFT is already normalized by N.
            var decibels = 20 * Math.Log10(peak * Gain + 1e-7);
            var normalized = Math.Clamp((float)((decibels + FloorDb) / FloorDb), 0f, 1f);
            _working[band] = normalized;
            if (normalized > peakBand) peakBand = normalized;
        }

        // Slow auto-gain keeps quiet tracks from reading as a flat line.
        var desired = 1f / Math.Max(0.18f, peakBand);
        _autoGain = Math.Clamp(_autoGain * 0.94f + desired * 0.06f, 1f, 4.2f);

        lock (_gate)
        {
            for (var band = 0; band < BandCount; band++)
            {
                var target = Math.Clamp((float)Math.Pow(_working[band], 1.35) * _autoGain, 0f, 1f);
                _bands[band] = target >= _bands[band]
                    ? _bands[band] * 0.35f + target * 0.65f
                    : _bands[band] * 0.76f + target * 0.24f;
            }
        }
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        Stop();
    }
}
