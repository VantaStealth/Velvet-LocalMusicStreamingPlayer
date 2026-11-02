Add-Type @'
using System;
using System.Runtime.InteropServices;
using System.Text;
public class VKTest {
  [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
  public delegate bool EnumProc(IntPtr hWnd, IntPtr lParam);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr lParam);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr hWnd, StringBuilder text, int count);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
  public static bool Find(string title) {
    bool found = false;
    EnumWindows((h, l) => {
      var sb = new StringBuilder(256);
      GetWindowText(h, sb, 256);
      if (sb.ToString() == title && IsWindowVisible(h)) found = true;
      return true;
    }, IntPtr.Zero);
    return found;
  }
}
'@

[VKTest]::keybd_event(0xAF, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 400
Write-Output ("HUD visible: " + [VKTest]::Find('Velvet Volume'))
Start-Sleep -Milliseconds 2300
Write-Output ("HUD hidden after delay: " + (-not [VKTest]::Find('Velvet Volume')))
