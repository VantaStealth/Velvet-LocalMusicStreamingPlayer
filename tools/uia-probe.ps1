Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Q { [DllImport("user32.dll")] public static extern bool SetProcessDPIAware(); }
"@
[Q]::SetProcessDPIAware() | Out-Null
$exe = "C:\Users\Umar\Desktop\Anti\Spotify Premium\desktop\Velvet.Desktop\bin\Release\net8.0-windows10.0.19041.0\win-x64\Velvet.exe"
$p = Start-Process -FilePath $exe -PassThru
Start-Sleep -Seconds 6
foreach ($pt in @(@(949, 145), @(955, 145), @(945, 140))) {
    $point = New-Object System.Windows.Point($pt[0], $pt[1])
    $el = [System.Windows.Automation.AutomationElement]::FromPoint($point)
    if ($el -eq $null) { "no element at $($pt[0]),$($pt[1])"; continue }
    $c = $el.Current
    "$($pt[0]),$($pt[1]) -> name='$($c.Name)' type=$($c.ControlType.ProgrammaticName) class=$($c.ClassName) rect=$($c.BoundingRectangle) app=$($c.ProcessId)"
    $walker = [System.Windows.Automation.TreeWalker]::ControlViewWalker
    $cur = $el
    for ($i = 0; $i -lt 6; $i++) {
        if ($cur -eq $null) { break }
        $cur = $walker.GetParent($cur)
        if ($cur -eq $null) { break }
        $cc = $cur.Current
        "   parent: name='$($cc.Name)' type=$($cc.ControlType.ProgrammaticName) class=$($cc.ClassName) rect=$($cc.BoundingRectangle)"
    }
}
Stop-Process -Id $p.Id -Force
