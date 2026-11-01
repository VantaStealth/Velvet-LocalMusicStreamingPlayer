Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class S {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extra);
    public struct POINT { public int X; public int Y; }
}
"@
[S]::SetProcessDPIAware() | Out-Null

$root = [System.Windows.Automation.AutomationElement]::RootElement
$main = $null
foreach ($w in $root.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)) {
    try { if ($w.Current.Name -eq 'Velvet') { $main = $w } } catch {}
}
if (-not $main) { "main window not found"; exit 1 }
$mr = $main.Current.BoundingRectangle
"Main: $mr"

$top = @()
foreach ($b in $main.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
    try {
        if ($b.Current.ControlType -eq [System.Windows.Automation.ControlType]::Button) {
            $r = $b.Current.BoundingRectangle
            if ($r.Y -lt $mr.Y + 44) { $top += $b; "  btn '$($b.Current.Name)' $r" }
        }
    } catch {}
}
$min = $top | Sort-Object { $_.Current.BoundingRectangle.X } | Select-Object -First 1
$r = $min.Current.BoundingRectangle
$x = [int]($r.X + $r.Width / 2); $y = [int]($r.Y + $r.Height / 2)
"Click at ($x,$y)"
[S]::SetCursorPos($x, $y) | Out-Null
Start-Sleep -Milliseconds 250
[S]::mouse_event(2, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 90
[S]::mouse_event(4, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 800
"Main rect after click: $($main.Current.BoundingRectangle)"
$p = $null
if ($main.TryGetCurrentPattern([System.Windows.Automation.WindowPattern]::Pattern, [ref]$p)) {
    "state: $($p.Current.WindowVisualState)"
    $p.SetWindowVisualState([System.Windows.Automation.WindowVisualState]::Normal)
}
