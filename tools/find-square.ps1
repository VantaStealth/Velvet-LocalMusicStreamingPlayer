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
$minX = 9999; $maxX = -1; $minY = 9999; $maxY = -1
for ($y = 60; $y -lt 1025; $y += 2) {
    for ($x = 1000; $x -lt 1600; $x += 2) {
        $c = $bmp.GetPixel($x, $y)
        if ($c.R -ge 16 -and $c.R -le 22 -and $c.G -ge 13 -and $c.G -le 19 -and $c.B -ge 20 -and $c.B -le 26) {
            if ($x -lt $minX) { $minX = $x }; if ($x -gt $maxX) { $maxX = $x }
            if ($y -lt $minY) { $minY = $y }; if ($y -gt $maxY) { $maxY = $y }
        }
    }
}
"bbox of #121017-ish: ($minX,$minY) - ($maxX,$maxY) size $($maxX-$minX)x$($maxY-$minY)"
$g.Dispose(); $bmp.Dispose()
