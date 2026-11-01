Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
$root = [System.Windows.Automation.AutomationElement]::RootElement
$main = $null
foreach ($w in $root.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)) {
    try { if ($w.Current.Name -like "*Velvet*" -and $w.Current.BoundingRectangle.Width -gt 520) { $main = $w } } catch {}
}
foreach ($b in $main.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
    try {
        if ($b.Current.ControlType -eq [System.Windows.Automation.ControlType]::Button) {
            "{0} :: {1}" -f $b.Current.Name, $b.Current.BoundingRectangle
        }
    } catch {}
}
