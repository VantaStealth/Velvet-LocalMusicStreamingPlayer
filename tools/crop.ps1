param([string]$x="779", [string]$y="0", [string]$w="362", [string]$h="105", [string]$name="zoom", [int]$scale=4, [string]$outDir="C:\Users\Umar\Desktop\Anti\Spotify Premium\tools\shots")
Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class D { [DllImport("user32.dll")] public static extern bool SetProcessDPIAware(); }
"@
[D]::SetProcessDPIAware() | Out-Null
$bmp = New-Object System.Drawing.Bitmap([int]$w, [int]$h)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen([int]$x, [int]$y, 0, 0, (New-Object System.Drawing.Size([int]$w, [int]$h)))
$big = New-Object System.Drawing.Bitmap($bmp, ([int]$w * $scale), ([int]$h * $scale))
$big.Save("$outDir\$name.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose(); $big.Dispose()
"saved $outDir\$name.png"
