param([int]$x = 160, [int]$y = 28, [int]$w = 1600, [int]$h = 1025, [string]$name = "live", [int]$wait = 6)
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class ShotDpi { [DllImport("user32.dll")] public static extern bool SetProcessDPIAware(); }
"@
[ShotDpi]::SetProcessDPIAware() | Out-Null
$exe = "C:\Users\Umar\Desktop\Anti\Spotify Premium\desktop\Velvet.Desktop\bin\Release\net8.0-windows10.0.19041.0\win-x64\Velvet.exe"
$p = Start-Process -FilePath $exe -PassThru
Start-Sleep -Seconds $wait
$bmp = New-Object System.Drawing.Bitmap($w, $h)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($x, $y, 0, 0, (New-Object System.Drawing.Size($w, $h)))
$bmp.Save("C:\Users\Umar\Desktop\Anti\Spotify Premium\tools\shots\$name.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose()
Stop-Process -Id $p.Id -Force
"captured $name"
