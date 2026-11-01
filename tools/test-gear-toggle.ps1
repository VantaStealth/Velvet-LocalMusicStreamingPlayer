Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
$root = [System.Windows.Automation.AutomationElement]::RootElement
function Island() {
    foreach ($w in $root.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)) {
        try { if ($w.Current.Name -eq 'Velvet Island') { return $w } } catch {}
    }
}
$island = Island
"Island start: $($island.Current.BoundingRectangle)"
$cond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::NameProperty, 'Island settings')
$gear = $island.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $cond)
$p = $null
"If TryGetPattern: " + $gear.TryGetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern, [ref]$p)
$p.Invoke()
for ($i = 1; $i -le 8; $i++) {
    Start-Sleep -Milliseconds 250
    "  t=$($i*250)ms size=$($island.Current.BoundingRectangle)"
}
$sliderCond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::NameProperty, 'Island opacity')
"Slider present: " + [bool]$island.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $sliderCond)
