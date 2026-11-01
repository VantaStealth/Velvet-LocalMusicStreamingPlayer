Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Native {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extra);
    [DllImport("user32.dll")] public static extern int GetSystemMetrics(int index);
    public const uint LEFTDOWN = 0x0002, LEFTUP = 0x0004;
}
"@
[Native]::SetProcessDPIAware() | Out-Null
"Screen: $([Native]::GetSystemMetrics(0))x$([Native]::GetSystemMetrics(1)) physical"

$root = [System.Windows.Automation.AutomationElement]::RootElement
$island = $null
foreach ($w in $root.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)) {
    try { if ($w.Current.Name -like "*Velvet*" -and $w.Current.BoundingRectangle.Width -lt 520) { $island = $w } } catch {}
}
if (-not $island) { "island not found"; exit 1 }
"Island rect: $($island.Current.BoundingRectangle)"

function FindByName($name) {
    $cond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::NameProperty, $name)
    return $island.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $cond)
}
function CenterOf($el) {
    $r = $el.Current.BoundingRectangle
    return @([int]($r.X + $r.Width / 2), [int]($r.Y + $r.Height / 2))
}
function Drag($fromX, $fromY, $toX, $toY, $steps = 24) {
    [Native]::SetCursorPos($fromX, $fromY) | Out-Null
    Start-Sleep -Milliseconds 220
    [Native]::mouse_event([Native]::LEFTDOWN, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 140
    for ($i = 1; $i -le $steps; $i++) {
        $x = [int]($fromX + ($toX - $fromX) * $i / $steps)
        $y = [int]($fromY + ($toY - $fromY) * $i / $steps)
        [Native]::SetCursorPos($x, $y) | Out-Null
        Start-Sleep -Milliseconds 20
    }
    Start-Sleep -Milliseconds 80
    [Native]::mouse_event([Native]::LEFTUP, 0, 0, 0, [UIntPtr]::Zero)
}

$slide = FindByName "Island opacity"
if (-not $slide) { "settings not open, opening via Invoke"; $btn = FindByName "Island settings"; $p = $null; if ($btn.TryGetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern, [ref]$p)) { $p.Invoke() }; Start-Sleep -Milliseconds 800; $slide = FindByName "Island opacity" }
if (-not $slide) { "slider still missing"; exit 1 }
"Slider rect: $($slide.Current.BoundingRectangle)"

$r = $slide.Current.BoundingRectangle
$y = [int]($r.Y + $r.Height / 2)
$fromX = [int]($r.X + $r.Width * 0.82)
$toX = [int]($r.X + $r.Width * 0.30)
"Drag $fromX -> $toX at y=$y"
Drag $fromX $y $toX $y
Start-Sleep -Milliseconds 600

$cond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::ControlTypeProperty, [System.Windows.Automation.ControlType]::Text)
foreach ($t in $island.FindAll([System.Windows.Automation.TreeScope]::Descendants, $cond)) {
    if ($t.Current.Name -match '%') { "Label: '$($t.Current.Name)'" }
}
