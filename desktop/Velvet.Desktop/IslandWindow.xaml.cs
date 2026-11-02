using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Threading;
using Velvet.Desktop.Models;
using Velvet.Desktop.Services;
using VelvetTrack = Velvet.Desktop.Models.Track;

namespace Velvet.Desktop;

public partial class IslandWindow : Window, INotifyPropertyChanged
{
    private const double ExpandedWidth = 356;
    private const double ExpandedHeight = 82;
    private const double SettingsHeight = 152;
    private const double MinimalWidth = 296;
    private const double MinimalHeight = 30;
    private const double MinScale = 0.6;
    private const double MaxScale = 1.9;

    private readonly PlaybackEngine _engine;
    private readonly AudioSpectrum _spectrum;
    private readonly AppSettings _settings;
    private readonly Action _persist;
    private readonly float[] _bands = new float[AudioSpectrum.BandCount];
    private readonly DispatcherTimer _ticker;
    private readonly DispatcherTimer _saveDebounce;

    private bool _settingsOpen;
    private double _scaleX = 1;
    private double _scaleY = 1;
    private double _opacityValue;
    private bool _vertical;
    private bool _minimal;
    private bool _dragging;
    private double _minimalWidth = MinimalWidth;
    private bool _opacityDragging;
    private double _corner = 34;
    private bool _closingForApp;

    private string _titleText = "Velvet";
    private string _artistText = "Nothing playing yet";
    private string? _coverPath;
    private Brush _glowBrush = Brushes.Transparent;
    private Color _glowColor = Colors.Transparent;
    private double _progressValue;
    private string _playGlyph = "\uE768";
    private string _timeText = "0:00/0:00";
    private bool _isPlaying;

    public IslandWindow(PlaybackEngine engine, AudioSpectrum spectrum, AppSettings settings,
        Action persist)
    {
        InitializeComponent();
        _engine = engine;
        _spectrum = spectrum;
        _settings = settings;
        _persist = persist;

        var legacy = settings.IslandScale <= 0 ? 1 : settings.IslandScale;
        _scaleX = Math.Clamp(settings.IslandScaleX > 0 ? settings.IslandScaleX : legacy, MinScale, MaxScale);
        _scaleY = Math.Clamp(settings.IslandScaleY > 0 ? settings.IslandScaleY : legacy, MinScale, MaxScale);
        _opacityValue = Math.Clamp(settings.IslandOpacity, 0.35, 1);
        _vertical = settings.IslandVertical;
        _minimal = settings.IslandMinimal;
        _minimalWidth = Math.Clamp(
            settings.IslandMinimalWidth > 0 ? settings.IslandMinimalWidth : MinimalWidth, 236, 620);

        IslandScale.ScaleX = _scaleX;
        IslandScale.ScaleY = _scaleY;
        IslandRotation.Angle = _vertical && !_minimal ? 90 : 0;
        Opacity = _opacityValue;
        OpacitySlider.Minimum = 0.35;
        OpacitySlider.Maximum = 1;
        MiniSpectrum.Sensitivity = IslandSensitivity;
        MiniSpectrum.BandsProvider = ReadBands;

        _engine.TrackChanged += OnTrackChanged;
        _engine.StateChanged += OnStateChanged;

        _ticker = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(250) };
        _ticker.Tick += (_, _) => UpdateProgress();
        _ticker.Start();

