Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class S {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extra);
    [DllImport("user32.dll")] public static extern bool GetCursorPos(out POINT p);
    public struct POINT { public int X; public int Y; }
}
"@
[S]::SetProcessDPIAware() | Out-Null

$root = [System.Windows.Automation.AutomationElement]::RootElement
$main = $null
foreach ($w in $root.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)) {
    try { if ($w.Current.Name -like "*Velvet*" -and $w.Current.BoundingRectangle.Width -gt 520) { $main = $w } } catch {}
}
if (-not $main) { "main window not found"; exit 1 }
"Main: '$($main.Current.Name)' $($main.Current.BoundingRectangle)"

$buttons = @()
foreach ($b in $main.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
    try { if ($b.Current.ControlType -eq [System.Windows.Automation.ControlType]::Button) { $buttons += $b } } catch {}
}
"Main buttons: " + ($buttons | ForEach-Object { "'$($_.Current.Name)'" }) -join ", "
$min = $buttons | Where-Object { $_.Current.Name -match 'inimi|min' } | Select-Object -First 1
if (-not $min) { "no minimize button"; exit 1 }
$r = $min.Current.BoundingRectangle
$x = [int]($r.X + $r.Width / 2); $y = [int]($r.Y + $r.Height / 2)
"Synthetic click on '$($min.Current.Name)' at ($x,$y)"
[S]::SetCursorPos($x, $y) | Out-Null
Start-Sleep -Milliseconds 250
$c = New-Object S+POINT; [S]::GetCursorPos([ref]$c) | Out-Null; "cursor now ($($c.X),$($c.Y))"
[S]::mouse_event(2, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 90
[S]::mouse_event(4, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 700
"Main rect after: $($main.Current.BoundingRectangle) offscreen=$($main.Current.IsOffscreen)"

# restore
$p = $null
if ($main.TryGetCurrentPattern([System.Windows.Automation.WindowPattern]::Pattern, [ref]$p)) {
    $p.SetWindowVisualState([System.Windows.Automation.WindowVisualState]::Normal)
    "Restored via WindowPattern"
}
