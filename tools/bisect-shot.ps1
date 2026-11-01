param([string]$variant = "base", [int]$x = 190, [int]$y = 300, [int]$w = 280, [int]$h = 110)
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class B { [DllImport("user32.dll")] public static extern bool SetProcessDPIAware(); }
"@
[B]::SetProcessDPIAware() | Out-Null
$exe = "C:\Users\Umar\Desktop\Anti\Spotify Premium\desktop\Velvet.Desktop\bin\Release\net8.0-windows10.0.19041.0\win-x64\Velvet.exe"
if ($variant -eq "base") { Remove-Item Env:VELVET_HIDE -ErrorAction SilentlyContinue }
else { $env:VELVET_HIDE = $variant }
$p = Start-Process -FilePath $exe -PassThru
Start-Sleep -Seconds 6
$bmp = New-Object System.Drawing.Bitmap($w, $h)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen($x, $y, 0, 0, (New-Object System.Drawing.Size($w, $h)))
$big = New-Object System.Drawing.Bitmap($bmp, ($w * 4), ($h * 4))
$big.Save("C:\Users\Umar\Desktop\Anti\Spotify Premium\tools\shots\bisect-$variant.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose(); $big.Dispose()
Stop-Process -Id $p.Id -Force
"captured $variant"
