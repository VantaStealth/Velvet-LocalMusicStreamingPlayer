Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class S {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extra);
}
"@
[S]::SetProcessDPIAware() | Out-Null
$root = [System.Windows.Automation.AutomationElement]::RootElement
$out = "C:\Users\Umar\Desktop\Anti\Spotify Premium\tools\shots"
New-Item -ItemType Directory -Force -Path $out | Out-Null

function Island() {
    foreach ($w in $root.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)) {
        try { if ($w.Current.Name -eq 'Velvet Island') { return $w } } catch {}
    }
}
function ByName($island, $name) {
    $cond = New-Object System.Windows.Automation.PropertyCondition([System.Windows.Automation.AutomationElement]::NameProperty, $name)
    return $island.FindFirst([System.Windows.Automation.TreeScope]::Descendants, $cond)
}
function InvokeBtn($b) { $p = $null; if ($b -and $b.TryGetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern, [ref]$p)) { $p.Invoke(); return $true }; return $false }
function Shot($name, $pad = 14) {
    $island = Island
    $r = $island.Current.BoundingRectangle
    $x = [int]([Math]::Max(0, $r.X - $pad)); $y = [int]([Math]::Max(0, $r.Y - $pad))
    $w = [int]($r.Width + $pad * 2); $h = [int]($r.Height + $pad * 2)
    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($x, $y, 0, 0, (New-Object System.Drawing.Size($w, $h)))
    $big = New-Object System.Drawing.Bitmap($bmp, ($w * 2), ($h * 2))
    $big.Save("$out\$name.png", [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose(); $big.Dispose()
    "shot $name : $x,$y ${w}x${h}"
}
function Label($island) {
    foreach ($t in $island.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
        try { if ($t.Current.Name -match '^\d+%$') { return $t.Current.Name } } catch {}
    }
    return "none"
}

$island = Island
"Island: $($island.Current.BoundingRectangle)"
Shot "01-expanded"

InvokeBtn (ByName $island 'Island settings') | Out-Null
Start-Sleep -Milliseconds 900
$island = Island
"settings: $($island.Current.BoundingRectangle) label=$(Label $island)"
Shot "02-settings"

$slider = ByName $island 'Island opacity'
$sr = $slider.Current.BoundingRectangle
$y = [int]($sr.Y + $sr.Height/2)
$x1 = [int]($sr.X + $sr.Width*0.15); $x2 = [int]($sr.X + $sr.Width*0.85)
"drag slider row $x1 -> $x2 at y=$y"
[S]::SetCursorPos($x1, $y) | Out-Null; Start-Sleep -Milliseconds 260
[S]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 150
for ($i=1; $i -le 26; $i++) { [S]::SetCursorPos([int]($x1 + ($x2-$x1)*$i/26), $y) | Out-Null; Start-Sleep -Milliseconds 20 }
Start-Sleep -Milliseconds 120
[S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
Start-Sleep -Milliseconds 700
$island = Island
"after drag: label=$(Label $island) opacity in file: $((Get-Content 'C:\Users\Umar\AppData\Local\Velvet\settings.json' | Select-String 'IslandOpacity'))"
Shot "03-after-drag"

InvokeBtn (ByName $island 'Island minimal') | Out-Null
Start-Sleep -Milliseconds 900
$island = Island
"minimal: $($island.Current.BoundingRectangle)"
Shot "04-minimal"

# double-click minimal to expand
$r = $island.Current.BoundingRectangle
$cx = [int]($r.X + $r.Width/2); $cy = [int]($r.Y + $r.Height/2)
[S]::SetCursorPos($cx, $cy) | Out-Null; Start-Sleep -Milliseconds 240
[S]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 55; [S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
Start-Sleep -Milliseconds 120
[S]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 55; [S]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
Start-Sleep -Milliseconds 900
$island = Island
"after dbl-click: $($island.Current.BoundingRectangle)"
Shot "05-restored"
