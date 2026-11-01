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
function Label($island) {
    foreach ($t in $island.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
        try { if ($t.Current.Name -match '^\d+%$') { return $t.Current.Name } } catch {}
    }
    return "none"
}
function ClickAt($x, $y) {
    [S]::SetCursorPos($x, $y) | Out-Null; Start-Sleep -Milliseconds 240
    [S]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 110; [S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
}
function DragFrac($sr, $f1, $f2) {
    $y = [int]($sr.Y + $sr.Height/2)
    $x1 = [int]($sr.X + $sr.Width*$f1); $x2 = [int]($sr.X + $sr.Width*$f2)
    [S]::SetCursorPos($x1, $y) | Out-Null; Start-Sleep -Milliseconds 250
    [S]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 150
    for ($i=1; $i -le 26; $i++) { [S]::SetCursorPos([int]($x1 + ($x2-$x1)*$i/26), $y) | Out-Null; Start-Sleep -Milliseconds 20 }
    Start-Sleep -Milliseconds 100
    [S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
}
$island = Island
"window: $($island.Current.BoundingRectangle)"
if (-not (ByName $island 'Island opacity')) {
    $gear = ByName $island 'Island settings'
    $g = $gear.Current.BoundingRectangle
    ClickAt ([int]($g.X + $g.Width/2)) ([int]($g.Y + $g.Height/2))
    Start-Sleep -Milliseconds 900
    $island = Island
}
$slider = ByName $island 'Island opacity'
if (-not $slider) { "no slider after gear click - window: $($island.Current.BoundingRectangle)"; exit 1 }
$w = $island.Current.BoundingRectangle
$sr = $slider.Current.BoundingRectangle
"window=$w"
"slider=$sr  insideWindow=$($sr.X -ge $w.X -and $sr.Y -ge $w.Y -and ($sr.X+$sr.Width) -le ($w.X+$w.Width) -and ($sr.Y+$sr.Height) -le ($w.Y+$w.Height))"
"label before = $(Label $island)"
ClickAt ([int]($sr.X + $sr.Width*0.2)) ([int]($sr.Y + $sr.Height/2))
Start-Sleep -Milliseconds 700
$island = Island
"label after click 20% = $(Label $island)"
DragFrac $sr 0.2 0.85
Start-Sleep -Milliseconds 800
$island = Island
"label after drag to 85% = $(Label $island)"
