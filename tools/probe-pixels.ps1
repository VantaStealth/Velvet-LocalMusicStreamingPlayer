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
# window-relative probes; row y=140 abs -> window y=112
foreach ($y in @(100, 120, 140, 470, 500)) {
    $out = "y=$y : "
    foreach ($x in @(1000, 1020, 1040, 1060, 1100, 1150, 1200, 1250, 1300, 1350, 1400)) {
        $c = $bmp.GetPixel($x, $y)
        $out += "($x) #$('{0:X2}{1:X2}{2:X2}' -f $c.R,$c.G,$c.B) "
    }
    $out
}
$g.Dispose(); $bmp.Dispose()
