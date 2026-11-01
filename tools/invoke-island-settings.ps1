Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes

$root = [System.Windows.Automation.AutomationElement]::RootElement
$island = $null
$main = $null
$wins = $root.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)
foreach ($w in $wins) {
    try {
        if ($w.Current.Name -like "*Velvet*" -and $w.Current.BoundingRectangle.Width -lt 500) { $island = $w }
        elseif ($w.Current.Name -like "*Velvet*") { $main = $w }
    } catch {}
}
if (-not $island) { "island not found"; exit 1 }
"Island: $($island.Current.Name) $($island.Current.BoundingRectangle)"

$buttons = $island.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)
foreach ($b in $buttons) {
    try {
        $n = $b.Current.Name
        if ($n) { "  [$($b.Current.ControlType.ProgrammaticName)] '$n' $($b.Current.BoundingRectangle) offscreen=$($b.Current.IsOffscreen)" }
    } catch {}
}

$settings = $buttons | Where-Object { try { $_.Current.Name -eq "Island settings" } catch { $false } } | Select-Object -First 1
if (-not $settings) { "settings button not found"; exit 1 }
$pattern = $null
if ($settings.TryGetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern, [ref]$pattern)) {
    $pattern.Invoke()
    "Invoked settings button"
} else {
    "No InvokePattern on settings button"
}

Start-Sleep -Milliseconds 900

$after = $island.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)
"After invoke - island size: $($island.Current.BoundingRectangle)"
foreach ($b in $after) {
    try {
        $n = $b.Current.Name
        if ($n) { "  [$($b.Current.ControlType.ProgrammaticName)] '$n' $($b.Current.BoundingRectangle)" }
    } catch {}
}
