using System.Collections.ObjectModel;
using System.ComponentModel;
using System.IO;
using System.Runtime.CompilerServices;
using System.Runtime.InteropServices;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Threading;
using Microsoft.Win32;
using Velvet.Desktop.Models;
using Velvet.Desktop.Services;
using Windows.Media.Control;

namespace Velvet.Desktop;

public partial class MainWindow : Window, INotifyPropertyChanged
{
    private readonly SettingsService _settingsService = new();
    private readonly CoverService _coverService;
    private readonly LibraryService _libraryService;
    private readonly PlaybackEngine _engine = new();
    private readonly AudioSpectrum _spectrum = new();
    private readonly AppSettings _settings;
    private readonly ObservableCollection<Track> _all = new();
    private readonly ObservableCollection<CloudTrack> _cloudItems = new();
    private readonly float[] _spectrumBuffer = new float[AudioSpectrum.BandCount];
    private static readonly int[] LineBandStart = { 0, 8, 16, 26, 36 };
    private static readonly int[] LineBandEnd = { 10, 18, 28, 38, AudioSpectrum.BandCount };
    private ScaleTransform[] _lineScales = Array.Empty<ScaleTransform>();

    private readonly DispatcherTimer _ticker;
    private RotateTransform? _accentRotate;
    private DoubleAnimation? _accentSpin;
    private readonly DispatcherTimer _watchDebounce;
    private readonly DispatcherTimer _toastTimer;
    private readonly DispatcherTimer _settingsDebounce;

    private readonly string? _bundledFolder;

    private IslandWindow? _island;
    private double _visualizerSensitivity = 1.35;
    private double _veinLevel = 0.72;
    private bool _disco;
    private float _discoPhase;
    private int _discoColor;
    private DateTime _lastDiscoColor;
    private static readonly Color[] DiscoColors =
    {
        Color.FromRgb(0xB0, 0x26, 0xFF),
        Color.FromRgb(0x00, 0xE5, 0xFF),
        Color.FromRgb(0xFF, 0x2B, 0x8A),
        Color.FromRgb(0xFF, 0xC4, 0x4D),
        Color.FromRgb(0xFF, 0x3B, 0x3B)
    };
    private string _islandButtonText = "Open floating island";
    private List<(TimeSpan At, string Text)> _lyrics = new();

    private FileSystemWatcher? _watcher;
    private CancellationTokenSource? _scanCts;
    private bool _scanning;
    private bool _scrubbing;
    private DateTime _seekHoldUntil;
    private DateTime _lastUserSeek;
    private bool _closing;

    private string _greeting = "Good evening";
    private string _folderName = "No folder chosen";
    private string _folderSummary = "Choose a music folder";
    private string _searchText = "";
    private string _mixMetaText = "0 tracks · shuffled";
    private string? _heroCoverOne;
    private string? _heroCoverTwo;
    private string? _heroCoverThree;
    private string _trackCountText = "0 tracks";
    private bool _isScanning;
    private string _scanText = "Reading library…";
    private double _scanFraction;
    private string _scanPercent = "0%";
    private bool _showEmptyOverlay = true;
    private string _emptyTitle = "Your listening room awaits";
    private string _emptyText = "Choose the folder where your music lives. Velvet reads your files on this PC and never uploads them.";
    private string _currentTitle = "Nothing playing";
    private string _currentArtist = "Choose a track to begin";
    private string _currentAlbum = "";
    private string? _currentCover;
    private string _currentId = "";
    private Brush _currentGlowBrush = Brushes.Transparent;
    private bool _hasCurrent;
    private string _playPauseGlyph = "\uE768";
    private bool _isPlaying;
    private double _seekValue;
    private string _elapsedText = "0:00";
    private string _totalText = "0:00";
    private bool _shuffleActive;
    private bool _repeatActive;
    private bool _repeatOneActive;
    private double _volumeValue = 0.82;
    private string _volumePercent = "82%";
    private string _sessionStatus = "WINDOWS MEDIA SESSION";
    private string _sessionDetail = "Play a track to publish Velvet to the Island Media Controls.";
    private string _queueSummary = "Queue is empty";
    private string _accentKey = "Amethyst";
    private bool _cloudOverlayOpen;
    private bool _cloudUpload;
    private bool _cloudBusy;
    private string _cloudTitle = "";
    private string _cloudPathText = "";
    private string _cloudSummary = "";
    private string _cloudActionText = "Sync";

    public MainWindow()
    {
        InitializeComponent();
        FitToWorkArea();

        _settings = _settingsService.Load();
        _bundledFolder = ResolveBundledFolder();
        if (string.IsNullOrWhiteSpace(_settings.Folder) && _bundledFolder is not null)
        {
            _settings.Folder = _bundledFolder;
            _settingsService.Save(_settings);
        }

        _coverService = new CoverService(_settingsService.CoversDirectory);
        _libraryService = new LibraryService(_coverService);

        _volumeValue = Math.Clamp(_settings.Volume, 0, 1);
        _volumePercent = $"{_volumeValue * 100:0}%";
        _engine.Volume = _volumeValue;
        _engine.SetShuffle(_settings.Shuffle);
        _engine.SetRepeat(_settings.Repeat);
        _shuffleActive = _settings.Shuffle;
        _repeatActive = _settings.Repeat != RepeatMode.Off;
        _repeatOneActive = _settings.Repeat == RepeatMode.One;
        _greeting = BuildGreeting();
        _visualizerSensitivity = Math.Clamp(_settings.VisualizerSensitivity, 0.4, 3);
        _disco = _settings.Disco;
        Spectrum.BandsProvider = ReadSpectrum;

        _engine.TrackChanged += OnTrackChanged;
        _engine.StateChanged += OnEngineStateChanged;
        _engine.QueueChanged += UpdateUpcoming;
        _engine.PlaybackFailed += message => ShowToast("Playback issue", message);

        _ticker = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(200) };
        _ticker.Tick += Ticker_Tick;

