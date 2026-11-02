using System.Windows;
using System.Windows.Media;

namespace Velvet.Desktop.Controls;

/// <summary>
/// Beat-reactive disco stage: corner glow, sweeping spotlights, a flowing
/// theme-coloured liquid floor, embers, shockwaves and the occasional pirate
/// ship. Nothing is washed over the whole window; every light has a source.
/// </summary>
public sealed class DiscoOverlay : FrameworkElement
{
    public static readonly DependencyProperty ActiveProperty = DependencyProperty.Register(
        nameof(Active), typeof(bool), typeof(DiscoOverlay),
        new FrameworkPropertyMetadata(false, FrameworkPropertyMetadataOptions.AffectsRender, OnActiveChanged));

    private static readonly Color[] Palette =
    {
        Color.FromRgb(0xB0, 0x26, 0xFF),
        Color.FromRgb(0x00, 0xE5, 0xFF),
        Color.FromRgb(0xFF, 0x2B, 0x8A),
        Color.FromRgb(0xFF, 0xC4, 0x4D),
        Color.FromRgb(0xFF, 0x3B, 0x3B)
    };

    private static readonly Brush ShipLightBrush = Radial(
        Color.FromArgb(0xFF, 0xFF, 0xE3, 0xB0), Color.FromArgb(0x00, 0xFF, 0xC4, 0x4D));

    private const int ParticleCap = 150;
    private const double ShipIntervalMin = 17;
    private const double ShipIntervalMax = 33;
    private const double ShipSailSeconds = 9;

    private readonly RadialGradientBrush[] _corners = new RadialGradientBrush[Palette.Length];
    private readonly LinearGradientBrush[] _spots = new LinearGradientBrush[Palette.Length];
    private readonly RadialGradientBrush[] _pools = new RadialGradientBrush[Palette.Length];
    private readonly LinearGradientBrush[] _liquid = new LinearGradientBrush[Palette.Length];
    private readonly LinearGradientBrush[] _edgeHorizontal = new LinearGradientBrush[Palette.Length];
    private readonly LinearGradientBrush[] _edgeVertical = new LinearGradientBrush[Palette.Length];
    private readonly SolidColorBrush[][] _dust = new SolidColorBrush[Palette.Length][];
    private readonly Pen[] _rings = new Pen[Palette.Length];
    private readonly Pen[] _shipPens = new Pen[Palette.Length];
    private readonly LinearGradientBrush[] _shipHulls = new LinearGradientBrush[Palette.Length];
    private readonly LinearGradientBrush[] _shipSails = new LinearGradientBrush[Palette.Length];
    private readonly RadialGradientBrush[] _shipGlow = new RadialGradientBrush[Palette.Length];

    private readonly List<Particle> _particles = new();
    private readonly List<Shockwave> _waves = new();
    private readonly Random _random = new(19);

    private TimeSpan _lastFrame;
    private bool _hooked;
    private double _time;
    private double _bass;
    private double _bassTarget;
    private double _lastPulse = -10;
    private double _prevBass;
    private double _shake;
    private int _colorIndex;
    private double _colorClock;
    private double _lastBurst = -10;
    private double _nextShip = 9;
    private double _shipStart = -100;
    private double _shipDirection = 1;

    public DiscoOverlay()
    {
        IsHitTestVisible = false;
        BuildBrushes();
        Loaded += (_, _) => SyncLoop();
        Unloaded += (_, _) => SyncLoop();
        IsVisibleChanged += (_, _) => SyncLoop();
    }

    public bool Active
    {
        get => (bool)GetValue(ActiveProperty);
        set => SetValue(ActiveProperty, value);
    }

    /// <summary>Fresh low-band energy for the current frame.</summary>
    public void Pulse(float bass)
    {
        _bassTarget = Math.Clamp(bass, 0f, 1f);
        _lastPulse = _time;
    }

