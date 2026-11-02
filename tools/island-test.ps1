Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Dpi { [DllImport("user32.dll")] public static extern bool SetProcessDPIAware(); }
"@
[Dpi]::SetProcessDPIAware() | Out-Null
$exe = "C:\Users\Umar\Desktop\Anti\Spotify Premium\desktop\Velvet.Desktop\bin\Release\net8.0-windows10.0.19041.0\win-x64\Velvet.exe"
$p = Start-Process -FilePath $exe -PassThru
Start-Sleep -Seconds 7
$root = [System.Windows.Automation.AutomationElement]::RootElement
$cond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::ProcessIdProperty, $p.Id)
$wins = $root.FindAll([System.Windows.Automation.TreeScope]::Children, $cond)
"windows: $($wins.Count)"
foreach ($win in $wins) {
  "win: name='$($win.Current.Name)' class='$($win.Current.ClassName)' rect=$($win.Current.BoundingRectangle)"
  $all = $win.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)
  foreach ($el in $all) {
    if ($el.Current.Name -or $el.Current.AutomationId) {
      "  el: name='$($el.Current.Name)' id='$($el.Current.AutomationId)' type=$($el.Current.ControlType.ProgrammaticName)"
    }
  }
}
"pid=$($p.Id)"
