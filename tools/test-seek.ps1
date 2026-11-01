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

$sliders = @()
foreach ($s in $main.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
    try { if ($s.Current.ControlType -eq [System.Windows.Automation.ControlType]::Slider) { $sliders += $s } } catch {}
}
"Sliders: " + (($sliders | ForEach-Object { "'$($_.Current.Name)' $($_.Current.BoundingRectangle)" }) -join " | ")

$seek = $sliders | Where-Object { $_.Current.Name -match 'seek|progress|timeline' } | Select-Object -First 1
if (-not $seek) { $seek = $sliders | Where-Object { $_.Current.BoundingRectangle.Width -gt 300 } | Select-Object -First 1 }
if (-not $seek) { "no seek slider"; exit 1 }

function Read-Seek {
    $v = $null
    if ($seek.TryGetCurrentPattern([System.Windows.Automation.RangeValuePattern]::Pattern, [ref]$v)) {
        return [math]::Round($v.Current.Value, 4)
    }
    return -1
}

$r = $seek.Current.BoundingRectangle
"Seek rect: $r  value before: $(Read-Seek)"
$x = [int]($r.X + $r.Width * 0.62)
$y = [int]($r.Y + $r.Height / 2)
"Clicking at ($x,$y)"
[S]::SetCursorPos($x, $y) | Out-Null
Start-Sleep -Milliseconds 200
[S]::mouse_event(2, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 80
[S]::mouse_event(4, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 300
"after 0.3s: $(Read-Seek)"
Start-Sleep -Milliseconds 1500
"after 1.8s: $(Read-Seek)"
Start-Sleep -Milliseconds 3000
"after 4.8s: $(Read-Seek)"

