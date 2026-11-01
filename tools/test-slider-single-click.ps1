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
$root = [System.Windows.Automation.AutomationElement]::RootElement
function Island() {
    foreach ($w in $root.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)) {
        try { if ($w.Current.Name -eq 'Velvet Island') { return $w } } catch {}
    }
}
function ByName($island, $name) {
    $cond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::NameProperty, $name)
    return $island.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $cond)
}
$island = Island
$r = $island.Current.BoundingRectangle
"Island: $r"

$minBtn = ByName $island 'Island minimal'
if (-not $minBtn) {
    # minimal mode currently: double click to restore
    $cx = [int]($r.X + $r.Width / 2); $cy = [int]($r.Y + $r.Height / 2)
    [S]::SetCursorPos($cx, $cy) | Out-Null
    Start-Sleep -Milliseconds 200
    [S]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 60; [S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
    Start-Sleep -Milliseconds 120
    [S]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 60; [S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
    Start-Sleep -Milliseconds 800
    $island = Island
    "Restored: $($island.Current.BoundingRectangle)"
}

$gear = ByName $island 'Island settings'
$p = $null
$gear.TryGetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern, [ref]$p) | Out-Null
$p.Invoke()
Start-Sleep -Milliseconds 600
$island = Island
"After gear: $($island.Current.BoundingRectangle)"
$slider = ByName $island 'Island opacity'
if (-not $slider) { "slider missing"; exit 1 }
$sr = $slider.Current.BoundingRectangle
$tx = [int]($sr.X + $sr.Width * 0.2); $ty = [int]($sr.Y + $sr.Height / 2)
"Click at ($tx,$ty)"
[S]::SetCursorPos($tx, $ty) | Out-Null
Start-Sleep -Milliseconds 250
$c = New-Object S+POINT; [S]::GetCursorPos([ref]$c) | Out-Null; "cursor ($($c.X),$($c.Y))"
[S]::mouse_event(2,0,0,0,[UIntPtr]::Zero)
Start-Sleep -Milliseconds 120
[S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
Start-Sleep -Milliseconds 600
$island = Island
foreach ($t in $island.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
    try { if ($t.Current.Name -match '%') { "label: $($t.Current.Name)" } } catch {}
}
