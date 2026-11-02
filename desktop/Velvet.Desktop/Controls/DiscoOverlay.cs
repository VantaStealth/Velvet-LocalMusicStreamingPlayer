using System.Windows;
using System.Windows.Media;

namespace Velvet.Desktop.Controls;

/// <summary>
/// Beat-reactive stage. Every light has a source: soft beams enter from the
/// edges, the theme-coloured liquid rolls with the low end, and fireworks
/// detonate on the kick. Static brushes, pens and ship geometry are built once;
/// the per-frame path only moves particles and rebuilds the liquid surface.
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

    private const int ParticleCap = 420;
    private const int FlashCap = 12;
    private const int TrailLevels = 6;
    private const int DustLevels = 8;
    private const double ShipIntervalMin = 21;
    private const double ShipIntervalMax = 38;
    private const double ShipSailSeconds = 10;

    private readonly RadialGradientBrush[] _corner = new RadialGradientBrush[Palette.Length];
    private readonly RadialGradientBrush[] _beam = new RadialGradientBrush[Palette.Length];
    private readonly RadialGradientBrush[] _bloom = new RadialGradientBrush[Palette.Length];
    private readonly LinearGradientBrush[] _liquid = new LinearGradientBrush[Palette.Length];
    private readonly SolidColorBrush[][] _dust = new SolidColorBrush[Palette.Length][];
    private readonly Pen[][] _trails = new Pen[Palette.Length][];
    private readonly Pen[] _whiteTrails = new Pen[TrailLevels];
    private readonly Pen[] _shipPens = new Pen[Palette.Length];
    private readonly LinearGradientBrush[] _shipHulls = new LinearGradientBrush[Palette.Length];
    private readonly LinearGradientBrush[] _shipSails = new LinearGradientBrush[Palette.Length];
    private readonly RadialGradientBrush[] _shipGlow = new RadialGradientBrush[Palette.Length];

    private readonly Particle[] _particles = new Particle[ParticleCap];
    private readonly Flash[] _flashes = new Flash[FlashCap];
    private int _particleCount;
    private int _flashCount;

    private readonly Geometry _hull;
    private readonly Geometry _deck;
    private readonly Geometry _sailMain;
    private readonly Geometry _sailFore;
    private readonly Geometry _jib;
    private readonly Geometry _flag;

    private readonly Random _random = new(Random.Shared.Next());
    private readonly int _colorOffset;

    private TimeSpan _lastFrame;
    private bool _hooked;
    private double _time;
    private double _bass;
    private double _baseline;
    private double _input;
    private double _lastPulse = -10;
    private double _shake;
    private int _colorIndex;
    private double _colorClock;
    private double _lastBurst = -10;
    private int _burstCount;
    private double _nextShip = 8;
    private double _shipStart = -100;
    private double _shipDirection = 1;

    private double _swellX;
    private double _swellAmp;
    private double _swellSpeed;

    public DiscoOverlay()
    {
        IsHitTestVisible = false;
        ClipToBounds = true;
        _colorOffset = _random.Next(Palette.Length);
        _colorIndex = _colorOffset;
        BuildBrushes();
        _hull = BuildHull();
        _deck = BuildDeck();
        _sailMain = BuildSailMain();
        _sailFore = BuildSailFore();
        _jib = BuildJib();
        _flag = BuildFlag();
        Loaded += (_, _) => SyncLoop();
        Unloaded += (_, _) => SyncLoop();
        IsVisibleChanged += (_, _) => SyncLoop();
    }

    public bool Active
    {
        get => (bool)GetValue(ActiveProperty);
        set => SetValue(ActiveProperty, value);
    }

    /// <summary>
    /// Fresh low-band energy from the current frame. Attack is immediate so the
    /// stage never trails the kick; only the decay is smoothed.
    /// </summary>
    public void Pulse(float bass)
    {
        var value = Math.Clamp(bass, 0f, 1f);
        _input = value;
        _lastPulse = _time;

        if (value > _bass) _bass = value;

        // Onsets are measured against a slow baseline: consecutive frames rarely
        // jump far, so a frame-to-frame delta would almost never fire.
        var onset = value - _baseline;
        var strongRise = onset > 0.095 && value > 0.28;
        var sustained = value > 0.82 && _time - _lastBurst > 0.9;
        if ((strongRise || sustained) && _time - _lastBurst > 0.22)
        {
            _lastBurst = _time;
            var strength = Math.Clamp(0.35 + onset * 2.4 + value * 0.55, 0.35, 1.0);
            Explode(strength);
            _shake = Math.Min(11, 3 + strength * 8);
            _swellAmp = 10 + strength * 22;
            _swellX = ActualWidth * (0.15 + _random.NextDouble() * 0.7);
            _swellSpeed = (40 + strength * 260) * (_random.Next(2) == 0 ? 1 : -1);

            _burstCount++;
            if (_burstCount % 3 == 0)
            {
                _colorIndex = (_colorIndex + 1 + _random.Next(1, 3)) % Palette.Length;
                _colorClock = _time;
            }
        }

    }

    public void Reset()
    {
        _particleCount = 0;
        _flashCount = 0;
        _bass = 0;
        _baseline = 0;
        _input = 0;
        _shake = 0;
        _swellAmp = 0;
        _shipStart = -100;
        _nextShip = _time + 6 + _random.NextDouble() * 12;
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

        // The input is refreshed every frame while music plays; if the pulses
        // stop, let it fall away quickly instead of freezing on the last kick.
        if (_time - _lastPulse > 0.14) _input *= Math.Exp(-24 * dt);
        if (_input > _bass) _bass = _input;
        else _bass += (_input - _bass) * (1 - Math.Exp(-9 * dt));
        _baseline += (_bass - _baseline) * (1 - Math.Exp(-2.6 * dt));

        if (_time - _colorClock > 6.5)
        {
            _colorIndex = (_colorIndex + 1) % Palette.Length;
            _colorClock = _time;
        }

        _shake *= Math.Exp(-9 * dt);
        _swellAmp *= Math.Exp(-2.4 * dt);
        _swellX += _swellSpeed * dt;

        StepParticles(dt);
        StepFlashes(dt);

        if (_time >= _nextShip)
        {
            _shipStart = _time;
            _shipDirection = _random.Next(2) == 0 ? 1 : -1;
            _nextShip = _time + ShipIntervalMin +
                _random.NextDouble() * (ShipIntervalMax - ShipIntervalMin);
        }
    }

    // ---------------------------------------------------------------- bursts

    private void Explode(double strength)
    {
        var width = ActualWidth;
        var height = ActualHeight;
        if (width < 40 || height < 40) return;

        var color = _colorIndex;
        var x = width * (0.12 + _random.NextDouble() * 0.76);
        var y = height * (0.13 + _random.NextDouble() * 0.38);
        var pattern = _random.Next(4);
        var speed = 130 + strength * 300;

        var count = (int)(46 + strength * 66);
        for (var i = 0; i < count && _particleCount < ParticleCap; i++)
        {
            var angle = _random.NextDouble() * Math.PI * 2;
            var ring = pattern == 1 ? (i % 2 == 0 ? 1.0 : 0.58) : 1.0;
            var velocity = speed * ring * (0.55 + _random.NextDouble() * 0.65);
            var sparkColor = pattern == 3 && i % 5 == 0 ? -1 : color;

            AddParticle(new Particle
            {
                X = x,
                Y = y,
                PX = x,
                PY = y,
                VX = Math.Cos(angle) * velocity,
                VY = Math.Sin(angle) * velocity,
                Size = 0.9 + _random.NextDouble() * 1.3,
                Life = 0.65 + _random.NextDouble() * (pattern == 2 ? 1.35 : 0.85),
                MaxLife = pattern == 2 ? 2.0 : 1.5,
                Gravity = pattern == 2 ? 34 : 96,
                Drag = pattern == 2 ? 0.6 : 1.5,
                Color = sparkColor,
                Kind = 0
            });
        }

        // White-hot cores that outrun the coloured shell.
        for (var i = 0; i < 10 && _particleCount < ParticleCap; i++)
        {
            var angle = _random.NextDouble() * Math.PI * 2;
            var velocity = speed * (0.9 + _random.NextDouble() * 0.5);
            AddParticle(new Particle
            {
                X = x,
                Y = y,
                PX = x,
                PY = y,
                VX = Math.Cos(angle) * velocity,
                VY = Math.Sin(angle) * velocity,
                Size = 1.5,
                Life = 0.32 + _random.NextDouble() * 0.22,
                MaxLife = 0.55,
                Gravity = 60,
                Drag = 2.4,
                Color = -1,
                Kind = 0
            });
        }

        AddFlash(new Flash
        {
            X = x,
            Y = y,
            Age = 0,
            Duration = 0.42,
            Size = 150 + strength * 190,
            Color = color
        });

        // A handful of embers fall out of the burst and drift down.
        for (var i = 0; i < 6 && _particleCount < ParticleCap; i++)
        {
            AddParticle(new Particle
            {
                X = x + (_random.NextDouble() - 0.5) * 40,
                Y = y,
                PX = x,
                PY = y,
                VX = (_random.NextDouble() - 0.5) * 46,
                VY = 20 + _random.NextDouble() * 60,
                Size = 0.8 + _random.NextDouble() * 1.2,
                Life = 1.2 + _random.NextDouble() * 1.4,
                MaxLife = 2.6,
                Gravity = 26,
                Drag = 0.7,
                Color = color,
                Kind = 1
            });
        }
    }

    private void AddParticle(Particle particle)
    {
        if (_particleCount >= ParticleCap) return;
        _particles[_particleCount++] = particle;
    }

    private void AddFlash(Flash flash)
    {
        if (_flashCount >= FlashCap) return;
        _flashes[_flashCount++] = flash;
    }

    private void StepParticles(double dt)
    {
        // Ambient embers rise from the liquid; their rate follows the low end.
        var ambientRate = 10 + _bass * 34;
        if (_random.NextDouble() < ambientRate * dt && _particleCount < ParticleCap)
        {
            var width = Math.Max(1, ActualWidth);
            var height = Math.Max(1, ActualHeight);
            AddParticle(new Particle
            {
                X = _random.NextDouble() * width,
                Y = height * (0.84 + _random.NextDouble() * 0.16),
                PX = 0,
                PY = 0,
                VX = (_random.NextDouble() - 0.5) * 24,
                VY = -14 - _random.NextDouble() * 44,
                Size = 0.7 + _random.NextDouble() * 1.5,
                Life = 2.2 + _random.NextDouble() * 2.2,
                MaxLife = 4.4,
                Gravity = -1.5,
                Drag = 0.35,
                Color = (_colorIndex + _random.Next(3)) % Palette.Length,
                Kind = 2
            });
        }

        for (var i = _particleCount - 1; i >= 0; i--)
        {
            var particle = _particles[i];
            particle.Life -= dt;
            if (particle.Life <= 0)
            {
                _particles[i] = _particles[--_particleCount];
                continue;
            }

            particle.PX = particle.X;
            particle.PY = particle.Y;
            var drag = Math.Exp(-particle.Drag * dt);
            particle.VX *= drag;
            particle.VY = particle.VY * drag + particle.Gravity * dt;

            if (particle.Kind == 2)
            {
                particle.VX += Math.Sin(_time * 1.9 + particle.Y * 0.012) * 10 * dt;
            }

            particle.X += particle.VX * dt;
            particle.Y += particle.VY * dt;
            _particles[i] = particle;
        }
    }

    private void StepFlashes(double dt)
    {
        for (var i = _flashCount - 1; i >= 0; i--)
        {
            var flash = _flashes[i];
            flash.Age += dt;
            if (flash.Age >= flash.Duration)
            {
                _flashes[i] = _flashes[--_flashCount];
                continue;
            }
            _flashes[i] = flash;
        }
    }

    // ---------------------------------------------------------------- render

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

        // A whole-stage shove on hard hits. The interface sits above this layer.
        if (wobble > 0.35)
            dc.PushTransform(new TranslateTransform(
                Math.Sin(_time * 57) * wobble * 0.3,
                Math.Cos(_time * 71) * wobble * 0.3));

        DrawCornerHaze(dc, width, height, minSide, color, energy);
        DrawBeams(dc, width, height, color, energy, wobble);
        DrawLiquid(dc, width, height, color);
        DrawShip(dc, width, height, color);
        DrawFlashes(dc, width, height);
        DrawParticles(dc);

        if (wobble > 0.35) dc.Pop();
    }

    private void DrawCornerHaze(
        DrawingContext dc, double width, double height, double minSide,
        int color, double energy)
    {
        var radius = minSide * (0.20 + _bass * 0.055);
        dc.PushOpacity(energy * 0.5);
        for (var index = 0; index < 4; index++)
        {
            var brush = _corner[(color + _colorOffset + index) % Palette.Length];
            var x = (index & 1) == 0 ? 0.0 : width;
            var y = index < 2 ? 0.0 : height;
            var driftX = Math.Sin(_time * 0.21 + index * 1.7) * minSide * 0.018;
            var driftY = Math.Cos(_time * 0.17 + index * 2.3) * minSide * 0.014;
            dc.DrawEllipse(brush, null, new Point(x + driftX, y + driftY), radius, radius);
        }
        dc.Pop();
    }

    private void DrawBeams(
        DrawingContext dc, double width, double height, int color,
        double energy, double wobble)
    {
        var sweep = Math.Sin(_time * 0.34) * 0.22;
        DrawBeam(dc, width, height, color, energy, wobble,
            new Point(-width * 0.02, -height * 0.06), 0.46 + sweep, 0.085);
        DrawBeam(dc, width, height, (color + 2) % Palette.Length, energy, wobble,
            new Point(width * 1.02, -height * 0.06), -0.46 + sweep * 0.8, 0.072);
        DrawBeam(dc, width, height, (color + 1) % Palette.Length, energy * 0.8, wobble,
            new Point(-width * 0.03, height * 1.04), -0.62 + sweep * 0.55, 0.058);
    }

    private void DrawBeam(
        DrawingContext dc, double width, double height, int color,
        double energy, double wobble, Point origin, double angle, double spread)
    {
        var length = height * (1.02 + _bass * 0.1);
        var halfWidth = height * (spread + _bass * 0.022);
        var center = new Point(
            origin.X + Math.Sin(angle) * length * 0.5,
            origin.Y + Math.Cos(angle) * length * 0.5);
        var theta = Math.Atan2(Math.Cos(angle), Math.Sin(angle)) * 180 / Math.PI;

        dc.PushOpacity(energy * (0.55 + _bass * 0.35));
        dc.PushTransform(new RotateTransform(theta, center.X, center.Y));
        dc.DrawEllipse(_beam[color], null, center, length * 0.5, halfWidth);
        dc.Pop();
        dc.Pop();

        // The fixture itself, so the haze always reads as light from a source.
        var lamp = new Point(origin.X + wobble * 0.4, origin.Y + wobble * 0.3);
        var glow = 12 + _bass * 18;
        dc.PushOpacity(0.55 + _bass * 0.35);
        dc.DrawEllipse(_bloom[color], null, lamp, glow, glow);
        dc.DrawEllipse(Brushes.White, null, lamp, 2.2 + _bass * 1.4, 2.2 + _bass * 1.4);
        dc.Pop();
    }

    private void DrawLiquid(DrawingContext dc, double width, double height, int color)
    {
        var baseY = height * 0.875;
        var points = 44;
        var stepX = width / (points - 1);
        var swellSpread = Math.Max(90, width * 0.17);

        for (var layer = 2; layer >= 0; layer--)
        {
            var amplitude = 7 + _bass * 26 + layer * 4;
            var drift = _time * (0.95 + layer * 0.34 + _bass * 0.7) + layer * 1.9;
            var geometry = new StreamGeometry();
            using (var ctx = geometry.Open())
            {
                ctx.BeginFigure(new Point(-2, height + 2), true, true);
                for (var index = 0; index < points; index++)
                {
                    var x = index * stepX;
                    var swell = Math.Exp(-Math.Pow((x - _swellX) / swellSpread, 2)) * _swellAmp;
                    var y = baseY + layer * 10
                        + Math.Sin(x * 0.0068 + drift) * amplitude
                        + Math.Sin(x * 0.0195 - _time * 1.35) * (3 + _bass * 6)
                        + swell;
                    ctx.LineTo(new Point(x, y), true, false);
                }
                ctx.LineTo(new Point(width + 2, height + 2), true, false);
            }
            geometry.Freeze();

            dc.PushOpacity(0.48 + _bass * 0.24 - layer * 0.09);
            dc.DrawGeometry(_liquid[(color + layer) % Palette.Length], null, geometry);
            dc.Pop();
        }
    }

    private void DrawShip(DrawingContext dc, double width, double height, int color)
    {
        var age = _time - _shipStart;
        if (age < 0 || age > ShipSailSeconds) return;

        var progress = age / ShipSailSeconds;
        var x = _shipDirection > 0
            ? -width * 0.12 + progress * width * 1.24
            : width * 1.12 - progress * width * 1.24;
        var y = height * 0.872 + Math.Sin(_time * 1.5) * 3;
        var scale = Math.Clamp(height / 1450, 0.40, 0.62);
        var tilt = Math.Sin(_time * 1.25) * 2.0 * _shipDirection;

        dc.PushTransform(new TranslateTransform(x, y));
        dc.PushTransform(new RotateTransform(tilt));
        dc.PushTransform(new ScaleTransform(scale * _shipDirection, scale));

        dc.PushOpacity(0.7);
        dc.DrawEllipse(_shipGlow[color], null, new Point(0, 16), 110, 40);
        dc.Pop();
        dc.DrawEllipse(null, _shipPens[color], new Point(0, 30), 90, 5.5);

        dc.DrawGeometry(_shipHulls[color], _shipPens[color], _hull);
        dc.DrawGeometry(null, _shipPens[color], _deck);
        dc.DrawLine(_shipPens[color], new Point(-36, -12), new Point(-36, -88));
        dc.DrawLine(_shipPens[color], new Point(6, -12), new Point(6, -98));
        dc.DrawLine(_shipPens[color], new Point(44, -12), new Point(44, -74));
        dc.DrawGeometry(_shipSails[color], _shipPens[color], _sailMain);
        dc.DrawGeometry(_shipSails[color], _shipPens[color], _sailFore);
        dc.DrawGeometry(_shipSails[color], _shipPens[color], _jib);
        dc.DrawGeometry(_dust[color][5], null, _flag);

        var lamp = new Point(70, -8);
        dc.DrawEllipse(_bloom[(color + 3) % Palette.Length], null, lamp, 14, 14);
        dc.DrawEllipse(Brushes.White, null, lamp, 2, 2);

        dc.Pop();
        dc.Pop();
        dc.Pop();
    }

    private void DrawFlashes(DrawingContext dc, double width, double height)
    {
        for (var i = 0; i < _flashCount; i++)
        {
            var flash = _flashes[i];
            var progress = flash.Age / flash.Duration;
            var fade = (1 - progress) * (1 - progress);
            var radius = flash.Size * (0.28 + progress * 0.72);

            dc.PushOpacity(fade * 0.7);
            dc.DrawEllipse(_bloom[flash.Color], null, new Point(flash.X, flash.Y), radius, radius);
            dc.Pop();

            // A soft reflected bloom on the liquid keeps the burst grounded.
            var reflection = radius * 1.4;
            dc.PushOpacity(fade * 0.22);
            dc.DrawEllipse(_bloom[flash.Color], null,
                new Point(flash.X, height * 0.885), reflection, reflection * 0.22);
            dc.Pop();
        }
    }

    private void DrawParticles(DrawingContext dc)
    {
        for (var i = 0; i < _particleCount; i++)
        {
            var particle = _particles[i];
            var ratio = Math.Clamp(particle.Life / particle.MaxLife, 0, 1);
            var level = Math.Clamp((int)(ratio * (TrailLevels - 1) + 0.5), 0, TrailLevels - 1);

            if (particle.Kind == 2)
            {
                var bucket = Math.Clamp((int)(ratio * (DustLevels - 1)), 0, DustLevels - 1);
                dc.DrawEllipse(_dust[particle.Color][bucket], null,
                    new Point(particle.X, particle.Y), particle.Size, particle.Size);
                continue;
            }

            var pen = particle.Color < 0 ? _whiteTrails[level] : _trails[particle.Color][level];
            dc.DrawLine(pen, new Point(particle.PX, particle.PY),
                new Point(particle.X, particle.Y));
        }
    }

    // ---------------------------------------------------------------- assets

    private void BuildBrushes()
    {
        for (var index = 0; index < Palette.Length; index++)
        {
            var c = Palette[index];

            _corner[index] = Frozen(Radial(
                Color.FromArgb(0x70, c.R, c.G, c.B),
                Color.FromArgb(0x1E, c.R, c.G, c.B),
                Color.FromArgb(0x00, c.R, c.G, c.B)));

            var beam = new RadialGradientBrush
            {
                Center = new Point(0.5, 0.5),
                GradientOrigin = new Point(0, 0.5),
                RadiusX = 0.5,
                RadiusY = 0.5
            };
            beam.GradientStops.Add(new GradientStop(Color.FromArgb(0x8C, c.R, c.G, c.B), 0));
            beam.GradientStops.Add(new GradientStop(Color.FromArgb(0x2A, c.R, c.G, c.B), 0.42));
            beam.GradientStops.Add(new GradientStop(Color.FromArgb(0x00, c.R, c.G, c.B), 1));
            _beam[index] = Frozen(beam);

            _bloom[index] = Frozen(Radial(
                Color.FromArgb(0xB8, c.R, c.G, c.B),
                Color.FromArgb(0x2A, c.R, c.G, c.B),
                Color.FromArgb(0x00, c.R, c.G, c.B)));

            var liquid = new LinearGradientBrush
            {
                StartPoint = new Point(0, 0),
                EndPoint = new Point(0, 1)
            };
            liquid.GradientStops.Add(new GradientStop(Color.FromArgb(0x56, c.R, c.G, c.B), 0));
            liquid.GradientStops.Add(new GradientStop(Color.FromArgb(0x14, c.R, c.G, c.B), 1));
            _liquid[index] = Frozen(liquid);

            _dust[index] = new SolidColorBrush[DustLevels];
            for (var bucket = 0; bucket < DustLevels; bucket++)
            {
                var alpha = (byte)(40 + bucket * 27);
                _dust[index][bucket] = Frozen(new SolidColorBrush(Color.FromArgb(alpha, c.R, c.G, c.B)));
            }

            _trails[index] = new Pen[TrailLevels];
            for (var level = 0; level < TrailLevels; level++)
            {
                var alpha = (byte)(34 + level * 40);
                _trails[index][level] = TrailPen(Color.FromArgb(alpha, c.R, c.G, c.B), 0.9 + level * 0.28);
            }

            _shipPens[index] = Pen(Color.FromArgb(0xE6, c.R, c.G, c.B), 1.2);

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

        for (var level = 0; level < TrailLevels; level++)
        {
            var alpha = (byte)(40 + level * 42);
            _whiteTrails[level] = TrailPen(Color.FromArgb(alpha, 255, 250, 255), 0.8 + level * 0.26);
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

    private static T Frozen<T>(T value) where T : Freezable
    {
        value.Freeze();
        return value;
    }

    private static Pen Pen(Color color, double thickness)
    {
        var pen = new Pen(Frozen(new SolidColorBrush(color)), thickness)
        {
            StartLineCap = PenLineCap.Round,
            EndLineCap = PenLineCap.Round
        };
        pen.Freeze();
        return pen;
    }

    private static Pen TrailPen(Color color, double thickness) => Pen(color, thickness);

    private static Geometry BuildHull()
    {
        var geometry = new StreamGeometry();
        using (var ctx = geometry.Open())
        {
            ctx.BeginFigure(new Point(-94, -6), true, true);
            ctx.LineTo(new Point(-66, 24), true, false);
            ctx.LineTo(new Point(58, 24), true, false);
            ctx.LineTo(new Point(98, -6), true, false);
            ctx.LineTo(new Point(70, -12), true, false);
            ctx.LineTo(new Point(-72, -12), true, false);
        }
        geometry.Freeze();
        return geometry;
    }

    private static Geometry BuildDeck()
    {
        var geometry = new StreamGeometry();
        using (var ctx = geometry.Open())
        {
            ctx.BeginFigure(new Point(-78, -12), false, false);
            ctx.LineTo(new Point(76, -12), true, false);
            ctx.LineTo(new Point(88, -2), true, false);
        }
        geometry.Freeze();
        return geometry;
    }

    private static Geometry BuildSailMain()
    {
        var geometry = new StreamGeometry();
        using (var ctx = geometry.Open())
        {
            ctx.BeginFigure(new Point(-32, -84), true, true);
            ctx.LineTo(new Point(-32, -26), true, false);
            ctx.QuadraticBezierTo(new Point(-70, -40), new Point(-64, -66), true, false);
        }
        geometry.Freeze();
        return geometry;
    }

    private static Geometry BuildSailFore()
    {
        var geometry = new StreamGeometry();
        using (var ctx = geometry.Open())
        {
            ctx.BeginFigure(new Point(10, -94), true, true);
            ctx.LineTo(new Point(10, -24), true, false);
            ctx.QuadraticBezierTo(new Point(48, -36), new Point(42, -74), true, false);
        }
        geometry.Freeze();
        return geometry;
    }

    private static Geometry BuildJib()
    {
        var geometry = new StreamGeometry();
        using (var ctx = geometry.Open())
        {
            ctx.BeginFigure(new Point(48, -70), true, true);
            ctx.LineTo(new Point(48, -22), true, false);
            ctx.QuadraticBezierTo(new Point(76, -32), new Point(66, -56), true, false);
        }
        geometry.Freeze();
        return geometry;
    }

    private static Geometry BuildFlag()
    {
        var geometry = new StreamGeometry();
        using (var ctx = geometry.Open())
        {
            ctx.BeginFigure(new Point(6, -98), true, true);
            ctx.LineTo(new Point(6, -112), true, false);
            ctx.LineTo(new Point(26, -105), true, false);
        }
        geometry.Freeze();
        return geometry;
    }

    private struct Particle
    {
        public double X;
        public double Y;
        public double PX;
        public double PY;
        public double VX;
        public double VY;
        public double Size;
        public double Life;
        public double MaxLife;
        public double Gravity;
        public double Drag;
        public int Color;
        public int Kind;
    }

    private struct Flash
    {
        public double X;
        public double Y;
        public double Age;
        public double Duration;
        public double Size;
        public int Color;
    }
}
