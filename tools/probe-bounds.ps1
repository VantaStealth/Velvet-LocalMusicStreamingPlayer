Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class D { [DllImport("user32.dll")] public static extern bool SetProcessDPIAware(); }
"@
[D]::SetProcessDPIAware() | Out-Null
$bmp = New-Object System.Drawing.Bitmap(1600, 1025)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.CopyFromScreen(160, 28, 0, 0, (New-Object System.Drawing.Size(1600, 1025)))
$row = ""
foreach ($x in 1160..1580) {
    if (($x % 10) -ne 0) { continue }
    $c = $bmp.GetPixel($x, 140)
    $row += "$x=#$('{0:X2}{1:X2}{2:X2}' -f $c.R,$c.G,$c.B) "
}
"row y=140: $row"
$col = ""
foreach ($y in (90..540)) {
    if (($y % 10) -ne 0) { continue }
    $c = $bmp.GetPixel(1400, $y)
    $col += "$y=#$('{0:X2}{1:X2}{2:X2}' -f $c.R,$c.G,$c.B) "
}
"col x=1400: $col"
$g.Dispose(); $bmp.Dispose()