    public void Reset()
    {
        _particles.Clear();
        _waves.Clear();
        _bass = 0;
        _bassTarget = 0;
        _shake = 0;
        _shipStart = -100;
        _nextShip = _time + 12;
        InvalidateVisual();
    }

    private static void OnActiveChanged(DependencyObject d, DependencyPropertyChangedEventArgs e) =>
        ((DiscoOverlay)d).SyncLoop();

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
            Reset();
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

        Step(dt);
        InvalidateVisual();
    }

    private void Step(double dt)
    {
        _time += dt;
        if (_time - _lastPulse > 0.2) _bassTarget = 0;

        var attack = 1 - Math.Exp(-22 * dt);
        var release = 1 - Math.Exp(-7 * dt);
        _bass += (_bassTarget - _bass) * (_bassTarget > _bass ? attack : release);

        var rising = _bass - _prevBass;
        if (rising > 0.13 && _bass > 0.22 && _time - _lastBurst > 0.15)
        {
            _lastBurst = _time;
            Burst(Math.Clamp(_bass, 0.3, 1.0));
            if (_time - _colorClock > 1.1)
            {
                _colorIndex = (_colorIndex + 1) % Palette.Length;
                _colorClock = _time;
            }
        }
        _prevBass = _bass;

        if (_time - _colorClock > 5.5)
        {
            _colorIndex = (_colorIndex + 1) % Palette.Length;
            _colorClock = _time;
        }

        _shake *= Math.Exp(-8 * dt);
        StepParticles(dt);
        StepWaves(dt);

        if (_time >= _nextShip)
        {
            _shipStart = _time;
            _shipDirection = _random.Next(2) == 0 ? 1 : -1;
            _nextShip = _time + ShipIntervalMin +
                _random.NextDouble() * (ShipIntervalMax - ShipIntervalMin);
        }
    }

    private void Burst(double strength)
    {
        _shake = Math.Min(13, 4 + strength * 9);
        var color = _colorIndex;
        var center = new Point(
            ActualWidth * (0.2 + _random.NextDouble() * 0.6),
            ActualHeight * (0.2 + _random.NextDouble() * 0.5));
        _waves.Add(new Shockwave
        {
            X = center.X,
            Y = center.Y,
            Radius = 12,
            Life = 0.9,
            ColorIndex = color
        });
        _waves.Add(new Shockwave
        {
            X = center.X,
            Y = center.Y,
            Radius = 4,
            Life = 0.5,
            ColorIndex = (color + 2) % Palette.Length
        });

        var count = (int)(18 + strength * 34);
        for (var i = 0; i < count && _particles.Count < ParticleCap; i++)
        {
            var angle = _random.NextDouble() * Math.PI * 2;
            var speed = 70 + _random.NextDouble() * 340;
            _particles.Add(new Particle
            {
                X = center.X,
                Y = center.Y,
                VX = Math.Cos(angle) * speed,
                VY = Math.Sin(angle) * speed,
                Size = 1 + _random.NextDouble() * 2.6,
                Life = 0.55 + _random.NextDouble() * 0.9,
                MaxLife = 1.45,
                ColorIndex = color,
                Ambient = false
            });
        }
    }

    private void StepParticles(double dt)
    {
        var ambientRate = 26 + _bass * 60;
        if (_random.NextDouble() < ambientRate * dt && _particles.Count < ParticleCap)
        {
            _particles.Add(new Particle
            {
                X = _random.NextDouble() * Math.Max(1, ActualWidth),
                Y = ActualHeight * (0.82 + _random.NextDouble() * 0.2),
                VX = (_random.NextDouble() - 0.5) * 26,
                VY = -18 - _random.NextDouble() * 42,
                Size = 0.7 + _random.NextDouble() * 1.7,
                Life = 2.4 + _random.NextDouble() * 2.2,
                MaxLife = 4.6,
                ColorIndex = _colorIndex,
                Ambient = true
            });
        }

        for (var i = _particles.Count - 1; i >= 0; i--)
        {
            var particle = _particles[i];
            particle.Life -= dt;
            if (particle.Life <= 0)
            {
                _particles.RemoveAt(i);
                continue;
            }
            particle.X += particle.VX * dt;
            particle.Y += particle.VY * dt;
            if (particle.Ambient)
            {
                particle.VX += Math.Sin(_time * 1.7 + particle.Y * 0.01) * 8 * dt;
                particle.VY -= 5 * dt;
            }
            else
            {
                particle.VX *= Math.Exp(-1.6 * dt);
                particle.VY = particle.VY * Math.Exp(-1.6 * dt) + 140 * dt;
            }
        }
    }

    private void StepWaves(double dt)
    {
        for (var i = _waves.Count - 1; i >= 0; i--)
        {
            var wave = _waves[i];
            wave.Life -= dt;
            wave.Radius += (260 + _bass * 180) * dt;
            if (wave.Life <= 0) _waves.RemoveAt(i);
        }
    }

    protected override void OnRender(DrawingContext dc)
    {
        if (!Active) return;
        var width = ActualWidth;
        var height = ActualHeight;
        if (width < 40 || height < 40) return;

        var minSide = Math.Min(width, height);
        var color = _colorIndex;
        var energy = 0.55 + _bass * 0.45;
        var wobble = _shake;

        // A whole-stage shove on hard hits. The UI content sits above this layer,
        // so the room shakes without ever making the interface unreadable.
        if (wobble > 0.35)
            dc.PushTransform(new TranslateTransform(
                Math.Sin(_time * 57) * wobble * 0.34,
                Math.Cos(_time * 71) * wobble * 0.34));

        DrawCorners(dc, width, height, minSide, color, energy, wobble);
        DrawEdges(dc, width, height, color, energy);
        DrawSpotlights(dc, width, height, color, energy, wobble);
        DrawLiquid(dc, width, height, color);
        DrawShip(dc, width, height, color);
        DrawWaves(dc);
        DrawParticles(dc);

        if (wobble > 0.35) dc.Pop();
    }

    private void DrawCorners(
        DrawingContext dc, double width, double height, double minSide,
        int color, double energy, double wobble)
    {
        var radius = minSide * (0.18 + _bass * 0.05);
        var points = new[]
        {
            new Point(-wobble * 0.6, -wobble * 0.4),
            new Point(width + wobble * 0.6, -wobble * 0.5),
            new Point(width + wobble * 0.4, height + wobble * 0.6),
            new Point(-wobble * 0.5, height + wobble * 0.5)
        };

        for (var index = 0; index < points.Length; index++)
        {
            var brush = _corners[(color + index) % Palette.Length];
            dc.PushOpacity(energy * 0.58);
            dc.DrawEllipse(brush, null, points[index], radius, radius);
            dc.Pop();
        }
    }

    private void DrawEdges(DrawingContext dc, double width, double height, int color, double energy)
    {
        dc.PushOpacity(0.35 + _bass * 0.55);
        var horizontal = _edgeHorizontal[color];
        var vertical = _edgeVertical[(color + 2) % Palette.Length];
        dc.DrawRectangle(horizontal, null, new Rect(0, 0, width, 2.4));
        dc.DrawRectangle(horizontal, null, new Rect(0, height - 2.4, width, 2.4));
        dc.DrawRectangle(vertical, null, new Rect(0, 0, 2.2, height));
        dc.DrawRectangle(vertical, null, new Rect(width - 2.2, 0, 2.2, height));
        dc.Pop();
    }

    private void DrawSpotlights(
        DrawingContext dc, double width, double height, int color,
        double energy, double wobble)
    {
        var sweep = Math.Sin(_time * 0.42) * 0.26;
        DrawSpotlight(dc, width, height, color, energy, wobble,
            new Point(width * 0.04 + wobble, -height * 0.10), 0.50 + sweep, 0.055);
        DrawSpotlight(dc, width, height, (color + 2) % Palette.Length, energy, wobble,
            new Point(width * 0.96 - wobble, -height * 0.10), -0.50 + sweep * 0.8, 0.045);
    }

    private void DrawSpotlight(
        DrawingContext dc, double width, double height, int color,
        double energy, double wobble, Point origin, double angle, double spread)
    {
        var length = height * (0.92 + _bass * 0.12);
        var end = new Point(
            origin.X + Math.Sin(angle) * length,
            origin.Y + Math.Cos(angle) * length);
        var half = height * (spread + _bass * 0.03);
        var wedge = Wedge(origin, end, half);

        dc.PushOpacity(energy * 0.72);
        dc.DrawGeometry(_spots[color], null, wedge);
        dc.Pop();

        var poolRadius = height * (0.12 + _bass * 0.05);
        dc.PushOpacity(energy * 0.72);
        dc.DrawEllipse(_pools[color], null, end, poolRadius, poolRadius * 0.5);
        dc.Pop();

        // A visible lamp fixture on the border, so every beam has a source.
        var sourceRadius = 6 + _bass * 6;
        dc.PushOpacity(0.9);
        dc.DrawEllipse(_pools[color], null, origin, sourceRadius * 2.6, sourceRadius * 2.6);
        dc.DrawEllipse(Brushes.White, null, origin, sourceRadius * 0.34, sourceRadius * 0.34);
        dc.Pop();

        var core = new Pen(_dust[color][5], 1.2);
        dc.PushOpacity(0.45 + _bass * 0.4);
        dc.DrawLine(core, origin, end);
        dc.Pop();
    }

    private void DrawLiquid(DrawingContext dc, double width, double height, int color)
    {
        var baseY = height * 0.87;
        for (var layer = 2; layer >= 0; layer--)
        {
            var path = new StreamGeometry();
            var amplitude = 10 + _bass * 14 + layer * 4;
            using (var ctx = path.Open())
            {
                ctx.BeginFigure(new Point(0, height + 4), true, true);
                for (var x = 0.0; x <= width + 12; x += 12)
                {
                    var y = baseY + layer * 11 +
                        Math.Sin(x * 0.0075 + _time * (1.1 + layer * 0.32) + layer * 1.7) * amplitude +
                        Math.Sin(x * 0.021 - _time * 0.8) * 4;
                    ctx.LineTo(new Point(x, y), true, false);
                }
                ctx.LineTo(new Point(width + 4, height + 4), true, false);
            }
            path.Freeze();

            dc.PushOpacity(0.42 + _bass * 0.25 - layer * 0.07);
            dc.DrawGeometry(_liquid[(color + layer) % Palette.Length], null, path);
            dc.Pop();
        }
    }

    private void DrawShip(DrawingContext dc, double width, double height, int color)
    {
        var age = _time - _shipStart;
        if (age < 0 || age > ShipSailSeconds) return;

        var progress = age / ShipSailSeconds;
        var x = _shipDirection > 0
            ? -width * 0.14 + progress * width * 1.28
            : width * 1.14 - progress * width * 1.28;
        var y = height * 0.845 + Math.Sin(_time * 1.6) * 4;
        var scale = Math.Clamp(height / 1150, 0.52, 0.78);
        var tilt = Math.Sin(_time * 1.3) * 2.2 * _shipDirection;

        dc.PushTransform(new TranslateTransform(x, y));
        dc.PushTransform(new RotateTransform(tilt));
        dc.PushTransform(new ScaleTransform(scale * _shipDirection, scale));

        dc.PushOpacity(0.8);
        dc.DrawEllipse(_shipGlow[color], null, new Point(0, 14), 120, 46);
        dc.Pop();
        dc.DrawEllipse(null, _shipPens[color], new Point(0, 30), 96, 6);

        var hull = new StreamGeometry();
        using (var ctx = hull.Open())
        {
            ctx.BeginFigure(new Point(-94, -6), true, true);
            ctx.LineTo(new Point(-66, 24), true, false);
            ctx.LineTo(new Point(58, 24), true, false);
            ctx.LineTo(new Point(98, -6), true, false);
            ctx.LineTo(new Point(70, -12), true, false);
            ctx.LineTo(new Point(-72, -12), true, false);
        }
        hull.Freeze();
        dc.DrawGeometry(_shipHulls[color], _shipPens[color], hull);

        var deck = new StreamGeometry();
        using (var ctx = deck.Open())
        {
            ctx.BeginFigure(new Point(-78, -12), false, false);
            ctx.LineTo(new Point(76, -12), true, false);
            ctx.LineTo(new Point(88, -2), true, false);
        }
        deck.Freeze();
        dc.DrawGeometry(null, _shipPens[color], deck);

        var mastPen = _shipPens[color];
        dc.DrawLine(mastPen, new Point(-36, -12), new Point(-36, -88));
        dc.DrawLine(mastPen, new Point(6, -12), new Point(6, -98));
        dc.DrawLine(mastPen, new Point(44, -12), new Point(44, -74));

        var main = new StreamGeometry();
        using (var ctx = main.Open())
        {
            ctx.BeginFigure(new Point(-32, -84), true, true);
            ctx.LineTo(new Point(-32, -26), true, false);
            ctx.QuadraticBezierTo(new Point(-70, -40), new Point(-64, -66), true, false);
        }
        main.Freeze();
        dc.DrawGeometry(_shipSails[color], mastPen, main);

        var fore = new StreamGeometry();
        using (var ctx = fore.Open())
        {
            ctx.BeginFigure(new Point(10, -94), true, true);
            ctx.LineTo(new Point(10, -24), true, false);
            ctx.QuadraticBezierTo(new Point(48, -36), new Point(42, -74), true, false);
        }
        fore.Freeze();
        dc.DrawGeometry(_shipSails[color], mastPen, fore);

        var jib = new StreamGeometry();
        using (var ctx = jib.Open())
        {
            ctx.BeginFigure(new Point(48, -70), true, true);
            ctx.LineTo(new Point(48, -22), true, false);
            ctx.QuadraticBezierTo(new Point(76, -32), new Point(66, -56), true, false);
        }
        jib.Freeze();
        dc.DrawGeometry(_shipSails[color], mastPen, jib);

        var flag = new StreamGeometry();
        using (var ctx = flag.Open())
        {
            ctx.BeginFigure(new Point(6, -98), true, true);
            ctx.LineTo(new Point(6, -112), true, false);
            ctx.LineTo(new Point(26, -105), true, false);
        }
        flag.Freeze();
        dc.DrawGeometry(_dust[color][6], null, flag);

        var lantern = new Point(70, -8);
        dc.DrawEllipse(ShipLightBrush, null, lantern, 11, 11);
        dc.DrawEllipse(Brushes.White, null, lantern, 2.2, 2.2);

        dc.Pop();
        dc.Pop();
        dc.Pop();
    }

    private void DrawWaves(DrawingContext dc)
    {
        foreach (var wave in _waves)
        {
            var center = new Point(wave.X, wave.Y);
            var fade = Math.Clamp(wave.Life, 0, 1);
            dc.PushOpacity(fade * 0.5);
            dc.DrawEllipse(_liquid[wave.ColorIndex], null, center, wave.Radius, wave.Radius * 0.62);
            dc.Pop();
            dc.PushOpacity(fade * 0.8);
            dc.DrawEllipse(null, _rings[wave.ColorIndex], center, wave.Radius, wave.Radius * 0.62);
            dc.DrawEllipse(_dust[wave.ColorIndex][7], null, center, 3 + fade * 4, 3 + fade * 4);
            dc.Pop();
        }
    }

    private void DrawParticles(DrawingContext dc)
    {
        foreach (var particle in _particles)
        {
            var ratio = Math.Clamp(particle.Life / particle.MaxLife, 0, 1);
            var bucket = Math.Clamp((int)(ratio * 7), 0, 7);
            var brush = _dust[particle.ColorIndex][bucket];
            dc.PushOpacity(particle.Ambient ? 0.85 : 1.0);
            dc.DrawEllipse(brush, null, new Point(particle.X, particle.Y), particle.Size, particle.Size);
            dc.Pop();
        }
    }

    private void BuildBrushes()
    {
        for (var index = 0; index < Palette.Length; index++)
        {
            var c = Palette[index];

            _corners[index] = Frozen(Radial(
                Color.FromArgb(0x9C, c.R, c.G, c.B),
                Color.FromArgb(0x28, c.R, c.G, c.B),
                Color.FromArgb(0x00, c.R, c.G, c.B)));

            _pools[index] = Frozen(Radial(
                Color.FromArgb(0xB4, c.R, c.G, c.B),
                Color.FromArgb(0x2E, c.R, c.G, c.B),
                Color.FromArgb(0x00, c.R, c.G, c.B)));

            var spot = new LinearGradientBrush
            {
                StartPoint = new Point(0, 0),
                EndPoint = new Point(0, 1)
            };
            spot.GradientStops.Add(new GradientStop(Color.FromArgb(0x7A, c.R, c.G, c.B), 0));
            spot.GradientStops.Add(new GradientStop(Color.FromArgb(0x20, c.R, c.G, c.B), 0.6));
            spot.GradientStops.Add(new GradientStop(Color.FromArgb(0x00, c.R, c.G, c.B), 1));
            _spots[index] = Frozen(spot);

            var liquid = new LinearGradientBrush
            {
                StartPoint = new Point(0, 0),
                EndPoint = new Point(0, 1)
            };
            liquid.GradientStops.Add(new GradientStop(Color.FromArgb(0x58, c.R, c.G, c.B), 0));
            liquid.GradientStops.Add(new GradientStop(Color.FromArgb(0x16, c.R, c.G, c.B), 1));
            _liquid[index] = Frozen(liquid);

            var horizontal = new LinearGradientBrush
            {
                StartPoint = new Point(0, 0),
                EndPoint = new Point(1, 0)
            };
            horizontal.GradientStops.Add(new GradientStop(Color.FromArgb(0x00, c.R, c.G, c.B), 0));
            horizontal.GradientStops.Add(new GradientStop(Color.FromArgb(0x8C, c.R, c.G, c.B), 0.5));
            horizontal.GradientStops.Add(new GradientStop(Color.FromArgb(0x00, c.R, c.G, c.B), 1));
            _edgeHorizontal[index] = Frozen(horizontal);

            var vertical = new LinearGradientBrush
            {
                StartPoint = new Point(0, 0),
                EndPoint = new Point(0, 1)
            };
            vertical.GradientStops.Add(new GradientStop(Color.FromArgb(0x00, c.R, c.G, c.B), 0));
            vertical.GradientStops.Add(new GradientStop(Color.FromArgb(0x8C, c.R, c.G, c.B), 0.5));
            vertical.GradientStops.Add(new GradientStop(Color.FromArgb(0x00, c.R, c.G, c.B), 1));
            _edgeVertical[index] = Frozen(vertical);

            _dust[index] = new SolidColorBrush[8];
            for (var bucket = 0; bucket < _dust[index].Length; bucket++)
            {
                var alpha = (byte)(48 + bucket * 27);
                _dust[index][bucket] = Frozen(new SolidColorBrush(Color.FromArgb(alpha, c.R, c.G, c.B)));
            }

            _rings[index] = new Pen(new SolidColorBrush(Color.FromArgb(0xE0, c.R, c.G, c.B)), 1.8);
            _rings[index].Freeze();
            _shipPens[index] = new Pen(new SolidColorBrush(Color.FromArgb(0xE6, c.R, c.G, c.B)), 1.3);
            _shipPens[index].Freeze();

            var hullFill = new LinearGradientBrush
            {
                StartPoint = new Point(0, 0),
                EndPoint = new Point(0, 1)
            };
            hullFill.GradientStops.Add(new GradientStop(
                Color.FromArgb(0xF4, (byte)(c.R * 0.34), (byte)(c.G * 0.34), (byte)(c.B * 0.34)), 0));
            hullFill.GradientStops.Add(new GradientStop(Color.FromArgb(0xF4, 0x05, 0x03, 0x09), 1));
            _shipHulls[index] = Frozen(hullFill);

            var sailFill = new LinearGradientBrush
            {
                StartPoint = new Point(0, 0),
                EndPoint = new Point(1, 1)
            };
            sailFill.GradientStops.Add(new GradientStop(Color.FromArgb(0xE4, c.R, c.G, c.B), 0));
            sailFill.GradientStops.Add(new GradientStop(
                Color.FromArgb(0x7C, (byte)(c.R * 0.55), (byte)(c.G * 0.55), (byte)(c.B * 0.55)), 1));
            _shipSails[index] = Frozen(sailFill);

            _shipGlow[index] = Frozen(Radial(
                Color.FromArgb(0x66, c.R, c.G, c.B),
                Color.FromArgb(0x1E, c.R, c.G, c.B),
                Color.FromArgb(0x00, c.R, c.G, c.B)));
        }
    }

    private static RadialGradientBrush Radial(Color inner, Color mid, Color outer)
    {
        var brush = new RadialGradientBrush
        {
            Center = new Point(0.5, 0.5),
            GradientOrigin = new Point(0.5, 0.5),
            RadiusX = 0.5,
            RadiusY = 0.5
        };
        brush.GradientStops.Add(new GradientStop(inner, 0));
        brush.GradientStops.Add(new GradientStop(mid, 0.55));
        brush.GradientStops.Add(new GradientStop(outer, 1));
        return brush;
    }

    private static RadialGradientBrush Radial(Color inner, Color outer)
    {
        var brush = new RadialGradientBrush
        {
            Center = new Point(0.5, 0.5),
            GradientOrigin = new Point(0.5, 0.5),
            RadiusX = 0.5,
            RadiusY = 0.5
        };
        brush.GradientStops.Add(new GradientStop(inner, 0));
        brush.GradientStops.Add(new GradientStop(outer, 1));
        return brush;
    }

    private static T Frozen<T>(T brush) where T : Freezable
    {
        brush.Freeze();
        return brush;
    }

    private static Geometry Wedge(Point origin, Point end, double halfWidth)
    {
        var dx = end.X - origin.X;
        var dy = end.Y - origin.Y;
        var length = Math.Sqrt(dx * dx + dy * dy);
        if (length < 1) return Geometry.Empty;
        var px = -dy / length * halfWidth;
        var py = dx / length * halfWidth;

        var geometry = new StreamGeometry();
        using (var ctx = geometry.Open())
        {
            ctx.BeginFigure(origin, true, true);
            ctx.LineTo(new Point(end.X + px, end.Y + py), true, false);
            ctx.LineTo(new Point(end.X - px, end.Y - py), true, false);
        }
        geometry.Freeze();
        return geometry;
    }

    private sealed class Particle
    {
        public double X;
        public double Y;
        public double VX;
        public double VY;
        public double Size;
        public double Life;
        public double MaxLife;
        public int ColorIndex;
        public bool Ambient;
    }

    private sealed class Shockwave
    {
        public double X;
        public double Y;
        public double Radius;
        public double Life;
        public int ColorIndex;
    }
}
