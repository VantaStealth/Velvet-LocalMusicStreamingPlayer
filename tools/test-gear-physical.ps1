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
    [S]::SetCursorPos($x, $y) | Out-Null; Start-Sleep -Milliseconds 220
    [S]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 110; [S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
}
function Label($island) {
    foreach ($t in $island.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
        try { if ($t.Current.Name -match '^\d+%$') { return $t.Current.Name } } catch {}
    }
    return "none"
}
$island = Island
"Island: $($island.Current.BoundingRectangle)"
$gear = ByName $island 'Island settings'
$gr = $gear.Current.BoundingRectangle
"gear: $gr"
ClickAt ([int]($gr.X + $gr.Width/2)) ([int]($gr.Y + $gr.Height/2))
Start-Sleep -Milliseconds 900
$island = Island
"after physical gear click: $($island.Current.BoundingRectangle) slider=$(if (ByName $island 'Island opacity') {'present'} else {'missing'})"
