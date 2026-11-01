param()
Add-Type -AssemblyName System.Drawing

function New-RoundedPath([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

function New-VelvetBitmap([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear([System.Drawing.Color]::Transparent)

    $pad = [float]($size * 0.03)
    $box = [float]($size - 2 * $pad)
    $path = New-RoundedPath $pad $pad $box $box ([float]($size * 0.235))
    $rect = New-Object System.Drawing.RectangleF(0, 0, $size, $size)
    $fill = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect,
        [System.Drawing.Color]::FromArgb(255, 41, 28, 54),
        [System.Drawing.Color]::FromArgb(255, 7, 5, 11), 55.0)
    $g.FillPath($fill, $path)

    $rim = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(90, 255, 255, 255), [float][Math]::Max(1, $size * 0.008))
    $g.DrawPath($rim, $path)

    $ringWidth = [float][Math]::Max(1.2, $size * 0.035)
    $ring = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 233, 199, 124), $ringWidth)
    $ringRadius = [float]($size * 0.335)
    $cx = [float]($size / 2)
    $g.DrawEllipse($ring, $cx - $ringRadius, $cx - $ringRadius, $ringRadius * 2, $ringRadius * 2)

    $vSize = [float]($size * 0.46)
    $serif = New-Object System.Drawing.Font("Georgia", $vSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $gold = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 243, 214, 154))
    $boxRect = New-Object System.Drawing.RectangleF(0, [float]($size * 0.02), $size, $size)
    $g.DrawString("V", $serif, $gold, $boxRect, $format)

    $dot = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 233, 90, 74))
    $dotSize = [float][Math]::Max(1.5, $size * 0.045)
    $g.FillEllipse($dot, $cx - $dotSize / 2, [float]($size * 0.795), $dotSize, $dotSize)

    $g.Dispose()
    return $bmp
}

function New-TrayBitmap([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear([System.Drawing.Color]::Transparent)

    $white = [System.Drawing.Color]::FromArgb(255, 255, 255, 255)
    $ringWidth = [float][Math]::Max(1.0, $size * 0.075)
    $ring = New-Object System.Drawing.Pen($white, $ringWidth)
    $inset = [float]($size * 0.09)
    $g.DrawEllipse($ring, $inset, $inset, [float]($size - 2 * $inset), [float]($size - 2 * $inset))

    $vSize = [float]($size * 0.5)
    $serif = New-Object System.Drawing.Font("Georgia", $vSize, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $format = New-Object System.Drawing.StringFormat
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $format.LineAlignment = [System.Drawing.StringAlignment]::Center
    $brush = New-Object System.Drawing.SolidBrush($white)
    $boxRect = New-Object System.Drawing.RectangleF(0, [float]($size * 0.03), $size, $size)
    $g.DrawString("V", $serif, $brush, $boxRect, $format)

    $g.Dispose()
    return $bmp
}

function Write-Ico([string]$file, [int[]]$sizes, [scriptblock]$factory) {
    $images = @()
    foreach ($size in $sizes) {
        $bmp = & $factory $size
        $ms = New-Object System.IO.MemoryStream
        $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
        $images += ,@($size, $ms.ToArray())
        $bmp.Dispose()
    }

    $stream = New-Object System.IO.MemoryStream
    $writer = New-Object System.IO.BinaryWriter($stream)
    $writer.Write([uint16]0)
    $writer.Write([uint16]1)
    $writer.Write([uint16]$images.Count)
    $offset = 6 + 16 * $images.Count
    foreach ($image in $images) {
        $size = $image[0]
        $data = $image[1]
        $dim = if ($size -ge 256) { 0 } else { $size }
        $writer.Write([byte]$dim)
        $writer.Write([byte]$dim)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([uint16]1)
        $writer.Write([uint16]32)
        $writer.Write([uint32]$data.Length)
        $writer.Write([uint32]$offset)
        $offset += $data.Length
    }
    foreach ($image in $images) { $writer.Write($image[1]) }
    $writer.Flush()
    [System.IO.File]::WriteAllBytes($file, $stream.ToArray())
    $writer.Dispose()
}

$assets = "C:\Users\Umar\Desktop\Anti\Spotify Premium\desktop\Velvet.Desktop\Assets"
Write-Ico "$assets\velvet.ico" @(256, 64, 48, 32, 20, 16) ${function:New-VelvetBitmap}
Write-Ico "$assets\velvet-tray.ico" @(64, 32, 24, 20, 16) ${function:New-TrayBitmap}

New-VelvetBitmap 256 | ForEach-Object {
    $_.Save("C:\Users\Umar\Desktop\Anti\Spotify Premium\tools\shots\icon-preview.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $_.Dispose()
}
New-TrayBitmap 64 | ForEach-Object {
    $_.Save("C:\Users\Umar\Desktop\Anti\Spotify Premium\tools\shots\icon-tray-preview.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $_.Dispose()
}
"icons written"
