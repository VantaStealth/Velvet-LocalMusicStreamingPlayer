using System.Windows;
using System.Windows.Media;

namespace Velvet.Desktop.Controls;

public sealed class SpectrumVisualizer : FrameworkElement
{
    public static readonly DependencyProperty ActiveProperty = DependencyProperty.Register(
        nameof(Active), typeof(bool), typeof(SpectrumVisualizer),
        new FrameworkPropertyMetadata(false, FrameworkPropertyMetadataOptions.AffectsRender, OnActiveChanged));

    public static readonly DependencyProperty SensitivityProperty = DependencyProperty.Register(
        nameof(Sensitivity), typeof(double), typeof(SpectrumVisualizer),
        new FrameworkPropertyMetadata(1.35, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty AccentBrushProperty = DependencyProperty.Register(
        nameof(AccentBrush), typeof(Brush), typeof(SpectrumVisualizer),
        new FrameworkPropertyMetadata(Brushes.MediumPurple, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty BrightBrushProperty = DependencyProperty.Register(
        nameof(BrightBrush), typeof(Brush), typeof(SpectrumVisualizer),
        new FrameworkPropertyMetadata(Brushes.White, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty BandsProperty = DependencyProperty.Register(
        nameof(Bands), typeof(int), typeof(SpectrumVisualizer),
        new FrameworkPropertyMetadata(48, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty InnerFractionProperty = DependencyProperty.Register(
        nameof(InnerFraction), typeof(double), typeof(SpectrumVisualizer),
        new FrameworkPropertyMetadata(0.362, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty LengthFractionProperty = DependencyProperty.Register(
        nameof(LengthFraction), typeof(double), typeof(SpectrumVisualizer),
        new FrameworkPropertyMetadata(0.09, FrameworkPropertyMetadataOptions.AffectsRender));

    public static readonly DependencyProperty GlowBrushProperty = DependencyProperty.Register(
        nameof(GlowBrush), typeof(Brush), typeof(SpectrumVisualizer),
        new FrameworkPropertyMetadata(null, FrameworkPropertyMetadataOptions.AffectsRender));

    private const int SparkCount = 36;
    private const int SparkAlphaLevels = 12;

    private readonly float[] _target = new float[48];
    private readonly float[] _values = new float[48];
    private readonly Spark[] _sparks = new Spark[SparkCount];
    private readonly Random _random = new(7);

    private TimeSpan _lastFrame;
    private bool _hooked;
    private double _spin;
    private double _bass;
    private double _level;
    private double _cachedSize;
    private Brush _cachedAccent = Brushes.MediumPurple;
    private Brush _cachedBright = Brushes.White;
    private Pen? _glowPen;
    private Pen? _bodyPen;
    private Pen? _tipPen;
    private Pen? _ringPen;
    private Brush[]? _sparkBrushes;
    private Brush? _hazeBrush;

    public SpectrumVisualizer()
    {
        IsHitTestVisible = false;
        ClipToBounds = false;
        for (var i = 0; i < _sparks.Length; i++) _sparks[i] = new Spark();
        Loaded += (_, _) => SyncLoop();
        Unloaded += (_, _) => SyncLoop();
        IsVisibleChanged += (_, _) => SyncLoop();
    }

    public Func<float[]>? BandsProvider { get; set; }

    public bool Active
    {
        get => (bool)GetValue(ActiveProperty);
        set => SetValue(ActiveProperty, value);
    }

    public double Sensitivity
    {
        get => (double)GetValue(SensitivityProperty);
        set => SetValue(SensitivityProperty, value);
    }

    public Brush AccentBrush
    {
        get => (Brush)GetValue(AccentBrushProperty);
        set => SetValue(AccentBrushProperty, value);
    }

    public Brush BrightBrush
    {
        get => (Brush)GetValue(BrightBrushProperty);
        set => SetValue(BrightBrushProperty, value);
    }

    public int Bands
    {
        get => (int)GetValue(BandsProperty);
        set => SetValue(BandsProperty, value);
    }

    public double InnerFraction
    {
        get => (double)GetValue(InnerFractionProperty);
        set => SetValue(InnerFractionProperty, value);
    }

    public double LengthFraction
    {
        get => (double)GetValue(LengthFractionProperty);
        set => SetValue(LengthFractionProperty, value);
    }

    public Brush? GlowBrush
    {
        get => (Brush?)GetValue(GlowBrushProperty);
        set => SetValue(GlowBrushProperty, value);
    }

    private double InnerRadius(double minSide) =>
        minSide * Math.Clamp(InnerFraction, 0.05, 0.49);

    private double MaxLength(double minSide) =>
        minSide * Math.Clamp(LengthFraction, 0.01, 0.24);

    private static void OnActiveChanged(DependencyObject d, DependencyPropertyChangedEventArgs e) =>
        ((SpectrumVisualizer)d).SyncLoop();

    private void SyncLoop()
    {
        var shouldRun = IsLoaded && IsVisible && Active;
        if (shouldRun == _hooked) return;

        _hooked = shouldRun;
        if (shouldRun)
        {
            _lastFrame = TimeSpan.Zero;
            CompositionTarget.Rendering += OnRendering;
        }
        else
        {
            CompositionTarget.Rendering -= OnRendering;
            Array.Clear(_target);
            for (var i = 0; i < _values.Length; i++) _values[i] = 0;
            for (var i = 0; i < _sparks.Length; i++) _sparks[i].Life = 0;
            InvalidateVisual();
        }
    }

    private void OnRendering(object? sender, EventArgs e)
    {
        if (e is not RenderingEventArgs args) return;
        if (args.RenderingTime == _lastFrame) return;
        if (_lastFrame == TimeSpan.Zero) _lastFrame = args.RenderingTime;

        var dt = (args.RenderingTime - _lastFrame).TotalSeconds;
        _lastFrame = args.RenderingTime;
        if (dt <= 0.0005 || dt > 0.1) dt = 1.0 / 60.0;

        Step((float)dt);
        InvalidateVisual();
    }

    private void Step(float dt)
    {
        var provider = BandsProvider;
        var count = 0;
        if (provider is not null)
        {
            var snapshot = provider();
            count = Math.Min(snapshot.Length, _target.Length);
            for (var i = 0; i < count; i++) _target[i] = snapshot[i];
        }

        var sensitivity = (float)Math.Clamp(Sensitivity, 0.2, 3.0);
        var attack = (float)(1.0 - Math.Exp(-46.0 * dt));
        var release = (float)(1.0 - Math.Exp(-7.5 * dt));
        for (var i = 0; i < _values.Length; i++)
        {
            var tilt = 1.0f + 0.16f * (1f - i / (float)_values.Length);
            var target = i < count
                ? Math.Clamp(_target[i] * sensitivity * tilt, 0f, 1f)
                : 0f;
            var value = _values[i];
            _values[i] = value + (target - value) * (target >= value ? attack : release);
        }

        _bass = 0;
        for (var i = 0; i < 8; i++) _bass += _values[i];
        _bass /= 8;

        var total = 0.0;
        for (var i = 0; i < _values.Length; i++) total += _values[i];
        _level = total / _values.Length;

        _spin = (_spin + (9 + _bass * 72) * dt) % 360;
        UpdateSparks(dt);
    }

    private void UpdateSparks(float dt)
    {
        var minSide = Math.Min(ActualWidth, ActualHeight);
        if (minSide <= 8) return;
        var innerRadius = InnerRadius(minSide);
        var maxRadius = Math.Min(innerRadius + MaxLength(minSide) * 1.25, minSide * 0.485);
        var spawnChance = (0.55 + _level * 4.2) * dt;

        for (var i = 0; i < _sparks.Length; i++)
        {
            var spark = _sparks[i];
            if (spark.Life <= 0)
            {
                if (spawnChance > 0 && _random.NextDouble() < spawnChance)
                {
                    spark.Angle = _random.NextDouble() * Math.PI * 2;
                    spark.Radius = innerRadius * 1.02;
                    spark.Speed = Math.Max(4, (maxRadius - innerRadius) * (0.7 + _random.NextDouble() * 1.2));
                    spark.Drift = (_random.NextDouble() - 0.5) * 2.1;
                    spark.Size = 0.7 + _random.NextDouble() * 2.1;
                    spark.Life = 0.55 + _random.NextDouble() * 1.25;
                }
                continue;
            }

            spark.Life -= dt;
            spark.Radius += spark.Speed * dt;
            spark.Angle += spark.Drift * dt;
            if (spark.Radius > maxRadius) spark.Life = 0;
        }
    }

    protected override void OnRender(DrawingContext dc)
    {
        var width = ActualWidth;
        var height = ActualHeight;
        if (width < 8 || height < 8) return;

        var accent = Resolve(AccentBrush, Color.FromRgb(0xA7, 0x8B, 0xFA));
        var bright = Resolve(BrightBrush, Colors.White);
        var minSide = Math.Min(width, height);
        EnsureCache(accent, bright, minSide);

        var center = new Point(width / 2, height / 2);
        var glowBrush = GlowBrush;
        if (glowBrush is not null)
        {
            var halo = minSide * 0.46;
            dc.DrawEllipse(glowBrush, null, center, halo, halo);
        }

        var innerRadius = InnerRadius(minSide);
        var maxLength = MaxLength(minSide);
        var bands = Math.Clamp(Bands, 8, _values.Length);
        var half = Math.Max(1, bands / 2);
        var perHalf = _values.Length / (double)half;
        var step = Math.PI * 2 / bands;
        var rotation = _spin * Math.PI / 180;

        var glow = 0.18 + _bass * 0.34;
        if (_hazeBrush is not null)
        {
            var haze = innerRadius * (1.02 + glow * 0.05);
            dc.DrawEllipse(_hazeBrush, null, center, haze, haze);
        }

        if (_ringPen is not null)
        {
            var ringRadius = Math.Min(innerRadius + maxLength + 1.2, minSide * 0.478);
            var outer = Math.Min(ringRadius + minSide * 0.012, minSide * 0.492);
            dc.DrawEllipse(null, _ringPen, center, ringRadius, ringRadius);
            for (var tick = 0; tick < 48; tick++)
            {
                var angle = rotation * 0.35 + tick * (Math.PI * 2 / 48);
                var dx = Math.Cos(angle);
                var dy = Math.Sin(angle);
                var innerTick = ringRadius + minSide * (tick % 4 == 0 ? 0.006 : 0.0025);
                dc.DrawLine(_ringPen,
                    new Point(center.X + dx * innerTick, center.Y + dy * innerTick),
                    new Point(center.X + dx * outer, center.Y + dy * outer));
            }
        }

        for (var i = 0; i < bands; i++)
        {
            var source = (int)((i % half) * perHalf);
            var value = _values[Math.Min(source, _values.Length - 1)];
            var length = 2.0 + Math.Pow(value, 0.78) * maxLength;
            var angle = rotation + i * step;
            var dx = Math.Cos(angle);
            var dy = Math.Sin(angle);
            var start = new Point(center.X + dx * innerRadius, center.Y + dy * innerRadius);
            var end = new Point(center.X + dx * (innerRadius + length), center.Y + dy * (innerRadius + length));

            dc.DrawLine(_glowPen!, start, end);
            dc.DrawLine(_bodyPen!, start, end);

            if (value > 0.14)
            {
                var tipStart = new Point(
                    center.X + dx * (innerRadius + length * 0.55),
                    center.Y + dy * (innerRadius + length * 0.55));
                dc.DrawLine(_tipPen!, tipStart, end);
            }
        }

        if (_sparkBrushes is not null)
        {
            for (var i = 0; i < _sparks.Length; i++)
            {
                var spark = _sparks[i];
                if (spark.Life <= 0) continue;
                var point = new Point(
                    center.X + Math.Cos(spark.Angle) * spark.Radius,
                    center.Y + Math.Sin(spark.Angle) * spark.Radius);
                var level = Math.Clamp((int)(spark.Life * SparkAlphaLevels), 0, SparkAlphaLevels - 1);
                dc.DrawEllipse(_sparkBrushes[level], null, point, spark.Size, spark.Size);
            }
        }
    }

    private void EnsureCache(Color accent, Color bright, double minSide)
    {
        if (ReferenceEquals(AccentBrush, _cachedAccent) && ReferenceEquals(BrightBrush, _cachedBright) &&
            Math.Abs(minSide - _cachedSize) < 1 && _glowPen is not null)
            return;

        _cachedAccent = AccentBrush ?? Brushes.MediumPurple;
        _cachedBright = BrightBrush ?? Brushes.White;
        _cachedSize = minSide;

        _glowPen = Pen(Color.FromArgb(34, accent.R, accent.G, accent.B), Math.Max(2.5, minSide * 0.028));
        _bodyPen = Pen(Color.FromArgb(215, accent.R, accent.G, accent.B), Math.Max(1.1, minSide * 0.0080));
        _tipPen = Pen(Color.FromArgb(245, bright.R, bright.G, bright.B), Math.Max(0.8, minSide * 0.0052));
        _ringPen = Pen(Color.FromArgb(40, accent.R, accent.G, accent.B), Math.Max(0.6, minSide * 0.0032));
        _sparkBrushes = new Brush[SparkAlphaLevels];
        for (var i = 0; i < SparkAlphaLevels; i++)
        {
            var alpha = (byte)(225 * (i + 1) / SparkAlphaLevels);
            _sparkBrushes[i] = Frozen(new SolidColorBrush(Color.FromArgb(alpha, bright.R, bright.G, bright.B)));
        }
        _hazeBrush = Frozen(new SolidColorBrush(Color.FromArgb(26, accent.R, accent.G, accent.B)));
    }

    private static Pen Pen(Color color, double thickness)
    {
        var brush = Frozen(new SolidColorBrush(color));
        var pen = new Pen(brush, thickness)
        {
            StartLineCap = PenLineCap.Round,
            EndLineCap = PenLineCap.Round
        };
        pen.Freeze();
        return pen;
    }

    private static SolidColorBrush Frozen(SolidColorBrush brush)
    {
        brush.Freeze();
        return brush;
    }

    private static Color Resolve(Brush brush, Color fallback) => brush switch
    {
        SolidColorBrush solid => solid.Color,
        GradientBrush gradient when gradient.GradientStops.Count > 0 => gradient.GradientStops[0].Color,
        _ => fallback
    };

    private sealed class Spark
    {
        public double Angle;
        public double Radius;
        public double Speed;
        public double Drift;
        public double Size;
        public double Life;
    }
}