        _watchDebounce = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(2500) };
        _watchDebounce.Tick += WatchDebounce_Tick;

        _toastTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(3.4) };
        _toastTimer.Tick += (_, _) =>
        {
            _toastTimer.Stop();
            var fade = new DoubleAnimation(1, 0, TimeSpan.FromMilliseconds(220));
            fade.Completed += (_, _) => ToastBorder.Visibility = Visibility.Collapsed;
            ToastBorder.BeginAnimation(OpacityProperty, fade);
        };

        _settingsDebounce = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(600) };
        _settingsDebounce.Tick += (_, _) =>
        {
            _settingsDebounce.Stop();
            PersistSettings();
        };

        DataContext = this;
        Spectrum.BassUpdated += OnBassUpdated;
        Loaded += OnLoaded;
        Closing += OnClosing;
        StateChanged += (_, _) =>
        {
            UpdateMaximizeIcon();
            SyncActivity();
        };
        IsVisibleChanged += (_, _) => SyncActivity();
        Activated += (_, _) =>
        {
            if (IsVisible && WindowState != WindowState.Minimized) StartAccentSpin();
        };
        Deactivated += (_, _) => StopAccentSpin();
    }

    private void SyncActivity()
    {
        if (_closing) return;
        if (!_engine.IsPlaying) ResetVeinBreath();

        var active = IsVisible && WindowState != WindowState.Minimized;
        if (!active)
        {
            _ticker.Stop();
            StopAccentSpin();
            UpdateSpectrumDemand();
            ApplyDiscoState();
            return;
        }

        _ticker.Interval = TimeSpan.FromMilliseconds(_engine.IsPlaying ? 200 : 700);
        _ticker.Start();
        if (IsActive) StartAccentSpin();
        UpdateSpectrumDemand();
        ApplyDiscoState();
    }

    private void UpdateSpectrumDemand()
    {
        var needed = _engine.IsPlaying && (IsVisible || _island is not null);
        if (needed)
        {
            _spectrum.Start();
        }
        else
        {
            _spectrum.Stop();
        }
    }

    private void StartAccentSpin()
    {
        if (_accentRotate is null || _accentSpin is null || _closing) return;
        _accentRotate.BeginAnimation(RotateTransform.AngleProperty, _accentSpin);
    }

    private void StopAccentSpin()
    {
        _accentRotate?.BeginAnimation(RotateTransform.AngleProperty, null);
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    private static string? ResolveBundledFolder()
    {
        var local = Path.Combine(AppContext.BaseDirectory, "music");
        if (HasAudio(local)) return local;

        var directory = new DirectoryInfo(AppContext.BaseDirectory);
        for (var depth = 0; depth < 8 && directory is not null; depth++, directory = directory.Parent)
        {
            var candidate = Path.Combine(directory.FullName, "music");
            if (HasAudio(candidate)) return candidate;
        }

        var userMusic = Environment.GetFolderPath(Environment.SpecialFolder.MyMusic);
        return HasAudio(userMusic) ? userMusic : null;
    }

    private static bool HasAudio(string? folder) =>
        !string.IsNullOrWhiteSpace(folder) && Directory.Exists(folder) &&
        Directory.EnumerateFiles(folder, "*.mp3", SearchOption.AllDirectories).Any();

    protected override void OnSourceInitialized(EventArgs e)
    {
        base.OnSourceInitialized(e);
        var handle = new WindowInteropHelper(this).Handle;
        var round = 2;
        DwmSetWindowAttribute(handle, 33, ref round, sizeof(int));
        var none = unchecked((int)0xFFFFFFFE);
        DwmSetWindowAttribute(handle, 34, ref none, sizeof(int));
        HwndSource.FromHwnd(handle)?.AddHook(WindowProc);
    }

    [DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);

    [DllImport("user32.dll")]
    private static extern IntPtr MonitorFromWindow(IntPtr hwnd, uint flags);

    [DllImport("user32.dll")]
    private static extern bool GetMonitorInfo(IntPtr monitor, ref MonitorInfo info);

    [StructLayout(LayoutKind.Sequential)]
    private struct NativePoint
    {
        public int X;
        public int Y;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct NativeRect
    {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MonitorInfo
    {
        public int Size;
        public NativeRect Monitor;
        public NativeRect Work;
        public uint Flags;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MinMaxInfo
    {
        public NativePoint Reserved;
        public NativePoint MaxSize;
        public NativePoint MaxPosition;
        public NativePoint MinTrackSize;
        public NativePoint MaxTrackSize;
    }

    private IntPtr WindowProc(IntPtr hwnd, int message, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        const int WmGetMinMaxInfo = 0x0024;
        if (message != WmGetMinMaxInfo) return IntPtr.Zero;

        var monitor = MonitorFromWindow(hwnd, 2);
        if (monitor == IntPtr.Zero) return IntPtr.Zero;

        var info = new MonitorInfo { Size = Marshal.SizeOf<MonitorInfo>() };
        if (!GetMonitorInfo(monitor, ref info)) return IntPtr.Zero;

        var bounds = Marshal.PtrToStructure<MinMaxInfo>(lParam);
        var work = info.Work;
        bounds.MaxPosition = new NativePoint { X = work.Left, Y = work.Top };
        bounds.MaxSize = new NativePoint { X = work.Right - work.Left, Y = work.Bottom - work.Top };
        bounds.MaxTrackSize = bounds.MaxSize;
        Marshal.StructureToPtr(bounds, lParam, true);
        handled = true;
        return IntPtr.Zero;
    }

    private void FitToWorkArea()
    {
        var area = SystemParameters.WorkArea;
        if (area.Width < MinWidth) MinWidth = area.Width;
        if (area.Height < MinHeight) MinHeight = area.Height;

        Width = Math.Min(Width, area.Width);
        Height = Math.Min(Height, area.Height);
        Left = area.Left + Math.Max(0, (area.Width - Width) / 2);
        Top = area.Top + Math.Max(0, (area.Height - Height) / 2);
    }

    public ObservableCollection<Track> Tracks { get; } = new();
    public ObservableCollection<AlbumGroup> Albums { get; } = new();
    public ObservableCollection<Track> Upcoming { get; } = new();

    public string Greeting { get => _greeting; private set => Set(ref _greeting, value); }
    public string FolderName { get => _folderName; private set => Set(ref _folderName, value); }
    public string FolderSummary { get => _folderSummary; private set => Set(ref _folderSummary, value); }
    public string MixMetaText { get => _mixMetaText; private set => Set(ref _mixMetaText, value); }

    public double VisualizerSensitivity
    {
        get => _visualizerSensitivity;
        set
        {
            var clamped = Math.Clamp(value, 0.4, 3);
            if (!Set(ref _visualizerSensitivity, clamped)) return;
            _settings.VisualizerSensitivity = clamped;
            OnPropertyChanged(nameof(VisualizerSensitivityLabel));
            _island?.RefreshSensitivity();
            ScheduleSettingsSave();
        }
    }

    public string VisualizerSensitivityLabel => $"{_visualizerSensitivity:0.0}x";
    public string DiscoButtonText => _disco ? "Disco mode · On" : "Disco mode · Off";
    public string IslandButtonText { get => _islandButtonText; private set => Set(ref _islandButtonText, value); }

    public string SearchText
    {
        get => _searchText;
        set
        {
            if (!Set(ref _searchText, value ?? "")) return;
            ApplyFilter();
        }
    }

    public string? HeroCoverOne { get => _heroCoverOne; private set => Set(ref _heroCoverOne, value); }
    public string? HeroCoverTwo { get => _heroCoverTwo; private set => Set(ref _heroCoverTwo, value); }
    public string? HeroCoverThree { get => _heroCoverThree; private set => Set(ref _heroCoverThree, value); }
    public string TrackCountText { get => _trackCountText; private set => Set(ref _trackCountText, value); }
    public bool IsScanning { get => _isScanning; private set => Set(ref _isScanning, value); }
    public string ScanText { get => _scanText; private set => Set(ref _scanText, value); }
    public double ScanFraction { get => _scanFraction; private set => Set(ref _scanFraction, value); }
    public string ScanPercent { get => _scanPercent; private set => Set(ref _scanPercent, value); }
    public bool ShowEmptyOverlay { get => _showEmptyOverlay; private set => Set(ref _showEmptyOverlay, value); }
    public string EmptyTitle { get => _emptyTitle; private set => Set(ref _emptyTitle, value); }
    public string EmptyText { get => _emptyText; private set => Set(ref _emptyText, value); }

    public string CurrentTitle { get => _currentTitle; private set => Set(ref _currentTitle, value); }
    public string CurrentArtist { get => _currentArtist; private set => Set(ref _currentArtist, value); }
    public string CurrentAlbum { get => _currentAlbum; private set => Set(ref _currentAlbum, value); }
    public string? CurrentCover { get => _currentCover; private set => Set(ref _currentCover, value); }
    public string CurrentId { get => _currentId; private set => Set(ref _currentId, value); }
    public Brush CurrentGlowBrush { get => _currentGlowBrush; private set => Set(ref _currentGlowBrush, value); }
    public bool HasCurrent { get => _hasCurrent; private set => Set(ref _hasCurrent, value); }
    public string PlayPauseGlyph { get => _playPauseGlyph; private set => Set(ref _playPauseGlyph, value); }
    public bool IsPlaying { get => _isPlaying; private set => Set(ref _isPlaying, value); }
    public double SeekValue { get => _seekValue; private set => Set(ref _seekValue, value); }
    public string ElapsedText { get => _elapsedText; private set => Set(ref _elapsedText, value); }
    public string TotalText { get => _totalText; private set => Set(ref _totalText, value); }
    public bool ShuffleActive { get => _shuffleActive; private set => Set(ref _shuffleActive, value); }
    public bool RepeatActive { get => _repeatActive; private set => Set(ref _repeatActive, value); }
    public bool RepeatOneActive { get => _repeatOneActive; private set => Set(ref _repeatOneActive, value); }

    public double VolumeValue
    {
        get => _volumeValue;
        set
        {
            var clamped = Math.Clamp(value, 0, 1);
            if (!Set(ref _volumeValue, clamped)) return;
            _engine.Volume = clamped;
            VolumePercent = $"{clamped * 100:0}%";
        }
    }

    public string VolumePercent { get => _volumePercent; private set => Set(ref _volumePercent, value); }
    public string SessionStatus { get => _sessionStatus; private set => Set(ref _sessionStatus, value); }
    public string SessionDetail { get => _sessionDetail; private set => Set(ref _sessionDetail, value); }
    public string QueueSummary { get => _queueSummary; private set => Set(ref _queueSummary, value); }
    public string AccentKey { get => _accentKey; private set => Set(ref _accentKey, value); }

    public ObservableCollection<CloudTrack> CloudItems => _cloudItems;
    public bool IsCloudOverlayOpen { get => _cloudOverlayOpen; private set => Set(ref _cloudOverlayOpen, value); }
    public string CloudTitle { get => _cloudTitle; private set => Set(ref _cloudTitle, value); }
    public string CloudPathText { get => _cloudPathText; private set => Set(ref _cloudPathText, value); }
    public string CloudSummary { get => _cloudSummary; private set => Set(ref _cloudSummary, value); }
    public string CloudActionText { get => _cloudActionText; private set => Set(ref _cloudActionText, value); }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        App.ApplyAccent(Accents.For(_settings.Accent));
        AccentKey = Accents.For(_settings.Accent).Key;

        var cached = await _settingsService.LoadLibraryAsync();
        foreach (var track in cached) _all.Add(track);
        ApplyFilter();
        UpdateFolderInfo();
        RefreshHeroCovers();
        UpdateEmptyState();

        if (!string.IsNullOrWhiteSpace(_settings.Folder) && Directory.Exists(_settings.Folder))
        {
            StartWatcher(_settings.Folder);
            RestoreLastTrack();
            _ = ScanAsync(silent: true);
        }
        else if (!string.IsNullOrWhiteSpace(_settings.Folder))
        {
            ShowToast("Folder missing", "Velvet could not find the saved folder. Choose it again.");
        }

        InitLiveAccents();
        InitSpectrumLines();
        RecommendCloudSync();
        SyncActivity();

        if (StartHidden)
        {
            ContentRoot.Opacity = 1;
            ContentShift.Y = 0;
            return;
        }

        AnimateIn();
        ContentRoot.Focus();
        if (_settings.IslandEnabled) ShowIsland();
    }

    public bool StartHidden { get; init; }

    private float[] ReadSpectrum()
    {
        _spectrum.CopyBands(_spectrumBuffer);
        return _spectrumBuffer;
    }

    private void InitLiveAccents()
    {
        var brush = new LinearGradientBrush { StartPoint = new Point(0, 0), EndPoint = new Point(1, 1) };
        brush.GradientStops.Add(new GradientStop(Color.FromRgb(0xA7, 0x8B, 0xFA), 0.00));
        brush.GradientStops.Add(new GradientStop(Color.FromRgb(0xF4, 0x72, 0xB6), 0.20));
        brush.GradientStops.Add(new GradientStop(Color.FromRgb(0x22, 0xD3, 0xEE), 0.40));
        brush.GradientStops.Add(new GradientStop(Color.FromRgb(0xA3, 0xE6, 0x35), 0.60));
        brush.GradientStops.Add(new GradientStop(Color.FromRgb(0xFB, 0xBF, 0x24), 0.80));
        brush.GradientStops.Add(new GradientStop(Color.FromRgb(0xA7, 0x8B, 0xFA), 1.00));

        var rotate = new RotateTransform { CenterX = 0.5, CenterY = 0.5 };
        brush.RelativeTransform = rotate;
        SearchShell.BorderBrush = brush;

        _accentRotate = rotate;
        _accentSpin = new DoubleAnimation(0, 360, TimeSpan.FromSeconds(7))
        {
            RepeatBehavior = RepeatBehavior.Forever
        };
        if (IsActive) StartAccentSpin();
    }

    private void InitSpectrumLines()
    {
        _lineScales = new[]
        {
            (ScaleTransform)Line1.RenderTransform,
            (ScaleTransform)Line2.RenderTransform,
            (ScaleTransform)Line3.RenderTransform,
            (ScaleTransform)Line4.RenderTransform,
            (ScaleTransform)Line5.RenderTransform
        };
    }

    private void UpdateSpectrumLines()
    {
        if (_lineScales.Length == 0) return;
        var bands = ReadSpectrum();
        var playing = _engine.IsPlaying;
        var seconds = DateTime.UtcNow.TimeOfDay.TotalSeconds;

        for (var index = 0; index < _lineScales.Length; index++)
        {
            double target;
            if (playing)
            {
                var peak = 0f;
                for (var band = LineBandStart[index]; band < LineBandEnd[index]; band++)
                    if (bands[band] > peak) peak = bands[band];
                target = Math.Clamp(0.2 + peak * 0.88, 0.2, 1.0);
            }
            else
            {
                target = 0.72 + Math.Sin(seconds * 0.85 + index * 0.8) * 0.04;
            }

            var scale = _lineScales[index];
            scale.BeginAnimation(ScaleTransform.ScaleXProperty,
                new DoubleAnimation(scale.ScaleX, target, TimeSpan.FromMilliseconds(180))
                {
                    EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut }
                });
        }
    }

    private void ToggleIsland_Click(object sender, RoutedEventArgs e) => ToggleIsland();

    private void ToggleDisco_Click(object sender, RoutedEventArgs e)
    {
        _disco = !_disco;
        _settings.Disco = _disco;
        OnPropertyChanged(nameof(DiscoButtonText));
        ApplyDiscoState();
        ScheduleSettingsSave();
    }

    private void ApplyDiscoState()
    {
        var on = _disco && _engine.IsPlaying && IsVisible && WindowState != WindowState.Minimized;
        DiscoStage.Opacity = on ? 1 : 0;
        if (on)
        {
            UpdateLyricLine();
            return;
        }
        DiscoFlash.Opacity = 0;
        DiscoA.Opacity = 0;
        DiscoB.Opacity = 0;
        DiscoC.Opacity = 0;
        DiscoD.Opacity = 0;
    }

    private void ToggleIsland()
    {
        if (_island is not null)
        {
            _island.Close();
            return;
        }
        ShowIsland();
    }

    private void ShowIsland()
    {
        if (_island is not null) return;
        _settings.IslandEnabled = true;
        PersistSettings();

        var island = new IslandWindow(_engine, _spectrum, _settings, PersistSettings);
        island.Closed += (_, _) =>
        {
            if (!ReferenceEquals(_island, island)) return;
            _island = null;
            IslandButtonText = "Open floating island";
            UpdateSpectrumDemand();
        };
        _island = island;
        IslandButtonText = "Hide floating island";
        island.Show();
        UpdateSpectrumDemand();
    }

    private void ScheduleSettingsSave()
    {
        _settingsDebounce.Stop();
        _settingsDebounce.Start();
    }

    private void AnimateIn()
    {
        var fade = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(420))
        {
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
        };
        ContentRoot.BeginAnimation(OpacityProperty, fade);

        var slide = new DoubleAnimation(14, 0, TimeSpan.FromMilliseconds(520))
        {
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
        };
        ContentShift.BeginAnimation(TranslateTransform.YProperty, slide);
    }

    private void ApplyFilter()
    {
        var query = (_searchText ?? "").Trim();
        IEnumerable<Track> filtered = _all;
        if (query.Length > 0)
        {
            filtered = _all.Where(track =>
                track.Title.Contains(query, StringComparison.OrdinalIgnoreCase)
                || track.Artist.Contains(query, StringComparison.OrdinalIgnoreCase)
                || track.Album.Contains(query, StringComparison.OrdinalIgnoreCase));
        }

        Tracks.Clear();
        foreach (var track in filtered) Tracks.Add(track);

        Albums.Clear();
        foreach (var album in AlbumGroup.Build(Tracks)) Albums.Add(album);

        TrackCountText = Tracks.Count == 1 ? "1 track" : $"{Tracks.Count} tracks";
        UpdateEmptyState();
    }

    private void UpdateFolderInfo()
    {
        var folder = _settings.Folder;
        if (string.IsNullOrWhiteSpace(folder))
        {
            FolderName = "No folder chosen";
            FolderSummary = "Choose a music folder";
            return;
        }

        var name = string.Equals(
            Path.TrimEndingDirectorySeparator(folder),
            Path.TrimEndingDirectorySeparator(_bundledFolder ?? ""),
            StringComparison.OrdinalIgnoreCase)
            ? "Velvet library"
            : new DirectoryInfo(folder).Name;
        FolderName = string.IsNullOrWhiteSpace(name) ? folder : name;
        var albumCount = AlbumGroup.Build(_all).Count;
        FolderSummary = _all.Count == 0
            ? "Empty library"
            : $"{_all.Count} {(_all.Count == 1 ? "track" : "tracks")} · {albumCount} {(albumCount == 1 ? "album" : "albums")}";
    }

    private void RefreshHeroCovers()
    {
        var covers = _all
            .Where(track => !string.IsNullOrWhiteSpace(track.CoverPath) && File.Exists(track.CoverPath))
            .Select(track => track.CoverPath!)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .Take(3)
            .ToList();

        string? Pick(int index) => covers.Count == 0 ? null : covers[Math.Min(index, covers.Count - 1)];

        HeroCoverOne = Pick(0);
        HeroCoverTwo = Pick(1);
        HeroCoverThree = Pick(2);
        MixMetaText = Tracks.Count == 1 ? "1 track · shuffled" : $"{Tracks.Count} tracks · shuffled";
    }

    private void UpdateEmptyState()
    {
        var hasFolder = !string.IsNullOrWhiteSpace(_settings.Folder);
        ShowEmptyOverlay = !_scanning && Tracks.Count == 0;
        EmptyTitle = hasFolder ? "No audio found here" : "Your listening room awaits";
        EmptyText = hasFolder
            ? $"Velvet searched “{FolderName}” and found no supported audio files. Add music or choose another folder."
            : "Choose the folder where your music lives. Velvet reads your files on this PC and never uploads them.";
    }

    private async Task ScanAsync(bool silent)
    {
        var folder = _settings.Folder;
        if (_scanning || string.IsNullOrWhiteSpace(folder)) return;
        if (!Directory.Exists(folder))
        {
            ShowToast("Folder missing", "That folder can't be opened any more.");
            UpdateEmptyState();
            return;
        }

        _scanning = true;
        IsScanning = true;
        ScanFraction = 0;
        ScanPercent = "0%";
        ScanText = silent ? "Syncing library…" : "Reading library…";
        UpdateEmptyState();

        _scanCts?.Dispose();
        _scanCts = new CancellationTokenSource();
        var token = _scanCts.Token;
        var snapshot = _all.ToList();

        var progress = new Progress<(int Done, int Total, string Name)>(report =>
        {
            if (report.Total <= 0) return;
            ScanFraction = (double)report.Done / report.Total;
            ScanPercent = $"{ScanFraction * 100:0}%";
            ScanText = $"Reading {report.Name} · {report.Done}/{report.Total}";
        });

        try
        {
            var scanned = await _libraryService.ScanAsync(folder, snapshot, progress, token);
            token.ThrowIfCancellationRequested();

            _all.Clear();
            foreach (var track in scanned) _all.Add(track);

            ApplyFilter();
            RefreshHeroCovers();
            UpdateFolderInfo();
            await _settingsService.SaveLibraryAsync(_all);

            if (!_engine.HasTrack && _all.Count > 0) RestoreLastTrack();
            if (_engine.Current is { } current)
                SessionDetail = $"{current.Title} — {current.Artist}";
            else
                SessionDetail = _all.Count > 0
                    ? $"Library synced · {_all.Count} tracks ready"
                    : "No supported audio files in this folder.";
        }
        catch (OperationCanceledException)
        {
            // A new folder or window close superseded this scan.
        }
        catch (Exception exception)
        {
            ShowToast("Scan failed", exception.Message);
        }
        finally
        {
            if (!_closing)
            {
                _scanning = false;
                IsScanning = false;
                ScanFraction = 0;
                ScanPercent = "0%";
                UpdateEmptyState();
            }
        }
    }

    private void RestoreLastTrack()
    {
        if (_engine.HasTrack || _all.Count == 0) return;
        var index = 0;
        if (!string.IsNullOrWhiteSpace(_settings.LastTrackId))
        {
            var found = _all.ToList().FindIndex(track => track.Id == _settings.LastTrackId);
            if (found >= 0) index = found;
        }
        _engine.PlayQueue(_all, index, _settings.Shuffle, autoplay: false);
    }

    private void OnTrackChanged(Track track)
    {
        CurrentId = track.Id;
        CurrentTitle = track.Title;
        CurrentArtist = track.Artist;
        CurrentAlbum = string.IsNullOrWhiteSpace(track.Album) ? "" : track.Album.ToUpperInvariant();
        CurrentCover = track.CoverPath;
        CurrentGlowBrush = BuildGlow(track.ColorArgb);
        HasCurrent = true;
        SessionStatus = _engine.IsPlaying ? "ISLAND MEDIA CONTROLS · LIVE" : "ISLAND MEDIA CONTROLS · READY";
        SessionDetail = $"{track.Title} — {track.Artist}";
        UpdateUpcoming();
        UpdateShuffleState();
        UpdateRepeatState();
        UpdateSeekFromEngine();
        LoadLyrics(track.Path);
        PersistSettings();
        _ = RefreshSessionAsync();
    }

    private void LoadLyrics(string path)
    {
        _lyrics = new List<(TimeSpan, string)>();
        try
        {
            var lrc = Path.ChangeExtension(path, ".lrc");
            if (File.Exists(lrc))
            {
                foreach (var raw in File.ReadAllLines(lrc))
                {
                    var stamps = Regex.Matches(raw, @"\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]");
                    if (stamps.Count == 0) continue;
                    var text = Regex.Replace(raw, @"\[[^\]]*]", "").Trim();
                    if (text.Length == 0) continue;
                    foreach (Match stamp in stamps)
                    {
                        var minutes = int.Parse(stamp.Groups[1].Value);
                        var seconds = int.Parse(stamp.Groups[2].Value);
                        var fraction = stamp.Groups[3].Success
                            ? stamp.Groups[3].Value.PadRight(3, '0')[..3]
                            : "000";
                        var at = TimeSpan.FromMilliseconds(
                            minutes * 60_000 + seconds * 1_000 + int.Parse(fraction));
                        _lyrics.Add((at, text));
                    }
                }
                _lyrics.Sort((left, right) => left.At.CompareTo(right.At));
            }
        }
        catch
        {
            _lyrics = new List<(TimeSpan, string)>();
        }
        UpdateLyricLine();
    }

    private void UpdateLyricLine()
    {
        if (_lyrics.Count == 0)
        {
            DiscoLyricText.Text = "";
            DiscoLyricNext.Text = "";
            return;
        }

        var index = -1;
        for (var i = 0; i < _lyrics.Count; i++)
        {
            if (_lyrics[i].At > _engine.Position) break;
            index = i;
        }

        DiscoLyricText.Text = index >= 0 ? _lyrics[index].Text : "";
        DiscoLyricNext.Text = index + 1 < _lyrics.Count ? _lyrics[index + 1].Text : "";
    }

    private void OnEngineStateChanged()
    {
        IsPlaying = _engine.IsPlaying;
        PlayPauseGlyph = _engine.IsPlaying ? "\uE769" : "\uE768";
        if (HasCurrent)
            SessionStatus = _engine.IsPlaying ? "ISLAND MEDIA CONTROLS · LIVE" : "ISLAND MEDIA CONTROLS · READY";

        Spectrum.Active = _engine.IsPlaying;
        SyncActivity();

        UpdateSeekFromEngine();
        UpdateShuffleState();
        UpdateRepeatState();
    }

    private void UpdateUpcoming()
    {
        Upcoming.Clear();
        foreach (var track in _engine.GetUpcoming(3)) Upcoming.Add(track);
        QueueSummary = _engine.QueueLength switch
        {
            0 => "Queue is empty",
            1 => "1 in queue",
            _ => $"{_engine.QueueLength} in queue"
        };
    }

    private void UpdateShuffleState() => ShuffleActive = _engine.Shuffle;

    private void UpdateRepeatState()
    {
        RepeatActive = _engine.Repeat != RepeatMode.Off;
        RepeatOneActive = _engine.Repeat == RepeatMode.One;
    }

    private void UpdateSeekFromEngine()
    {
        var duration = _engine.Duration;
        var position = _engine.Position;
        if (!_scrubbing && DateTime.UtcNow >= _seekHoldUntil)
        {
            ElapsedText = FormatTime(position);
            TotalText = FormatTime(duration);
            SeekValue = duration.TotalSeconds > 1
                ? Math.Clamp(position.TotalSeconds / duration.TotalSeconds, 0, 1)
                : 0;
        }
        else if (duration > TimeSpan.Zero)
        {
            TotalText = FormatTime(duration);
        }
    }

    private void Ticker_Tick(object? sender, EventArgs e)
    {
        UpdateSpectrumLines();
        if (!_engine.HasTrack) return;
        if (_scrubbing || DateTime.UtcNow < _seekHoldUntil)
        {
            if (_engine.IsPlaying) _engine.UpdateTimeline();
            return;
        }

        ElapsedText = FormatTime(_engine.Position);
        TotalText = FormatTime(_engine.Duration);
        var duration = _engine.Duration.TotalSeconds;
        SeekValue = duration > 1
            ? Math.Clamp(_engine.Position.TotalSeconds / duration, 0, 1)
            : 0;
        if (_disco) UpdateLyricLine();
        if (_engine.IsPlaying) _engine.UpdateTimeline();
    }

    private void OnBassUpdated(float bass)
    {
        if (!_engine.IsPlaying) return;

        // The spectrum hands us the already-smoothed low band every rendered
        // frame, so the veins follow the kick instead of sampling it at 5 Hz.
        var target = Math.Clamp(0.62 + bass * 0.62, 0.62, 1.0);
        _veinLevel += (target - _veinLevel) * 0.38;
        HeroVeins.Opacity = _veinLevel;

        if (!_disco) return;
        _discoPhase = (_discoPhase + 0.0042f) % 1f;
        var angle = _discoPhase * Math.PI * 2;
        DiscoATransform.X = Math.Sin(angle * 0.8) * 150;
        DiscoATransform.Y = Math.Cos(angle * 0.6) * 90;
        DiscoBTransform.X = Math.Cos(angle * 0.7) * 130;
        DiscoBTransform.Y = Math.Sin(angle * 0.9) * 110;
        DiscoCTransform.X = Math.Sin(angle * 0.5 + 1.3) * 160;
        DiscoCTransform.Y = Math.Cos(angle * 0.8) * 70;
        DiscoDTransform.X = Math.Cos(angle * 0.9 + 2.1) * 120;
        DiscoDTransform.Y = Math.Sin(angle * 0.6) * 80;

        DiscoA.Opacity = 0.28 + bass * 0.55;
        DiscoB.Opacity = 0.24 + bass * 0.48;
        DiscoC.Opacity = 0.22 + bass * 0.52;
        DiscoD.Opacity = 0.18 + bass * 0.42;

        if (bass > 0.48f && DateTime.UtcNow - _lastDiscoColor > TimeSpan.FromMilliseconds(210))
        {
            _lastDiscoColor = DateTime.UtcNow;
            _discoColor = (_discoColor + 1) % DiscoColors.Length;
            DiscoFlashBrush.Color = DiscoColors[_discoColor];
        }
        DiscoFlash.Opacity = bass * 0.16;
    }

    private void ResetVeinBreath()
    {
        _veinLevel = 0.72;
        HeroVeins.Opacity = 0.72;
    }

    private static string FormatTime(TimeSpan value)
    {
        if (value < TimeSpan.Zero) value = TimeSpan.Zero;
        return value.TotalHours >= 1
            ? $"{(int)value.TotalHours}:{value.Minutes:00}:{value.Seconds:00}"
            : $"{value.Minutes}:{value.Seconds:00}";
    }

    private static RadialGradientBrush BuildGlow(uint argb)
    {
        var color = Color.FromArgb(
            (byte)((argb >> 24) & 0xFF),
            (byte)((argb >> 16) & 0xFF),
            (byte)((argb >> 8) & 0xFF),
            (byte)(argb & 0xFF));

        var brush = new RadialGradientBrush
        {
            Center = new Point(0.5, 0.5),
            GradientOrigin = new Point(0.5, 0.5),
            RadiusX = 0.5,
            RadiusY = 0.5
        };
        brush.GradientStops.Add(new GradientStop(Color.FromArgb(0x66, color.R, color.G, color.B), 0));
        brush.GradientStops.Add(new GradientStop(Color.FromArgb(0x1C, color.R, color.G, color.B), 0.6));
        brush.GradientStops.Add(new GradientStop(Colors.Transparent, 1));
        brush.Freeze();
        return brush;
    }

    private async Task RefreshSessionAsync()
    {
        try
        {
            var manager = await GlobalSystemMediaTransportControlsSessionManager.RequestAsync();
            var session = manager.GetSessions().FirstOrDefault(candidate =>
                candidate.SourceAppUserModelId.Contains("Velvet", StringComparison.OrdinalIgnoreCase));
            if (session is null) return;

            var properties = await session.TryGetMediaPropertiesAsync();
            SessionStatus = _engine.IsPlaying ? "ISLAND MEDIA CONTROLS · LIVE" : "ISLAND MEDIA CONTROLS · READY";
            if (properties is not null && !string.IsNullOrWhiteSpace(properties.Title))
                SessionDetail = $"{properties.Title} — {properties.Artist}";
        }
        catch
        {
            // Publishing already happens through SystemMediaTransportControls; this is a status probe only.
        }
    }

    private void PlayQueueFrom(IReadOnlyList<Track> queue, Track track)
    {
        for (var index = 0; index < queue.Count; index++)
        {
            if (queue[index].Id != track.Id) continue;
            _engine.PlayQueue(queue, index);
            return;
        }
    }

    private void Mix_Click(object sender, RoutedEventArgs e)
    {
        if (Tracks.Count == 0)
        {
            ShowToast("Nothing to play", "Add music to your folder first.");
            return;
        }
        var queue = Tracks.ToList();
        _engine.PlayQueue(queue, Random.Shared.Next(queue.Count), shuffle: true);
    }

    private void TrackList_MouseDoubleClick(object sender, MouseButtonEventArgs e)
    {
        if (TrackList.SelectedItem is Track track) PlayQueueFrom(Tracks, track);
    }

    private void TrackPlay_Click(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.Tag is Track track)
        {
            PlayQueueFrom(Tracks, track);
            e.Handled = true;
        }
    }

    private void Album_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ClickCount < 2) return;
        if (sender is FrameworkElement { DataContext: AlbumGroup album } && album.Tracks.Count > 0)
        {
            _engine.PlayQueue(album.Tracks, 0);
            e.Handled = true;
        }
    }

    private void JumpToTrack_Click(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.Tag is Track track)
        {
            _engine.JumpTo(track);
            e.Handled = true;
        }
    }

    private void AlbumsLeft_Click(object sender, RoutedEventArgs e) =>
        AlbumScroll.ScrollToHorizontalOffset(Math.Max(0, AlbumScroll.HorizontalOffset - 430));

    private void AlbumsRight_Click(object sender, RoutedEventArgs e) =>
        AlbumScroll.ScrollToHorizontalOffset(AlbumScroll.HorizontalOffset + 430);

    private void PlayPause_Click(object sender, RoutedEventArgs e)
    {
        if (!_engine.HasTrack && Tracks.Count > 0)
        {
            var queue = Tracks.ToList();
            _engine.PlayQueue(queue, 0);
            return;
        }
        _engine.TogglePlayPause();
    }

    private void Next_Click(object sender, RoutedEventArgs e) => _engine.Next();

    private void Previous_Click(object sender, RoutedEventArgs e) => _engine.Previous();

    private void Shuffle_Click(object sender, RoutedEventArgs e)
    {
        _engine.SetShuffle(!_engine.Shuffle);
        ShuffleActive = _engine.Shuffle;
        UpdateUpcoming();
        PersistSettings();
    }

    private void Repeat_Click(object sender, RoutedEventArgs e)
    {
        _engine.CycleRepeat();
        UpdateRepeatState();
        PersistSettings();
    }

    private void SeekSlider_PreviewMouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (sender is not Slider slider) return;
        _scrubbing = true;
        slider.CaptureMouse();
        ScrubTo(slider, e.GetPosition(slider).X);
        e.Handled = true;
    }

    private void SeekSlider_PreviewMouseMove(object sender, MouseEventArgs e)
    {
        if (!_scrubbing || sender is not Slider slider) return;
        if (e.LeftButton != MouseButtonState.Pressed)
        {
            EndScrub(slider);
            return;
        }
        ScrubTo(slider, e.GetPosition(slider).X);
    }

    private void SeekSlider_PreviewMouseLeftButtonUp(object sender, MouseButtonEventArgs e)
    {
        if (sender is not Slider slider) return;
        if (_scrubbing) ScrubTo(slider, e.GetPosition(slider).X);
        EndScrub(slider);
    }

    private void SeekSlider_LostMouseCapture(object sender, MouseEventArgs e) => _scrubbing = false;

    private void ScrubTo(Slider slider, double x)
    {
        var width = slider.ActualWidth;
        if (width <= 0) return;
        var fraction = Math.Clamp(x / width, 0, 1);
        var duration = _engine.Duration;
        if (duration <= TimeSpan.Zero) return;

        slider.SetCurrentValue(Slider.ValueProperty, fraction);
        SeekValue = fraction;
        ElapsedText = FormatTime(TimeSpan.FromTicks((long)(duration.Ticks * fraction)));

        var now = DateTime.UtcNow;
        if (now - _lastUserSeek < TimeSpan.FromMilliseconds(90)) return;
        _lastUserSeek = now;
        _seekHoldUntil = now.AddMilliseconds(420);
        _engine.SeekToFraction(fraction);
    }

    private void EndScrub(Slider slider)
    {
        _scrubbing = false;
        if (slider.IsMouseCaptured) slider.ReleaseMouseCapture();
        _seekHoldUntil = DateTime.UtcNow.AddMilliseconds(420);
    }

    private async void ChooseFolder_Click(object sender, RoutedEventArgs e) => await PickFolderAsync();

    private async void Rescan_Click(object sender, RoutedEventArgs e) => await ScanAsync(silent: false);

    private async Task PickFolderAsync()
    {
        var dialog = new OpenFolderDialog
        {
            Title = "Choose your music folder",
            Multiselect = false
        };
        if (!string.IsNullOrWhiteSpace(_settings.Folder) && Directory.Exists(_settings.Folder))
            dialog.InitialDirectory = _settings.Folder;

        if (dialog.ShowDialog(this) != true || string.IsNullOrWhiteSpace(dialog.FolderName)) return;

        _settings.Folder = dialog.FolderName;
        PersistSettings();
        UpdateFolderInfo();
        StartWatcher(dialog.FolderName);
        if (SearchText.Length > 0) SearchText = "";
        await ScanAsync(silent: false);
    }

    private void UploadCloud_Click(object sender, RoutedEventArgs e) => OpenCloudPanel(upload: true);

    private void DownloadCloud_Click(object sender, RoutedEventArgs e) => OpenCloudPanel(upload: false);

    private void CloudClose_Click(object sender, RoutedEventArgs e) => IsCloudOverlayOpen = false;

    private void CloudSelectAll_Click(object sender, RoutedEventArgs e)
    {
        foreach (var item in _cloudItems) item.IsSelected = true;
    }

    private void OpenCloudPanel(bool upload)
    {
        if (string.IsNullOrWhiteSpace(_settings.Folder) || !Directory.Exists(_settings.Folder))
        {
            ShowToast("No library yet", "Choose your music folder first, then download Velvet Cloud.");
            return;
        }

        _cloudUpload = upload;
        _cloudItems.Clear();
        CloudTitle = upload ? "Publish to Velvet Cloud" : "Velvet Cloud";
        CloudPathText = "velvet-cloud  ·  synced library";
        CloudSummary = "Reading Velvet Cloud…";
        CloudActionText = upload ? "Publish selected" : "Download selected";
        IsCloudOverlayOpen = true;
        _ = LoadCloudPanelAsync(upload);
    }

    private async Task LoadCloudPanelAsync(bool upload)
    {
        try
        {
            var cloud = await CloudLibraryService.GetCloudAsync(true, CancellationToken.None);
            if (upload)
            {
                foreach (var entry in CloudLibraryService.MissingRemotely(_settings.Folder!, cloud))
                {
                    _cloudItems.Add(new CloudTrack
                    {
                        Name = entry.Name,
                        SizeText = FormatSize(entry.Size),
                        SizeBytes = entry.Size,
                        LocalPath = Path.Combine(_settings.Folder!, entry.Name)
                    });
                }
            }
            else
            {
                foreach (var entry in CloudLibraryService.MissingLocally(cloud, _settings.Folder!))
                {
                    _cloudItems.Add(new CloudTrack
                    {
                        Name = entry.Name,
                        SizeText = FormatSize(entry.Size),
                        SizeBytes = entry.Size,
                        Url = entry.Url,
                        Mirrors = entry.Mirrors
                    });
                }
            }

            CloudSummary = _cloudItems.Count == 0
                ? upload
                    ? "Every track in this library is already in Velvet Cloud."
                    : $"Velvet Cloud is in sync. {cloud.Count} tracks available."
                : $"{_cloudItems.Count} track{(_cloudItems.Count == 1 ? "" : "s")} " +
                  (upload ? "not published yet." : "not in this library yet.");
        }
        catch (Exception exception)
        {
            CloudSummary = $"Velvet Cloud is unreachable. {exception.Message}";
        }
    }

    private async void CloudRun_Click(object sender, RoutedEventArgs e)
    {
        if (_cloudBusy) return;

        var selected = _cloudItems.Where(item => item.IsSelected).ToList();
        if (selected.Count == 0)
        {
            ShowToast("Nothing selected", "Tick at least one track.");
            return;
        }

        _cloudBusy = true;
        CloudActionText = _cloudUpload ? "Publishing…" : "Downloading…";
        var upload = _cloudUpload;

        try
        {
            if (upload)
            {
                var cloud = (await CloudLibraryService.GetCloudAsync(true, CancellationToken.None)).ToList();
                foreach (var item in selected)
                {
                    item.Status = "Publishing";
                    var entry = await CloudLibraryService.UploadAsync(item.LocalPath, item, CancellationToken.None);
                    if (entry is not null)
                    {
                        cloud.RemoveAll(existing =>
                            string.Equals(existing.Name, entry.Name, StringComparison.OrdinalIgnoreCase));
                        cloud.Add(entry);
                    }
                    item.Status = "Published";
                    item.Progress = 1;
                    item.IsSelected = false;
                }
                await CloudLibraryService.PublishManifestAsync(cloud, CancellationToken.None);
            }
            else
            {
                foreach (var item in selected)
                {
                    item.Status = "Downloading";
                    var entry = new CloudLibraryService.CloudEntry(item.Name, item.Url, item.SizeBytes)
                    {
                        Mirrors = item.Mirrors
                    };
                    await CloudLibraryService.DownloadAsync(entry, _settings.Folder!, item,
                        CancellationToken.None);
                    item.Status = "Done";
                    item.IsSelected = false;
                }
                await ScanAsync(silent: true);
            }

            IsCloudOverlayOpen = false;
            ShowToast(upload ? "Velvet Cloud updated" : "Downloaded from Velvet Cloud",
                $"{selected.Count} track{(selected.Count == 1 ? "" : "s")} " +
                (upload ? "published." : "added to your library."));
        }
        catch (Exception exception)
        {
            ShowToast("Velvet Cloud", exception.Message);
        }
        finally
        {
            _cloudBusy = false;
            CloudActionText = upload ? "Publish selected" : "Download selected";
        }
    }

    private async void RecommendCloudSync()
    {
        if (string.IsNullOrWhiteSpace(_settings.Folder) || !Directory.Exists(_settings.Folder)) return;
        try
        {
            var cloud = await CloudLibraryService.GetCloudAsync(force: false, CancellationToken.None);
            var count = CloudLibraryService.MissingLocally(cloud, _settings.Folder!).Count;
            if (count == 0) return;
            ShowToast("Music in Velvet Cloud",
                $"{count} cloud track{(count == 1 ? "" : "s")} aren't in this library. Use the download button in the top bar.");
        }
        catch
        {
            // Offline launches stay quiet.
        }
    }

    private static string FormatSize(long bytes) => bytes >= 1L << 20
        ? $"{bytes / (double)(1 << 20):0.0} MB"
        : $"{Math.Max(1, bytes / 1024)} KB";

    private void Accent_Click(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.Tag is not string key) return;
        var palette = Accents.For(key);
        App.ApplyAccent(palette);
        AccentKey = palette.Key;
        _settings.Accent = palette.Key;
        PersistSettings();
    }

    private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (FindButtonAncestor(e.OriginalSource as DependencyObject) is not null) return;
        if (e.ClickCount == 2)
        {
            ToggleMaximize();
            return;
        }
        if (e.LeftButton == MouseButtonState.Pressed)
        {
            try
            {
                DragMove();
            }
            catch (InvalidOperationException)
            {
                // The mouse was released before the drag loop started.
            }
        }
    }

    private static System.Windows.Controls.Primitives.ButtonBase? FindButtonAncestor(DependencyObject? source)
    {
        while (source is not null)
        {
            if (source is System.Windows.Controls.Primitives.ButtonBase button) return button;
            source = source is Visual or System.Windows.Media.Media3D.Visual3D
                ? VisualTreeHelper.GetParent(source)
                : LogicalTreeHelper.GetParent(source);
        }
        return null;
    }

    private void Minimize_Click(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

    private void Maximize_Click(object sender, RoutedEventArgs e) => ToggleMaximize();

    private void Close_Click(object sender, RoutedEventArgs e) => Close();

    private void ToggleMaximize() =>
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;

    private void UpdateMaximizeIcon() =>
        MaximizeIcon.Text = WindowState == WindowState.Maximized ? "\uE923" : "\uE922";

    private void Window_PreviewKeyDown(object sender, KeyEventArgs e)
    {
        var typing = Keyboard.FocusedElement is TextBox;

        if (e.Key == Key.Space && !typing)
        {
            _engine.TogglePlayPause();
            e.Handled = true;
            return;
        }

        if (Keyboard.Modifiers == ModifierKeys.Control)
        {
            switch (e.Key)
            {
                case Key.O:
                    _ = PickFolderAsync();
                    e.Handled = true;
                    return;
                case Key.F:
                    SearchBox.Focus();
                    SearchBox.CaretIndex = SearchBox.Text.Length;
                    e.Handled = true;
                    return;
                case Key.Right:
                    _engine.Next();
                    e.Handled = true;
                    return;
                case Key.Left:
                    _engine.Previous();
                    e.Handled = true;
                    return;
                case Key.I:
                    ToggleIsland();
                    e.Handled = true;
                    return;
            }
        }

        if (e.Key == Key.Escape && typing)
        {
            SearchText = "";
            TrackList.Focus();
            e.Handled = true;
        }
    }

    private void StartWatcher(string folder)
    {
        _watcher?.Dispose();
        _watcher = null;

        try
        {
            _watcher = new FileSystemWatcher(folder)
            {
                IncludeSubdirectories = true,
                NotifyFilter = NotifyFilters.FileName | NotifyFilters.LastWrite | NotifyFilters.Size,
                EnableRaisingEvents = true
            };
            _watcher.Created += FolderTouched;
            _watcher.Deleted += FolderTouched;
            _watcher.Changed += FolderTouched;
            _watcher.Renamed += (_, args) => FolderTouched(this, args);
            _watcher.Error += (_, _) => { };
        }
        catch
        {
            _watcher = null;
        }
    }

    private void FolderTouched(object? sender, FileSystemEventArgs e)
    {
        if (!LibraryService.IsAudioFile(e.FullPath)) return;
        Dispatcher.BeginInvoke(new Action(() =>
        {
            if (_closing) return;
            _watchDebounce.Stop();
            _watchDebounce.Start();
        }));
    }

    private async void WatchDebounce_Tick(object? sender, EventArgs e)
    {
        _watchDebounce.Stop();
        if (_scanning || _closing)
        {
            if (!_closing) _watchDebounce.Start();
            return;
        }
        await ScanAsync(silent: true);
    }

    private void ShowToast(string title, string message)
    {
        ToastTitleText.Text = title;
        ToastMessageText.Text = message;
        ToastBorder.Visibility = Visibility.Visible;
        ToastBorder.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(180)));
        _toastTimer.Stop();
        _toastTimer.Start();
    }

    private void PersistSettings()
    {
        _settings.Volume = _engine.Volume;
        _settings.Shuffle = _engine.Shuffle;
        _settings.Repeat = _engine.Repeat;
        _settings.LastTrackId = _engine.Current?.Id;
        _settings.VisualizerSensitivity = _visualizerSensitivity;
        _settingsService.Save(_settings);
    }

    public event EventHandler? HiddenToTray;

    private void OnClosing(object? sender, CancelEventArgs e)
    {
        if (!App.Quitting)
        {
            e.Cancel = true;
            PersistSettings();
            Hide();
            HiddenToTray?.Invoke(this, EventArgs.Empty);
            return;
        }

        _closing = true;
        PersistSettings();
        _ticker.Stop();
        _watchDebounce.Stop();
        _toastTimer.Stop();
        _settingsDebounce.Stop();
        _scanCts?.Cancel();
        _scanCts?.Dispose();
        _watcher?.Dispose();
        _watcher = null;
        var island = _island;
        _island = null;
        island?.CloseForApp();
        _spectrum.Dispose();
        _engine.Dispose();
        _coverService.Dispose();
    }

    private static string BuildGreeting()
    {
        var hour = DateTime.Now.Hour;
        return hour switch
        {
            < 5 => "Still up",
            < 12 => "Good morning",
            < 18 => "Good afternoon",
            _ => "Good evening"
        };
    }

    private bool Set<T>(ref T storage, T value, [CallerMemberName] string? propertyName = null)
    {
        if (EqualityComparer<T>.Default.Equals(storage, value)) return false;
        storage = value;
        OnPropertyChanged(propertyName);
        return true;
    }

    private void OnPropertyChanged(string? propertyName) =>
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(propertyName));
}
