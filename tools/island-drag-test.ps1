Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class Win32 {
  [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint d, UIntPtr e);
}
"@
[Win32]::SetProcessDPIAware() | Out-Null
$settings = "$env:LOCALAPPDATA\Velvet\settings.json"
$before = Select-String -Path $settings -Pattern '"IslandMinimalWidth"\s*:\s*([0-9.]+)' | ForEach-Object { $_.Matches[0].Groups[1].Value }
"before width setting: $before"
$exe = "C:\Users\Umar\Desktop\Anti\Spotify Premium\desktop\Velvet.Desktop\bin\Release\net8.0-windows10.0.19041.0\win-x64\Velvet.exe"
$p = Start-Process -FilePath $exe -PassThru
Start-Sleep -Seconds 6
$root = [System.Windows.Automation.AutomationElement]::RootElement
$cond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::ProcessIdProperty, $p.Id)
$wins = $root.FindAll([System.Windows.Automation.TreeScope]::Children, $cond)
$island = $null
foreach ($w in $wins) { if ($w.Current.Name -eq 'Velvet Island') { $island = $w } }
if ($island -eq $null) { "island window not found"; exit 1 }
$r = $island.Current.BoundingRectangle
"island before: $($r.X),$($r.Y) $($r.Width)x$($r.Height)"
$gx = [int]($r.X + $r.Width - 5)
$gy = [int]($r.Y + 15)
[Win32]::SetCursorPos($gx, $gy) | Out-Null
Start-Sleep -Milliseconds 200
[Win32]::mouse_event(0x0002, 0, 0, 0, [UIntPtr]::Zero)
for ($i = 1; $i -le 14; $i++) {
  [Win32]::SetCursorPos($gx + ($i * 8), $gy) | Out-Null
  Start-Sleep -Milliseconds 25
}
[Win32]::mouse_event(0x0004, 0, 0, 0, [UIntPtr]::Zero)
Start-Sleep -Milliseconds 500
$r2 = $island.Current.BoundingRectangle
"island after: $($r2.X),$($r2.Y) $($r2.Width)x$($r2.Height)"
$after = Select-String -Path $settings -Pattern '"IslandMinimalWidth"\s*:\s*([0-9.]+)' | ForEach-Object { $_.Matches[0].Groups[1].Value }
"after width setting: $after"
$bmp = New-Object System.Drawing.Bitmap([int]$r2.Width + 40, [int]$r2.Height + 40)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen([int]$r2.X - 20, [int]$r2.Y - 10, 0, 0, (New-Object System.Drawing.Size([int]$r2.Width + 40, [int]$r2.Height + 40)))
$bmp.Save("C:\Users\Umar\Desktop\Anti\Spotify Premium\tools\shots\island-resized.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
"pid=$($p.Id)"
