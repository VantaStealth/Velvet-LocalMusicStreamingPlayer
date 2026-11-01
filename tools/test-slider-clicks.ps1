Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class S {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern bool GetCursorPos(out POINT p);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extra);
    public struct POINT { public int X; public int Y; }
}
"@
[S]::SetProcessDPIAware() | Out-Null

function MoveTo($x, $y) {
    for ($i = 0; $i -lt 8; $i++) {
        [S]::SetCursorPos($x, $y) | Out-Null
        Start-Sleep -Milliseconds 35
        $p = New-Object S+POINT; [S]::GetCursorPos([ref]$p) | Out-Null
        if ([Math]::Abs($p.X - $x) -le 1 -and [Math]::Abs($p.Y - $y) -le 1) { return }
    }
}
function ClickAt($x, $y) {
    MoveTo $x $y
    Start-Sleep -Milliseconds 140
    [S]::mouse_event(2, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 90
    [S]::mouse_event(4, 0, 0, 0, [UIntPtr]::Zero)
}

$root = [System.Windows.Automation.AutomationElement]::RootElement
$island = $null
foreach ($w in $root.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)) {
    try { if ($w.Current.Name -eq 'Velvet Island') { $island = $w } } catch {}
}
$cond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::NameProperty, 'Island opacity')
$slider = $island.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $cond)
if (-not $slider) { "open settings first"; exit 1 }
$r = $slider.Current.BoundingRectangle
$ty = [int]($r.Y + $r.Height / 2)
foreach ($frac in @(0.15, 0.55, 0.95)) {
    $x = [int]($r.X + $r.Width * $frac)
    ClickAt $x $ty
    Start-Sleep -Milliseconds 450
    $label = "?"
    foreach ($t in $island.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
        try { if ($t.Current.Name -match '^\d+%$') { $label = $t.Current.Name } } catch {}
    }
    $rv = $null
    $val = "n/a"
    if ($slider.TryGetCurrentPattern([System.Windows.Automation.RangeValuePattern]::Pattern, [ref]$rv)) { $val = $rv.Current.Value }
    "click frac=$frac x=$x -> label=$label sliderValue=$val"
}
