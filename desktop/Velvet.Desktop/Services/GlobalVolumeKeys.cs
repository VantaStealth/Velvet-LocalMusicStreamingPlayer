using System.Runtime.InteropServices;

namespace Velvet.Desktop;

/// <summary>
/// Replaces the Windows volume flyout: volume key presses are swallowed globally and the
/// system media volume is stepped through Core Audio while Velvet draws its own HUD.
/// </summary>
public static class GlobalVolumeKeys
{
    private const int WhKeyboardLl = 13;
    private const int WmKeyDown = 0x0100;
    private const int WmSysKeyDown = 0x0104;
    private const int VkVolumeMute = 0xAD;
    private const int VkVolumeDown = 0xAE;
    private const int VkVolumeUp = 0xAF;

    private const int ERender = 0;
    private const int EMultimedia = 1;
    private const int ClsctxAll = 23;

    private static HookProc? _callback;
    private static IntPtr _hook;
    private static VolumeHudWindow? _hud;
    private static IAudioEndpointVolume? _endpoint;
    private static Guid _context = Guid.Empty;

    public static void Start(VolumeHudWindow hud)
    {
        if (_hook != IntPtr.Zero) return;
        _hud = hud;
        _callback = HookCallback;
        _hook = SetWindowsHookEx(WhKeyboardLl, _callback, GetModuleHandle(null), 0);
    }

    public static void Stop()
    {
        if (_hook != IntPtr.Zero)
        {
            UnhookWindowsHookEx(_hook);
            _hook = IntPtr.Zero;
        }
        _callback = null;
        _hud = null;
        _endpoint = null;
    }

    private static IntPtr HookCallback(int nCode, IntPtr wParam, IntPtr lParam)
    {
        if (nCode >= 0 && (wParam == (IntPtr)WmKeyDown || wParam == (IntPtr)WmSysKeyDown))
        {
            var info = Marshal.PtrToStructure<KbdLlHookStruct>(lParam);
            var key = (int)info.vkCode;
            if (key is VkVolumeUp or VkVolumeDown or VkVolumeMute)
            {
                try
                {
                    var endpoint = Endpoint;
                    if (endpoint is not null)
                    {
                        Handle(endpoint, key);
                        return (IntPtr)1;
                    }
                }
                catch
                {
                    // If Core Audio is unavailable, fall back to the Windows keys.
                    _endpoint = null;
                }
            }
        }

        return CallNextHookEx(_hook, nCode, wParam, lParam);
    }

    private static void Handle(IAudioEndpointVolume endpoint, int key)
    {
        endpoint.GetMute(out var muted);
        if (key == VkVolumeMute)
        {
            endpoint.SetMute(!muted, ref _context);
        }
        else
        {
            if (muted) endpoint.SetMute(false, ref _context);
            if (key == VkVolumeUp) endpoint.VolumeStepUp(ref _context);
            else endpoint.VolumeStepDown(ref _context);
        }

        endpoint.GetMasterVolumeLevelScalar(out var level);
        endpoint.GetMute(out var nowMuted);
        _hud?.ShowLevel(level, nowMuted);
    }

    private static IAudioEndpointVolume? Endpoint
    {
        get
        {
            if (_endpoint is not null) return _endpoint;

            var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumeratorComObject();
            enumerator.GetDefaultAudioEndpoint(ERender, EMultimedia, out var device);
            var iid = typeof(IAudioEndpointVolume).GUID;
            device.Activate(ref iid, ClsctxAll, IntPtr.Zero, out var instance);
            _endpoint = instance as IAudioEndpointVolume;
            return _endpoint;
        }
    }

    private delegate IntPtr HookProc(int nCode, IntPtr wParam, IntPtr lParam);

    [StructLayout(LayoutKind.Sequential)]
    private struct KbdLlHookStruct
    {
        public uint vkCode;
        public uint scanCode;
        public uint flags;
        public uint time;
        public IntPtr extraInfo;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern IntPtr SetWindowsHookEx(int idHook, HookProc callback, IntPtr module, uint threadId);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool UnhookWindowsHookEx(IntPtr hook);

    [DllImport("user32.dll")]
    private static extern IntPtr CallNextHookEx(IntPtr hook, int nCode, IntPtr wParam, IntPtr lParam);

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode)]
    private static extern IntPtr GetModuleHandle(string? moduleName);

    [ComImport]
    [Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    private class MMDeviceEnumeratorComObject
    {
    }

    [ComImport]
    [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDeviceEnumerator
    {
        int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);
        int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice endpoint);
        int GetDevice(string id, out IMMDevice device);
        int RegisterEndpointNotificationCallback(IntPtr client);
        int UnregisterEndpointNotificationCallback(IntPtr client);
    }

    [ComImport]
    [Guid("D666063F-1587-4E43-81F1-B948E807363F")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IMMDevice
    {
        int Activate(ref Guid iid, int clsCtx, IntPtr activationParams,
            [MarshalAs(UnmanagedType.IUnknown)] out object instance);
        int OpenPropertyStore(int access, out IntPtr properties);
        int GetId([MarshalAs(UnmanagedType.LPWStr)] out string id);
        int GetState(out int state);
    }

    [ComImport]
    [Guid("5CDF2C82-841E-4546-9722-0CF74078229A")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IAudioEndpointVolume
    {
        int RegisterControlChangeNotify(IntPtr notify);
        int UnregisterControlChangeNotify(IntPtr notify);
        int GetChannelCount(out int count);
        int SetMasterVolumeLevel(float level, ref Guid context);
        int SetMasterVolumeLevelScalar(float level, ref Guid context);
        int GetMasterVolumeLevel(out float level);
        int GetMasterVolumeLevelScalar(out float level);
        int SetChannelVolumeLevel(uint channel, float level, ref Guid context);
        int SetChannelVolumeLevelScalar(uint channel, float level, ref Guid context);
        int GetChannelVolumeLevel(uint channel, out float level);
        int GetChannelVolumeLevelScalar(uint channel, out float level);
        int SetMute([MarshalAs(UnmanagedType.Bool)] bool mute, ref Guid context);
        int GetMute([MarshalAs(UnmanagedType.Bool)] out bool mute);
        int GetVolumeStepInfo(out uint step, out uint stepCount);
        int VolumeStepUp(ref Guid context);
        int VolumeStepDown(ref Guid context);
        int QueryHardwareSupport(out uint mask);
        int GetVolumeRange(out float min, out float max, out float increment);
    }
}
