Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class S {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extra);
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
function ClickAt($x, $y) {
    [S]::SetCursorPos($x, $y) | Out-Null; Start-Sleep -Milliseconds 300
    [S]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 110; [S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
}
$island = Island
# make main window active
$main = $null
foreach ($w in $root.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)) {
    try { if ($w.Current.Name -eq 'Velvet') { $main = $w } } catch {}
}
$mp = $null
if ($main -and $main.TryGetCurrentPattern([System.Windows.Automation.WindowPattern]::Pattern, [ref]$mp)) { $mp.SetWindowVisualState('Normal') }
if ($main) { $main.SetFocus() }
Start-Sleep -Milliseconds 500

$slider = ByName $island 'Island opacity'
if (-not $slider) {
    $gear = ByName $island 'Island settings'
    $p = $null; $gear.TryGetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern, [ref]$p) | Out-Null; $p.Invoke()
    Start-Sleep -Milliseconds 800
    $slider = ByName $island 'Island opacity'
}
$sr = $slider.Current.BoundingRectangle
$ty = [int]($sr.Y + $sr.Height/2)
"Slider: $sr"
foreach ($i in 1..3) {
    $x = [int]($sr.X + $sr.Width * (0.25 * $i))
    "click $i at ($x,$ty)"
    ClickAt $x $ty
    Start-Sleep -Milliseconds 900
    $label = "none"
    foreach ($t in $island.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
        try { if ($t.Current.Name -match '^\d+%$') { $label = $t.Current.Name } } catch {}
    }
    "  label=$label"
}
