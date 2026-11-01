Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type @"
using System;
using System.Text;
using System.Runtime.InteropServices;
public class W {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern bool GetCursorPos(out POINT p);
    [DllImport("user32.dll")] public static extern IntPtr WindowFromPoint(POINT p);
    [DllImport("user32.dll")] public static extern int GetClassName(IntPtr h, StringBuilder sb, int max);
    [DllImport("user32.dll")] public static extern int GetWindowText(IntPtr h, StringBuilder sb, int max);
    [DllImport("user32.dll")] public static extern int GetWindowLong(IntPtr h, int idx);
    [DllImport("user32.dll")] public static extern IntPtr GetAncestor(IntPtr h, uint flags);
    public struct POINT { public int X; public int Y; }
}
"@
[W]::SetProcessDPIAware() | Out-Null

function Probe($x, $y) {
    [W]::SetCursorPos($x, $y) | Out-Null
    Start-Sleep -Milliseconds 150
    $p = New-Object W+POINT; $p.X = $x; $p.Y = $y
    $c = New-Object W+POINT; [W]::GetCursorPos([ref]$c) | Out-Null
    $h = [W]::WindowFromPoint($p)
    $root = [W]::GetAncestor($h, 2)
    $cls = New-Object System.Text.StringBuilder 256; [W]::GetClassName($root, $cls, 256) | Out-Null
    $txt = New-Object System.Text.StringBuilder 256; [W]::GetWindowText($root, $txt, 256) | Out-Null
    $ex = [W]::GetWindowLong($root, -20)
    "probe($x,$y) cursor=($($c.X),$($c.Y)) hwnd=$root class='$cls' title='$txt' exStyle=0x$('{0:X}' -f $ex)"
}
Probe 898 104
Probe 1002 37
Probe 955 37
