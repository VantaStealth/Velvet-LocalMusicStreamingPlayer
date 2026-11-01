param([string]$src, [string]$x, [string]$y, [string]$w, [string]$h, [string]$name, [int]$scale = 3)
Add-Type -AssemblyName System.Drawing
$orig = [System.Drawing.Image]::FromFile($src)
$rect = New-Object System.Drawing.Rectangle([int]$x, [int]$y, [int]$w, [int]$h)
$bmp = New-Object System.Drawing.Bitmap([int]$w, [int]$h)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.DrawImage($orig, (New-Object System.Drawing.Rectangle(0, 0, [int]$w, [int]$h)), $rect, [System.Drawing.GraphicsUnit]::Pixel)
$big = New-Object System.Drawing.Bitmap($bmp, ([int]$w * $scale), ([int]$h * $scale))
$big.Save("C:\Users\Umar\Desktop\Anti\Spotify Premium\tools\shots\$name.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $bmp.Dispose(); $big.Dispose(); $orig.Dispose()
"saved $name"
