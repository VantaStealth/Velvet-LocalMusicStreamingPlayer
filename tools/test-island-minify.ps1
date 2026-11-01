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
$island = $null
foreach ($w in $root.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)) {
    try { if ($w.Current.Name -eq 'Velvet Island') { $island = $w } } catch {}
}
if (-not $island) { "island not found"; exit 1 }
"Island before: $($island.Current.BoundingRectangle)"

$cond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::NameProperty, 'Island minimal')
$btn = $island.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $cond)
if (-not $btn) { "minimal button missing (settings closed?)"; exit 1 }
$r = $btn.Current.BoundingRectangle
$x = [int]($r.X + $r.Width / 2); $y = [int]($r.Y + $r.Height / 2)
"Synthetic click on Island minimal at ($x,$y) rect $r"
[S]::SetCursorPos($x, $y) | Out-Null
Start-Sleep -Milliseconds 300
[S]::mouse_event(2, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 110
[S]::mouse_event(4, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 900
"Island after: $($island.Current.BoundingRectangle)"
