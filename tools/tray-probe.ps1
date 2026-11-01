Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
$exe = "C:\Users\Umar\Desktop\Anti\Spotify Premium\desktop\Velvet.Desktop\bin\Release\net8.0-windows10.0.19041.0\win-x64\Velvet.exe"
$p = Start-Process -FilePath $exe -PassThru
Start-Sleep -Seconds 7

$root = [System.Windows.Automation.AutomationElement]::RootElement
$nameCond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::NameProperty, "Velvet")
$found = $root.FindAll([System.Windows.Automation.TreeScope]::Descendants, $nameCond)
"matches: $($found.Count)"
foreach ($el in $found) {
    $c = $el.Current
    "  name=$($c.Name) type=$($c.ControlType.ProgrammaticName) class=$($c.ClassName) rect=$($c.BoundingRectangle) pid=$($c.ProcessId) offscreen=$($c.IsOffscreen)"
}
Stop-Process -Id $p.Id -Force
