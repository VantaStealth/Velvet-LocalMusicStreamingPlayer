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
    [S]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 55; [S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
    Start-Sleep -Milliseconds 120
    [S]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 55; [S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
}
function ClickAt($x, $y) {
    [S]::SetCursorPos($x, $y) | Out-Null; Start-Sleep -Milliseconds 250
    [S]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 110; [S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
}
function Drag($x1, $y1, $x2, $y2, $steps = 28) {
    [S]::SetCursorPos($x1, $y1) | Out-Null; Start-Sleep -Milliseconds 260
    [S]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 160
    for ($i = 1; $i -le $steps; $i++) {
        [S]::SetCursorPos([int]($x1 + ($x2-$x1)*$i/$steps), [int]($y1 + ($y2-$y1)*$i/$steps)) | Out-Null
        Start-Sleep -Milliseconds 22
    }
    Start-Sleep -Milliseconds 120
    [S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
}
function PercentLabel($island) {
    foreach ($t in $island.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
        try { if ($t.Current.Name -match '^\d+%$') { return $t.Current.Name } } catch {}
    }
    return "none"
}
function EnsureExpanded() {
    $island = Island
    if ($island.Current.BoundingRectangle.Height -lt 60) {
        $r = $island.Current.BoundingRectangle
        DblClick ([int]($r.X + $r.Width/2)) ([int]($r.Y + $r.Height/2))
        Start-Sleep -Milliseconds 900
        $island = Island
    }
    return $island
}

$island = EnsureExpanded
"Island expanded: $($island.Current.BoundingRectangle)"
if (-not (ByName $island 'Island opacity')) {
    InvokeBtn (ByName $island 'Island settings') | Out-Null
    Start-Sleep -Milliseconds 800
    $island = Island
}
"Settings: $($island.Current.BoundingRectangle) label=$(PercentLabel $island)"

$slider = ByName $island 'Island opacity'
$sr = $slider.Current.BoundingRectangle
$ty = [int]($sr.Y + $sr.Height/2)

# 1. single click at 20%
ClickAt ([int]($sr.X + $sr.Width*0.2)) $ty
Start-Sleep -Milliseconds 500
$island = Island
"CLICK 20%  -> label=$(PercentLabel $island)"

# 2. drag 20% -> 80%
Drag ([int]($sr.X + $sr.Width*0.2)) $ty ([int]($sr.X + $sr.Width*0.8)) $ty
Start-Sleep -Milliseconds 700
$island = Island
"DRAG 80%   -> label=$(PercentLabel $island)"

# 3. grip drag down-right
$grip = ByName $island 'Resize island'
if ($grip) {
    $gr = $grip.Current.BoundingRectangle
    $before = (Island).Current.BoundingRectangle
    Drag ([int]($gr.X + $gr.Width/2)) ([int]($gr.Y + $gr.Height/2)) ([int]($gr.X + $gr.Width/2 + 70)) ([int]($gr.Y + $gr.Height/2 + 45))
    Start-Sleep -Milliseconds 800
    $after = (Island).Current.BoundingRectangle
    "GRIP       -> $before  =>  $after"
} else { "grip not found by name" }

# 4. minimal toggle
$island = Island
$r = $island.Current.BoundingRectangle
DblClick ([int]($r.X + $r.Width/2)) ([int]($r.Y + 20))
Start-Sleep -Milliseconds 900
$island = Island
"MINIMAL    -> $($island.Current.BoundingRectangle)"
$r = $island.Current.BoundingRectangle
DblClick ([int]($r.X + $r.Width/2)) ([int]($r.Y + $r.Height/2))
Start-Sleep -Milliseconds 900
$island = Island
"EXPANDED   -> $($island.Current.BoundingRectangle)"

# 5. drag body
$before = $island.Current.BoundingRectangle
Drag ([int]($before.X + 60)) ([int]($before.Y + 30)) ([int]($before.X + 160)) ([int]($before.Y + 90))
Start-Sleep -Milliseconds 800
$island = Island
"BODY DRAG  -> $before => $($island.Current.BoundingRectangle)"
