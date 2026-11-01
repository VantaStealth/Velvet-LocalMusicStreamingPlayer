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
function InvokeBtn($b) { $p = $null; if ($b -and $b.TryGetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern, [ref]$p)) { $p.Invoke(); return $true }; return $false }
function DblClick($x, $y) {
    [S]::SetCursorPos($x, $y) | Out-Null; Start-Sleep -Milliseconds 220
    [S]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 60; [S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
    Start-Sleep -Milliseconds 130
    [S]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 60; [S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
}
function ClickAt($x, $y) {
    [S]::SetCursorPos($x, $y) | Out-Null; Start-Sleep -Milliseconds 250
    [S]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 110; [S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
}

$island = Island
"Island: $($island.Current.BoundingRectangle)"
if (-not (ByName $island 'Island minimal')) {
    $r = $island.Current.BoundingRectangle
    "expanding via double-click"
    DblClick ([int]($r.X + $r.Width/2)) ([int]($r.Y + $r.Height/2))
    Start-Sleep -Milliseconds 900
    $island = Island
    "Now: $($island.Current.BoundingRectangle)"
}
if (-not (ByName $island 'Island opacity')) {
    "opening settings"
    InvokeBtn (ByName $island 'Island settings') | Out-Null
    Start-Sleep -Milliseconds 800
    $island = Island
    "Now: $($island.Current.BoundingRectangle)"
}
$slider = ByName $island 'Island opacity'
if (-not $slider) { "slider missing -> abort"; exit 1 }
$sr = $slider.Current.BoundingRectangle
"Slider: $sr"
$tx = [int]($sr.X + $sr.Width * 0.2); $ty = [int]($sr.Y + $sr.Height / 2)
"Click at ($tx,$ty)"
ClickAt $tx $ty
Start-Sleep -Milliseconds 700
$island = Island
foreach ($t in $island.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
    try { if ($t.Current.Name -match '%') { "label: $($t.Current.Name)" } } catch {}
}
