using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media.Animation;
using System.Windows.Threading;

namespace Velvet.Desktop;

public partial class VolumeHudWindow : Window
{
    private readonly DispatcherTimer _hideTimer;

    public VolumeHudWindow()
    {
        InitializeComponent();
        Opacity = 0;

        _hideTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1.8) };
        _hideTimer.Tick += (_, _) =>
        {
            _hideTimer.Stop();
            var fade = new DoubleAnimation(0, TimeSpan.FromMilliseconds(200));
            fade.Completed += (_, _) => Hide();
            BeginAnimation(OpacityProperty, fade);
        };
    }

    protected override void OnSourceInitialized(EventArgs e)
    {
        base.OnSourceInitialized(e);
        var handle = new WindowInteropHelper(this).Handle;
        var exStyle = GetWindowLong(handle, GwlExStyle);
        SetWindowLong(handle, GwlExStyle, exStyle | WsExNoActivate | WsExToolWindow);
    }

    private const int GwlExStyle = -20;
    private const int WsExNoActivate = 0x08000000;
    private const int WsExToolWindow = 0x00000080;

    [DllImport("user32.dll")]
    private static extern int GetWindowLong(IntPtr hwnd, int index);

    [DllImport("user32.dll")]
    private static extern int SetWindowLong(IntPtr hwnd, int index, int value);

    public void ShowLevel(float level, bool muted)
    {
        level = Math.Clamp(level, 0f, 1f);
        PercentText.Text = muted ? "MUTE" : $"{level * 100:0}";
        SuffixText.Text = muted ? string.Empty : "%";
        DetailText.Text = muted ? "SYSTEM VOLUME · MUTED" : "SYSTEM VOLUME";
        SpeakerGlyph.Text = muted ? "\uE74F" : "\uE767";

        if (!IsVisible) Show();
        UpdateLayout();

        var track = TrackHost.ActualWidth > 0 ? TrackHost.ActualWidth : 262;
        Fill.Width = Math.Max(0, track * level);
        KnobShift.X = Math.Max(0, track * level);

        var area = SystemParameters.WorkArea;
        Left = area.Left + (area.Width - Width) / 2;
        Top = area.Bottom - Height - 24;

        BeginAnimation(OpacityProperty, null);
        Opacity = 1;
        _hideTimer.Stop();
        _hideTimer.Start();
    }
}
