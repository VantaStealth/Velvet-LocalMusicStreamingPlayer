Add-Type -AssemblyName UIAutomationClient
Add-Type -AssemblyName UIAutomationTypes
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.Windows.Forms
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class CaptureDpi {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extra);
}
"@
[CaptureDpi]::SetProcessDPIAware() | Out-Null

$exe = "C:\Users\Umar\Desktop\Anti\Spotify Premium\desktop\Velvet.Desktop\bin\Release\net8.0-windows10.0.19041.0\win-x64\Velvet.exe"
$rootDir = "C:\Users\Umar\Desktop\Anti\Spotify Premium"
$settingsPath = "C:\Users\Umar\AppData\Local\Velvet\settings.json"

Get-Process Velvet -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Milliseconds 800

$settings = Get-Content $settingsPath -Raw | ConvertFrom-Json
$settings.Disco = $false
$settings.IslandEnabled = $true
$settings.IslandMinimal = $false
$settings.IslandOpacity = 1
$settings.IslandVertical = $false
$settings.Shuffle = $false
$settings.LastTrackId = "C:\Users\Umar\Desktop\Music\Dua Lipa - Training Season (Official Music Video) [ZjBZ8MUnB0E].mp3"
$settings | ConvertTo-Json -Depth 6 | Set-Content $settingsPath -Encoding UTF8
"disco after write: $((Get-Content $settingsPath -Raw | ConvertFrom-Json).Disco)"

$root = [System.Windows.Automation.AutomationElement]::RootElement
function MainWindow() {
    foreach ($w in $root.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)) {
        try { if ($w.Current.Name -like "*Velvet*" -and $w.Current.BoundingRectangle.Width -gt 520) { return $w } } catch {}
    }
}
function IslandWindow() {
    foreach ($w in $root.FindAll([System.Windows.Automation.TreeScope]::Children, [System.Windows.Automation.Condition]::TrueCondition)) {
        try { if ($w.Current.Name -eq 'Velvet Island') { return $w } } catch {}
    }
}
function FindButton($scope, [string]$text) {
    foreach ($b in $scope.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
        try {
            if ($b.Current.ControlType -eq [System.Windows.Automation.ControlType]::Button -and
                $b.Current.Name -like "*$text*") { return $b }
        } catch {}
    }
}
function FindButtonByText($scope, [string]$text) {
    $walker = [System.Windows.Automation.TreeWalker]::ControlViewWalker
    foreach ($t in $scope.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
        try {
            if ($t.Current.Name -eq $text) {
                $node = $t
                while ($node) {
                    if ($node.Current.ControlType -eq [System.Windows.Automation.ControlType]::Button) { return $node }
                    $node = $walker.GetParent($node)
                }
            }
        } catch {}
    }
}
function InvokeBtn($b) {
    if (-not $b) { return $false }
    $p = $null
    if ($b.TryGetCurrentPattern([System.Windows.Automation.InvokePattern]::Pattern, [ref]$p)) {
        $p.Invoke()
        return $true
    }
    return $false
}
function DblClick($x, $y) {
    [CaptureDpi]::SetCursorPos($x, $y) | Out-Null; Start-Sleep -Milliseconds 220
    [CaptureDpi]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 55; [CaptureDpi]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
    Start-Sleep -Milliseconds 120
    [CaptureDpi]::mouse_event(2,0,0,0,[UIntPtr]::Zero); Start-Sleep -Milliseconds 55; [CaptureDpi]::mouse_event(4,0,0,0,[UIntPtr]::Zero)
}
function TimeSet($scope) {
    $out = @()
    foreach ($t in $scope.FindAll([System.Windows.Automation.TreeScope]::Descendants, [System.Windows.Automation.Condition]::TrueCondition)) {
        try { if ($t.Current.Name -match '^\d{1,2}:\d{2}$') { $out += $t.Current.Name } } catch {}
    }
    return ($out -join ',')
}
function WaitForPlayback([int]$maxSeconds = 60) {
    $main = MainWindow
    $prev = TimeSet $main
    for ($i = 0; $i -lt $maxSeconds / 2; $i++) {
        Start-Sleep -Seconds 2
        $main = MainWindow
        $cur = TimeSet $main
        if ($cur -ne $prev -and $cur) { return $true }
        $prev = $cur
    }
    return $false
}
function Capture([string]$file) {
    $bounds = [System.Windows.Forms.Screen]::PrimaryScreen.Bounds
    $bmp = New-Object System.Drawing.Bitmap($bounds.Width, $bounds.Height)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($bounds.X, $bounds.Y, 0, 0, (New-Object System.Drawing.Size($bounds.Width, $bounds.Height)))
    $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
}

$p = Start-Process -FilePath $exe -PassThru
Start-Sleep -Seconds 7

$main = MainWindow
if (-not $main) { throw "Velvet main window did not appear." }
$wp = $null
if ($main.TryGetCurrentPattern([System.Windows.Automation.WindowPattern]::Pattern, [ref]$wp)) {
    $wp.SetWindowVisualState([System.Windows.Automation.WindowVisualState]::Maximized)
}
Start-Sleep -Milliseconds 1500

$island = IslandWindow
if ($island) {
    $r = $island.Current.BoundingRectangle
    "island before: $r"
    if ($r.Height -lt 80) {
        DblClick ([int]($r.X + $r.Width/2)) ([int]($r.Y + $r.Height/2))
        Start-Sleep -Milliseconds 1200
        $island = IslandWindow
        "island expanded: $($island.Current.BoundingRectangle)"
    }
}

$main = MainWindow
$island = IslandWindow
$play = $null
if ($island) { $play = FindButton $island "Island play" }
if (-not $play) { $play = FindButton $island "play" }
"play control: $($play.Current.Name) $($play.Current.BoundingRectangle)"
InvokeBtn $play | Out-Null
Start-Sleep -Seconds 25
"warmup done, advancing: $(WaitForPlayback 90)"
Start-Sleep -Seconds 15

$main = MainWindow
$disco = FindButton $main "Disco mode"
"disco before normal: $($disco.Current.Name)"
if ($disco.Current.Name -like "*On*") {
    InvokeBtn $disco | Out-Null
    Start-Sleep -Seconds 2
    $main = MainWindow
    $disco = FindButton $main "Disco mode"
    "disco after reset: $($disco.Current.Name)"
}

Capture (Join-Path $rootDir "docs\velvet-desktop.png")
"captured normal"

$main = MainWindow
$disco = FindButton $main "Disco mode"
"disco button: $($disco.Current.Name)"
if ($disco.Current.Name -notlike "*On*") {
    InvokeBtn $disco | Out-Null
}
Start-Sleep -Seconds 8
"advancing before disco: $(WaitForPlayback 30)"

for ($i = 1; $i -le 3; $i++) {
    Capture (Join-Path $rootDir "docs\disco-candidate-$i.png")
    "captured disco $i"
    Start-Sleep -Milliseconds 700
}

Start-Sleep -Milliseconds 400
Stop-Process -Id $p.Id -Force
"done"