        _saveDebounce = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(450) };
        _saveDebounce.Tick += (_, _) =>
        {
            _saveDebounce.Stop();
            Persist();
        };

        if (_engine.Current is { } track) ApplyTrack(track);
        ApplyPlayState();
        DataContext = this;
        ApplyLayout();
        PlaceInitial();
        Loaded += (_, _) => ApplyLayout();
        LocationChanged += (_, _) => UpdateSnapMark();
        Closed += OnClosed;
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    public string TitleText { get => _titleText; private set => Set(ref _titleText, value); }
    public string ArtistText { get => _artistText; private set => Set(ref _artistText, value); }
    public string? CoverPath { get => _coverPath; private set => Set(ref _coverPath, value); }
    public Brush GlowBrush { get => _glowBrush; private set => Set(ref _glowBrush, value); }
    public Color GlowColor { get => _glowColor; private set => Set(ref _glowColor, value); }
    public double ProgressValue { get => _progressValue; private set => Set(ref _progressValue, value); }
    public string PlayGlyph { get => _playGlyph; private set => Set(ref _playGlyph, value); }
    public string TimeText { get => _timeText; private set => Set(ref _timeText, value); }
    public bool IsPlaying { get => _isPlaying; private set => Set(ref _isPlaying, value); }
    public double Sensitivity => _settings.VisualizerSensitivity;
    public bool IsVertical => _vertical;

    public double OpacityValue
    {
        get => _opacityValue;
        set
        {
            var clamped = Math.Clamp(value, 0.35, 1);
            if (!Set(ref _opacityValue, clamped)) return;
            Opacity = clamped;
            _settings.IslandOpacity = clamped;
            ScheduleSave();
        }
    }

    public void RefreshSensitivity()
    {
        MiniSpectrum.Sensitivity = IslandSensitivity;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(Sensitivity)));
    }

    private double IslandSensitivity =>
        Math.Clamp(_settings.VisualizerSensitivity * 1.35, 0.4, 3);

    public void CloseForApp()
    {
        _closingForApp = true;
        Close();
    }

    protected override void OnSourceInitialized(EventArgs e)
    {
        base.OnSourceInitialized(e);
        var handle = new WindowInteropHelper(this).Handle;

        // Overlay windows must never take focus; an activation click would otherwise
        // swallow the first mouse-down and make the slider feel unresponsive.
        var exStyle = GetWindowLong(handle, GwlExStyle);
        SetWindowLong(handle, GwlExStyle, exStyle | WsExNoActivate);

        var none = unchecked((int)0xFFFFFFFE);
        DwmSetWindowAttribute(handle, 34, ref none, sizeof(int));
    }

    private const int GwlExStyle = -20;
    private const int WsExNoActivate = 0x08000000;

    [DllImport("user32.dll")]
    private static extern int GetWindowLong(IntPtr hwnd, int index);

    [DllImport("user32.dll")]
    private static extern int SetWindowLong(IntPtr hwnd, int index, int value);

    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);

    private float[] ReadBands()
    {
        _spectrum.CopyBands(_bands);
        return _bands;
    }

    private void OnTrackChanged(VelvetTrack track)
    {
        ApplyTrack(track);
        UpdateProgress();
    }

    private void ApplyTrack(VelvetTrack track)
    {
        TitleText = string.IsNullOrWhiteSpace(track.Title) ? "Unknown track" : track.Title;
        ArtistText = string.IsNullOrWhiteSpace(track.Artist) ? "Unknown artist" : track.Artist;
        CoverPath = track.CoverPath;
        GlowBrush = BuildGlow(unchecked((int)track.ColorArgb));
        GlowColor = Color.FromArgb(0x8C,
            (byte)((track.ColorArgb >> 16) & 0xFF),
            (byte)((track.ColorArgb >> 8) & 0xFF),
            (byte)(track.ColorArgb & 0xFF));
        ApplyPlayState();
    }

    private void OnStateChanged()
    {
        ApplyPlayState();
        UpdateProgress();
    }

    private void ApplyPlayState()
    {
        IsPlaying = _engine.IsPlaying;
        PlayGlyph = _engine.IsPlaying ? "\uE769" : "\uE768";
        MiniSpectrum.Active = _engine.IsPlaying;
    }

    private void UpdateProgress()
    {
        var position = _engine.Position;
        var duration = _engine.Duration;
        TimeText = $"{FormatTime(position)}/{FormatTime(duration)}";

        if (duration.TotalSeconds <= 0)
        {
            ProgressValue = 0;
            return;
        }
        ProgressValue = Math.Clamp(position.TotalSeconds / duration.TotalSeconds, 0, 1);
    }

    private static string FormatTime(TimeSpan value)
    {
        if (value < TimeSpan.Zero) value = TimeSpan.Zero;
        return value.TotalHours >= 1
            ? $"{(int)value.TotalHours}:{value.Minutes:00}:{value.Seconds:00}"
            : $"{value.Minutes}:{value.Seconds:00}";
    }

    private void ApplyLayout()
    {
        var baseWidth = _minimal ? _minimalWidth : ExpandedWidth;
        var baseHeight = _minimal ? MinimalHeight : (_settingsOpen ? SettingsHeight : ExpandedHeight);

        LayoutHost.Width = baseWidth;
        LayoutHost.Height = baseHeight;

        ExpandedContent.Visibility = _minimal ? Visibility.Collapsed : Visibility.Visible;
        MinimalContent.Visibility = _minimal ? Visibility.Visible : Visibility.Collapsed;
        if (_minimal) _settingsOpen = false;
        SettingsPanel.Visibility = !_minimal && _settingsOpen ? Visibility.Visible : Visibility.Collapsed;
        Ornaments.Visibility = _minimal ? Visibility.Collapsed : Visibility.Visible;
        Hairline.Visibility = _minimal ? Visibility.Collapsed : Visibility.Visible;
        Grip.Visibility = _minimal ? Visibility.Collapsed : Visibility.Visible;
        MinimalGrip.Visibility = _minimal ? Visibility.Visible : Visibility.Collapsed;

        Card.Background = (Brush)FindResource(_minimal ? "IslandMinimalFill" : "IslandFill");
        Card.BorderBrush = _minimal
            ? (Brush)FindResource("IslandMinimalBorder")
            : (Brush)FindResource("AccentSoftBrush");

        var scaleX = _minimal ? 1 : _scaleX;
        var scaleY = _minimal ? 1 : _scaleY;
        IslandScale.ScaleX = scaleX;
        IslandScale.ScaleY = scaleY;

        var rotated = !_minimal && _vertical;
        IslandRotation.Angle = rotated ? 90 : 0;

        var visualWidth = baseWidth * scaleX;
        var visualHeight = baseHeight * scaleY;
        var width = rotated ? visualHeight : visualWidth;
        var height = rotated ? visualWidth : visualHeight;

        var hadPosition = !double.IsNaN(Left) && !double.IsNaN(Top);
        var topFlush = hadPosition && Math.Abs(Top - SystemParameters.WorkArea.Top) <= 40;

        Width = width;
        Height = height;

        _corner = _minimal
            ? Math.Min(Width, Height) / 2
            : Math.Min(34, Math.Min(Width, Height) / 2);

        Card.CornerRadius = new CornerRadius(_corner);
        Veil.CornerRadius = new CornerRadius(_corner);
        InnerEdge.CornerRadius = new CornerRadius(Math.Max(0, _corner - 1));
        UpdateClip();

        if (!hadPosition) return;

        // The island hangs from the top edge: growing or shrinking must never
        // recentre it vertically, otherwise it drifts away from the screen top.
        if (topFlush) Top = SystemParameters.WorkArea.Top;
        ClampToScreen();
        UpdateSnapMark();
    }

    private void UpdateSnapMark()
    {
        // The guide is a drag aid, like an editor's alignment line: it is only
        // visible while the pointer is down and the island is in the snap zone.
        if (!_minimal || !_dragging || double.IsNaN(Left) || double.IsNaN(Top) || Width <= 0)
        {
            SnapMark.Visibility = Visibility.Collapsed;
            return;
        }

        var area = SystemParameters.WorkArea;
        var centered = Math.Abs(Left + Width / 2 - (area.Left + area.Width / 2)) <= 30;
        var atTop = Math.Abs(Top - area.Top) <= 26;
        SnapMark.Visibility = centered && atTop ? Visibility.Visible : Visibility.Collapsed;
    }

    private void UpdateClip()
    {
        var width = Math.Max(0, Width);
        var height = Math.Max(0, Height);
        if (width <= 0 || height <= 0) return;
        CardScene.Clip = new RectangleGeometry(new Rect(0, 0, width, height), _corner, _corner);
    }

    private void CardScene_SizeChanged(object sender, SizeChangedEventArgs e) => UpdateClip();

    private void PlaceInitial()
    {
        var area = SystemParameters.WorkArea;
        if (double.IsNaN(_settings.IslandLeft) || double.IsNaN(_settings.IslandTop))
        {
            Left = area.Left + (area.Width - Width) / 2;
            Top = area.Top;
        }
        else
        {
            Left = _settings.IslandLeft;
            Top = _settings.IslandTop;
        }

        if (Math.Abs(Top - area.Top) <= 30) Top = area.Top;
        ClampToScreen();
        UpdateSnapMark();
    }

    private void ClampToScreen()
    {
        var area = SystemParameters.WorkArea;
        Left = Math.Clamp(Left, area.Left, Math.Max(area.Left, area.Right - Width));
        Top = Math.Clamp(Top, area.Top, Math.Max(area.Top, area.Bottom - Height));
    }

    private void Card_MouseEnter(object sender, MouseEventArgs e)
    {
        if (_minimal) return;
        Card.BorderBrush = (Brush)FindResource("AccentBrush");
    }

    private void Card_MouseLeave(object sender, MouseEventArgs e)
    {
        if (_minimal) return;
        if (_settingsOpen) return;
        Card.BorderBrush = (Brush)FindResource("AccentSoftBrush");
    }

    private void Card_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ChangedButton != MouseButton.Left || IsInteractive(e.OriginalSource as DependencyObject))
            return;

        if (e.ClickCount == 2)
        {
            SetMinimal(!_minimal);
            e.Handled = true;
            return;
        }

        _dragging = true;
        try
        {
            DragMove();
        }
        catch (InvalidOperationException)
        {
            _dragging = false;
            UpdateSnapMark();
            return;
        }

        var area = SystemParameters.WorkArea;
        if (Math.Abs(Top - area.Top) <= 26) Top = area.Top;

        var centeredLeft = area.Left + (area.Width - Width) / 2;
        if (Math.Abs(Left - centeredLeft) <= 56) Left = centeredLeft;

        ClampToScreen();
        _dragging = false;
        UpdateSnapMark();

        _settings.IslandLeft = Left;
        _settings.IslandTop = Top;
        ScheduleSave();
    }

    private static bool IsInteractive(DependencyObject? source)
    {
        while (source is not null)
        {
            if (source is ButtonBase or Thumb or Slider or TextBox) return true;
            source = source is Visual or System.Windows.Media.Media3D.Visual3D
                ? VisualTreeHelper.GetParent(source)
                : LogicalTreeHelper.GetParent(source);
        }
        return false;
    }

    private void Grip_DragDelta(object sender, DragDeltaEventArgs e)
    {
        if (_minimal) return;

        var nextX = _scaleX;
        var nextY = _scaleY;
        if (_vertical)
        {
            nextY += e.HorizontalChange / ExpandedHeight;
            nextX += e.VerticalChange / ExpandedWidth;
        }
        else
        {
            nextX += e.HorizontalChange / ExpandedWidth;
            nextY += e.VerticalChange / ExpandedHeight;
        }

        nextX = Math.Clamp(nextX, MinScale, MaxScale);
        nextY = Math.Clamp(nextY, MinScale, MaxScale);
        if (Math.Abs(nextX - _scaleX) < 0.003 && Math.Abs(nextY - _scaleY) < 0.003) return;

        _scaleX = nextX;
        _scaleY = nextY;
        IslandScale.ScaleX = _scaleX;
        IslandScale.ScaleY = _scaleY;
        _settings.IslandScale = (_scaleX + _scaleY) / 2;
        _settings.IslandScaleX = _scaleX;
        _settings.IslandScaleY = _scaleY;
        ApplyLayout();
    }

    private void MinimalGrip_DragDelta(object sender, DragDeltaEventArgs e)
    {
        if (!_minimal) return;

        _dragging = true;
        var oldWidth = Width;
        _minimalWidth = Math.Clamp(_minimalWidth + e.HorizontalChange * 2, 236, 620);
        ApplyLayout();
        Left -= (Width - oldWidth) / 2;
        ClampToScreen();
        UpdateSnapMark();

        _settings.IslandMinimalWidth = _minimalWidth;
        _settings.IslandLeft = Left;
    }

    private void Grip_DragCompleted(object sender, DragCompletedEventArgs e)
    {
        _dragging = false;
        UpdateSnapMark();
        _settings.IslandLeft = Left;
        _settings.IslandTop = Top;
        ScheduleSave();
    }

    private void SetMinimal(bool minimal)
    {
        if (_minimal == minimal) return;
        _minimal = minimal;
        _settings.IslandMinimal = minimal;
        if (minimal) _settingsOpen = false;
        ApplyLayout();
        _settings.IslandLeft = Left;
        _settings.IslandTop = Top;
        ScheduleSave();
    }

    private void Minify_Click(object sender, RoutedEventArgs e) => SetMinimal(true);

    private void Settings_Click(object sender, RoutedEventArgs e)
    {
        _settingsOpen = !_settingsOpen;
        ApplyLayout();
    }

    private void Rotate_Click(object sender, RoutedEventArgs e)
    {
        _vertical = !_vertical;
        _settings.IslandVertical = _vertical;
        ApplyLayout();
        ScheduleSave();
    }

    private void Close_Click(object sender, RoutedEventArgs e)
    {
        _settings.IslandEnabled = false;
        _settings.IslandLeft = Left;
        _settings.IslandTop = Top;
        Persist();
        Close();
    }

    private void Opacity_PreviewMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (sender is not FrameworkElement host || IsButtonSource(e.OriginalSource)) return;
        _opacityDragging = true;
        host.CaptureMouse();
        SetOpacityFrom(e.GetPosition(OpacitySlider).X);
        e.Handled = true;
    }

    private void Opacity_PreviewMouseMove(object sender, MouseEventArgs e)
    {
        if (sender is not FrameworkElement host) return;
        if (e.LeftButton != MouseButtonState.Pressed)
        {
            if (_opacityDragging) EndOpacityDrag(host);
            return;
        }
        if (IsButtonSource(e.OriginalSource)) return;
        if (!_opacityDragging)
        {
            // The press can be swallowed while the island window is inactive;
            // if the user is already dragging over the row, take over here.
            _opacityDragging = true;
            host.CaptureMouse();
        }
        SetOpacityFrom(e.GetPosition(OpacitySlider).X);
        e.Handled = true;
    }

    private void Opacity_PreviewMouseLeftButtonUp(object sender, MouseButtonEventArgs e)
    {
        if (sender is not FrameworkElement host || IsButtonSource(e.OriginalSource)) return;
        SetOpacityFrom(e.GetPosition(OpacitySlider).X);
        EndOpacityDrag(host);
        e.Handled = true;
    }

    private void Opacity_LostMouseCapture(object sender, MouseEventArgs e) => _opacityDragging = false;

    private static bool IsButtonSource(object? source)
    {
        var node = source as DependencyObject;
        while (node is not null)
        {
            if (node is ButtonBase) return true;
            if (node is FrameworkElement { Name: "OpacitySlider" }) return false;
            node = node is Visual or System.Windows.Media.Media3D.Visual3D
                ? VisualTreeHelper.GetParent(node)
                : LogicalTreeHelper.GetParent(node);
        }
        return false;
    }

    private void SetOpacityFrom(double x)
    {
        var width = OpacitySlider.ActualWidth;
        if (width <= 0) return;
        var fraction = Math.Clamp(x / width, 0, 1);
        var value = 0.35 + fraction * 0.65;
        OpacityValue = value;
        OpacitySlider.SetCurrentValue(Slider.ValueProperty, value);
    }

    private void EndOpacityDrag(FrameworkElement host)
    {
        _opacityDragging = false;
        if (host.IsMouseCaptured) host.ReleaseMouseCapture();
    }

    private void Previous_Click(object sender, RoutedEventArgs e) => _engine.Previous();

    private void Next_Click(object sender, RoutedEventArgs e) => _engine.Next();

    private void PlayPause_Click(object sender, RoutedEventArgs e) => _engine.TogglePlayPause();

    private void OnClosed(object? sender, EventArgs e)
    {
        _ticker.Stop();
        _saveDebounce.Stop();
        _engine.TrackChanged -= OnTrackChanged;
        _engine.StateChanged -= OnStateChanged;
        if (_closingForApp) return;
        _settings.IslandEnabled = false;
        Persist();
    }

    private void ScheduleSave()
    {
        if (_closingForApp) return;
        _saveDebounce.Stop();
        _saveDebounce.Start();
    }

    private void Persist() => _persist();

    private static Brush BuildGlow(int argb)
    {
        if (argb == 0) return Brushes.Transparent;
        var color = Color.FromArgb(
            (byte)((argb >> 24) & 0xFF), (byte)((argb >> 16) & 0xFF),
            (byte)((argb >> 8) & 0xFF), (byte)(argb & 0xFF));
        var brush = new RadialGradientBrush
        {
            GradientOrigin = new Point(0.5, 0.5),
            Center = new Point(0.5, 0.5)
        };
        brush.GradientStops.Add(new GradientStop(Color.FromArgb(0x52, color.R, color.G, color.B), 0));
        brush.GradientStops.Add(new GradientStop(Color.FromArgb(0x1B, color.R, color.G, color.B), 0.6));
        brush.GradientStops.Add(new GradientStop(Colors.Transparent, 1));
        brush.Freeze();
        return brush;
    }

    private bool Set<T>(ref T field, T value, [CallerMemberName] string? name = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value)) return false;
        field = value;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
        return true;
    }
}
