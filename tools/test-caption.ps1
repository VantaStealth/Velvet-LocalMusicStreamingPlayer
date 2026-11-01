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
$main = $null
foreach ($w in $root.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)) {
    try { if ($w.Current.Name -like "*Velvet*" -and $w.Current.BoundingRectangle.Width -gt 520) { $main = $w } } catch {}
}
if (-not $main) { "main window not found"; exit 1 }
"rect: $($main.Current.BoundingRectangle)"

function Click-Button([string]$pattern) {
    $btn = $null
    foreach ($b in $main.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
        try { if ($b.Current.ControlType -eq [System.Windows.Automation.ControlType]::Button -and $b.Current.Name -match $pattern) { $btn = $b; break } } catch {}
    }
    if (-not $btn) { "  button '$pattern' not found"; return }
    $r = $btn.Current.BoundingRectangle
    [S]::SetCursorPos([int]($r.X + $r.Width / 2), [int]($r.Y + $r.Height / 2)) | Out-Null
    Start-Sleep -Milliseconds 150
    [S]::mouse_event(2, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 70
    [S]::mouse_event(4, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 900
    "  clicked '$($btn.Current.Name)' -> rect: $($main.Current.BoundingRectangle) offscreen=$($main.Current.IsOffscreen)"
}

"Minimize:"; Click-Button 'inimi'
$p = $null
if ($main.TryGetCurrentPattern([System.Windows.Automation.WindowPattern]::Pattern, [ref]$p)) {
    $p.SetWindowVisualState([System.Windows.Automation.WindowVisualState]::Normal)
    Start-Sleep -Milliseconds 700
    "  restored rect: $($main.Current.BoundingRectangle)"
}
"Maximize:"; Click-Button 'aximi'
"Restore:"; Click-Button 'aximi'
"Close (last):"; Click-Button 'lose'
Start-Sleep -Milliseconds 500
"processes: " + ((Get-Process Velvet -ErrorAction SilentlyContinue | Measure-Object).Count)
