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
    for ($i = 0; $i -lt 6; $i++) {
        [S]::SetCursorPos($x, $y) | Out-Null
        Start-Sleep -Milliseconds 40
        $p = New-Object S+POINT; [S]::GetCursorPos([ref]$p) | Out-Null
        if ([Math]::Abs($p.X - $x) -le 1 -and [Math]::Abs($p.Y - $y) -le 1) { return $true }
        Start-Sleep -Milliseconds 30
    }
    return $false
}
function ClickAt($x, $y) {
    MoveTo $x $y | Out-Null
    Start-Sleep -Milliseconds 120
    [S]::mouse_event(2, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 70
    [S]::mouse_event(4, 0, 0, 0, [UIntPtr]::Zero)
}
function Island() {
    $root = [System.Windows.Automation.AutomationElement]::RootElement
    foreach ($w in $root.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)) {
        try { if ($w.Current.Name -eq 'Velvet Island') { return $w } } catch {}
    }
    return $null
}
function ByName($island, $name) {
    $cond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::NameProperty, $name)
    return $island.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $cond)
}

$island = Island
"Start: $($island.Current.BoundingRectangle)"

# 1. double-click minimal island center to restore
$r = $island.Current.BoundingRectangle
$cx = [int]($r.X + $r.Width / 2); $cy = [int]($r.Y + $r.Height / 2)
"Double-click at ($cx,$cy)"
MoveTo $cx $cy | Out-Null
Start-Sleep -Milliseconds 150
[S]::mouse_event(2, 0, 0, 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 60; [S]::mouse_event(4, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 120
[S]::mouse_event(2, 0, 0, 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 60; [S]::mouse_event(4, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 900
$island = Island
"After double-click: $($island.Current.BoundingRectangle)"

# 2. open settings via Invoke
$gear = ByName $island 'Island settings'
if ($gear) {
    $p = $null
    if ($gear.TryGetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern, [ref]$p)) { $p.Invoke(); "opened settings" }
}
Start-Sleep -Milliseconds 800
$island = Island
"Settings open: $($island.Current.BoundingRectangle)"
$slider = ByName $island 'Island opacity'
if (-not $slider) { "no slider"; exit 1 }
$sr = $slider.Current.BoundingRectangle
"Slider: $sr"

# 3. click at 25% of track
$ty = [int]($sr.Y + $sr.Height / 2)
$tx = [int]($sr.X + $sr.Width * 0.25)
"Click slider at ($tx,$ty)"
ClickAt $tx $ty
Start-Sleep -Milliseconds 500
$island = Island
foreach ($t in $island.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
    try { if ($t.Current.Name -match '%') { "Label after click: $($t.Current.Name)" } } catch {}
}

# 4. drag from 25% to 85%
$fromX = $tx; $toX = [int]($sr.X + $sr.Width * 0.85)
"Drag $fromX -> $toX"
MoveTo $fromX $ty | Out-Null
Start-Sleep -Milliseconds 120
[S]::mouse_event(2, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 120
for ($i = 1; $i -le 30; $i++) {
    [S]::SetCursorPos([int]($fromX + ($toX - $fromX) * $i / 30), $ty) | Out-Null
    Start-Sleep -Milliseconds 22
}
Start-Sleep -Milliseconds 100
[S]::mouse_event(4, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 600
$island = Island
foreach ($t in $island.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
    try { if ($t.Current.Name -match '%') { "Label after drag: $($t.Current.Name)" } } catch {}
}
