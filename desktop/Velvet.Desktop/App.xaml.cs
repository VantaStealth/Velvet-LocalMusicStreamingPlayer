using System.IO;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows;
using System.Windows.Media;
using Microsoft.Win32;
using Velvet.Desktop.Models;
using Forms = System.Windows.Forms;

namespace Velvet.Desktop;

public partial class App : Application
{
    private Mutex? _instanceMutex;
    private Forms.NotifyIcon? _tray;
    private bool _trayHintShown;

    /// <summary>True once the tray's Quit command (or a Windows session end) is closing the app.</summary>
    public static bool Quitting { get; private set; }

    protected override void OnStartup(StartupEventArgs e)
    {
        _instanceMutex = new Mutex(true, @"Local\Velvet.Music.Player", out var isFirstInstance);
        if (!isFirstInstance)
        {
            ActivateExistingWindow();
            Shutdown();
            return;
        }

        base.OnStartup(e);
        ApplyAccent(Accents.All[0]);

        DispatcherUnhandledException += (_, args) =>
        {
            try
            {
                var directory = Path.Combine(
                    Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Velvet");
                Directory.CreateDirectory(directory);
                File.AppendAllText(
                    Path.Combine(directory, "error.log"),
                    $"[{DateTimeOffset.Now:u}] {args.Exception}{Environment.NewLine}{Environment.NewLine}");
            }
            catch
            {
                // Logging must never mask the original failure.
            }

            MessageBox.Show(
                args.Exception.Message,
                "Velvet hit a snag",
                MessageBoxButton.OK,
                MessageBoxImage.Warning);
            args.Handled = true;
        };

        var window = new MainWindow();
        MainWindow = window;
        window.Show();
        SetupTray(window);
    }

    private void SetupTray(MainWindow window)
    {
        var stream = GetResourceStream(new Uri("Assets/velvet-tray.ico", UriKind.Relative))?.Stream;
        if (stream is null) return;

        var menu = new Forms.ContextMenuStrip();
        menu.Items.Add("Open Velvet", null, (_, _) => Restore(window));
        menu.Items.Add("Quit Velvet", null, (_, _) =>
        {
            Quitting = true;
            Shutdown();
        });

        _tray = new Forms.NotifyIcon
        {
            Icon = new System.Drawing.Icon(stream),
            Text = "Velvet",
            Visible = true,
            ContextMenuStrip = menu
        };
        _tray.MouseClick += (_, args) =>
        {
            if (args.Button == Forms.MouseButtons.Left) Restore(window);
        };

        window.HiddenToTray += (_, _) =>
        {
            if (_trayHintShown) return;
            _trayHintShown = true;
            _tray?.ShowBalloonTip(
                3200, "Velvet is still playing",
                "Closing the window keeps playback alive. Right-click the tray icon to quit.",
                Forms.ToolTipIcon.None);
        };

        PromoteTrayIcon();
    }

    /// <summary>
    /// Windows 11 keeps tray visibility in HKCU\Control Panel\NotifyIconSettings. The entry for an
    /// executable only exists after its icon has been shown once, so poll briefly and pin ourselves
    /// into the visible tray area instead of the overflow flyout.
    /// </summary>
    private static async void PromoteTrayIcon()
    {
        try
        {
            var exe = Environment.ProcessPath;
            if (string.IsNullOrEmpty(exe)) return;
            var target = Path.GetFullPath(exe);

            for (var attempt = 0; attempt < 12; attempt++)
            {
                await Task.Delay(attempt == 0 ? 600 : 1000);
                using var settings = Registry.CurrentUser.OpenSubKey(
                    @"Control Panel\NotifyIconSettings", writable: true);
                if (settings is null) continue;

                foreach (var name in settings.GetSubKeyNames())
                {
                    using var key = settings.OpenSubKey(name, writable: true);
                    if (key?.GetValue("ExecutablePath") is not string stored) continue;
                    if (!string.Equals(ExpandKnownFolder(stored), target, StringComparison.OrdinalIgnoreCase))
                        continue;
                    if (key.GetValue("IsPromoted") is int promoted && promoted == 1) return;
                    key.SetValue("IsPromoted", 1, RegistryValueKind.DWord);
                    return;
                }
            }
        }
        catch
        {
            // Tray promotion is cosmetic; never let it break startup.
        }
    }

    private static string ExpandKnownFolder(string path)
    {
        var separator = path.IndexOf('}');
        if (!path.StartsWith('{') || separator < 0) return path;

        Environment.SpecialFolder? folder = path[..(separator + 1)].ToUpperInvariant() switch
        {
            "{6D809377-6AF0-444B-8957-A3773F02200E}" => Environment.SpecialFolder.ProgramFiles,
            "{7C5A40EF-A0FB-4BFC-874A-C0F2E0B9FA8E}" => Environment.SpecialFolder.ProgramFilesX86,
            "{F38BF404-1D43-42F2-9305-67DE0B28FC23}" => Environment.SpecialFolder.Windows,
            "{F1B32785-6FBA-4FCF-9D55-7B8E7F157091}" => Environment.SpecialFolder.LocalApplicationData,
            "{5E6C858F-0E22-4760-9AFE-EA3317B67173}" => Environment.SpecialFolder.UserProfile,
            _ => null
        };

        return folder is null ? path : Environment.GetFolderPath(folder.Value) + path[separator..];
    }

    protected override void OnSessionEnding(SessionEndingCancelEventArgs e)
    {
        Quitting = true;
        base.OnSessionEnding(e);
    }

    private static void Restore(Window window)
    {
        if (window.WindowState == WindowState.Minimized) window.WindowState = WindowState.Normal;
        window.Show();
        window.Activate();
        window.Topmost = true;
        window.Topmost = false;
        window.Focus();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        if (_tray is not null)
        {
            _tray.Visible = false;
            _tray.Dispose();
            _tray = null;
        }
        _instanceMutex?.Dispose();
        base.OnExit(e);
    }

    private static void ActivateExistingWindow()
    {
        var handle = FindWindow(null, "Velvet");
        if (handle == IntPtr.Zero) return;
        ShowWindow(handle, 9);
        SetForegroundWindow(handle);
    }

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr FindWindow(string? className, string windowName);

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr handle, int command);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr handle);

    public static void ApplyAccent(AccentPalette palette)
    {
        var resources = Current.Resources;
        resources["AccentColor"] = palette.Primary;
        resources["AccentBrightColor"] = palette.Bright;
        resources["AccentBrush"] = Frozen(palette.Primary);
        resources["AccentBrightBrush"] = Frozen(palette.Bright);
        resources["AccentDeepBrush"] = Frozen(palette.Deep);
        resources["AccentGlowBrush"] = Frozen(palette.Glow);
        resources["AccentRowBrush"] = Frozen(WithAlpha(palette.Primary, 0x18));
        resources["AccentSoftBrush"] = Frozen(WithAlpha(palette.Primary, 0x2E));

        var gradient = new LinearGradientBrush
        {
            StartPoint = new Point(0, 0),
            EndPoint = new Point(1, 1)
        };
        gradient.GradientStops.Add(new GradientStop(palette.GradientStart, 0));
        gradient.GradientStops.Add(new GradientStop(palette.GradientEnd, 1));
        gradient.Freeze();
        resources["AccentGradient"] = gradient;

        var hero = new LinearGradientBrush
        {
            StartPoint = new Point(0, 0),
            EndPoint = new Point(1, 1)
        };
        hero.GradientStops.Add(new GradientStop(WithAlpha(palette.Deep, 0xCC), 0));
        hero.GradientStops.Add(new GradientStop(Color.FromRgb(0x15, 0x11, 0x1E), 0.62));
        hero.GradientStops.Add(new GradientStop(Color.FromRgb(0x0C, 0x0A, 0x11), 1));
        hero.Freeze();
        resources["AccentHeroBrush"] = hero;

        var aurora = new RadialGradientBrush();
        aurora.GradientStops.Add(new GradientStop(WithAlpha(palette.Glow, 0x66), 0));
        aurora.GradientStops.Add(new GradientStop(WithAlpha(palette.Glow, 0x22), 0.55));
        aurora.GradientStops.Add(new GradientStop(Colors.Transparent, 1));
        aurora.Freeze();
        resources["AccentAuroraBrush"] = aurora;

        var auroraSoft = new RadialGradientBrush();
        auroraSoft.GradientStops.Add(new GradientStop(WithAlpha(palette.Deep, 0x4D), 0));
        auroraSoft.GradientStops.Add(new GradientStop(Colors.Transparent, 1));
        auroraSoft.Freeze();
        resources["AccentAuroraSoftBrush"] = auroraSoft;

        resources["AccentAuroraHaloBrush"] = WithOpacity(aurora, 0.9);
        resources["AccentAuroraDimBrush"] = WithOpacity(aurora, 0.55);
        resources["AccentAuroraSoftDimBrush"] = WithOpacity(auroraSoft, 0.45);
        resources["AccentAuroraSoftFaintBrush"] = WithOpacity(auroraSoft, 0.28);

        resources["VeinBrush"] = BuildVeinBrush(palette);
        resources["IslandVeinBrush"] = BuildVeinBrush(palette, 0.22);
        resources["HeroVeinBrush"] = BuildVeinBrush(palette, 0.85);
    }

    private static DrawingBrush BuildVeinBrush(AccentPalette palette, double alphaScale = 1.0)
    {
        string[] paths =
        {
            "M0.02,-0.05 C0.10,0.12 0.04,0.22 0.13,0.34 C0.20,0.44 0.14,0.52 0.22,0.62 C0.29,0.72 0.24,0.86 0.30,1.05",
            "M0.13,0.34 C0.20,0.36 0.24,0.42 0.31,0.40 C0.37,0.38 0.40,0.30 0.47,0.30",
            "M0.22,0.62 C0.31,0.66 0.36,0.60 0.43,0.65 C0.50,0.70 0.52,0.80 0.61,0.83 C0.68,0.86 0.72,0.92 0.80,0.95",
            "M0.47,0.30 C0.55,0.34 0.58,0.24 0.66,0.27",
            "M0.61,0.83 C0.67,0.78 0.73,0.80 0.78,0.72 C0.83,0.64 0.80,0.55 0.88,0.50",
            "M0.88,0.50 C0.94,0.44 0.92,0.34 1.02,0.30",
            "M0.80,0.95 C0.86,1.02 0.94,0.99 1.03,1.04",
            "M0.44,-0.02 C0.47,0.08 0.53,0.10 0.55,0.18",
            "M0.66,0.27 C0.70,0.33 0.66,0.40 0.72,0.45",
            "M0.31,0.40 C0.30,0.48 0.36,0.51 0.34,0.58",
            "M0.72,0.45 C0.78,0.48 0.82,0.44 0.88,0.50"
        };

        var glowPen = new Pen(Frozen(WithAlpha(palette.Bright, Scaled(0x10, alphaScale))), 0.022);
        glowPen.Freeze();
        var bodyPen = new Pen(Frozen(WithAlpha(palette.Bright, Scaled(0x33, alphaScale))), 0.006);
        bodyPen.Freeze();
        var corePen = new Pen(Frozen(WithAlpha(palette.Bright, Scaled(0x5C, alphaScale))), 0.0022);
        corePen.Freeze();

        var group = new DrawingGroup();
        foreach (var data in paths)
        {
            var geometry = Geometry.Parse(data);
            geometry.Freeze();
            group.Children.Add(new GeometryDrawing(null, glowPen, geometry));
            group.Children.Add(new GeometryDrawing(null, bodyPen, geometry));
            group.Children.Add(new GeometryDrawing(null, corePen, geometry));
        }
        group.Freeze();

        var brush = new DrawingBrush(group)
        {
            Stretch = Stretch.Fill,
            AlignmentX = AlignmentX.Left,
            AlignmentY = AlignmentY.Top
        };
        brush.Freeze();
        return brush;
    }

    private static SolidColorBrush Frozen(Color color)
    {
        var brush = new SolidColorBrush(color);
        brush.Freeze();
        return brush;
    }

    private static Brush WithOpacity(Brush brush, double opacity)
    {
        var copy = brush.Clone();
        copy.Opacity = opacity;
        copy.Freeze();
        return copy;
    }

    private static Color WithAlpha(Color color, byte alpha) =>
        Color.FromArgb(alpha, color.R, color.G, color.B);

    private static byte Scaled(byte alpha, double scale) =>
        (byte)Math.Clamp((int)Math.Round(alpha * scale), 0, 255);
}
