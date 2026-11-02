param([string]$Folder)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$shell = New-Object -ComObject Shell.Application
$namespace = $shell.Namespace($Folder)
$items = @()
$names = @{}
for ($i = 0; $i -lt 340; $i++) {
  $label = $namespace.GetDetailsOf($null, $i)
  if ($label) { $names[$label] = $i }
}
$titleIndex = $names['Title']; $artistIndex = $names['Contributing artists']
$albumIndex = $names['Album']; $lengthIndex = $names['Length']
Get-ChildItem -LiteralPath $Folder -File | Where-Object { $_.Extension -match '^\.(mp3|m4a|aac|flac|wav|ogg|oga|opus|wma)$' } | ForEach-Object {
  $item = $namespace.ParseName($_.Name)
  $length = if ($lengthIndex -ne $null) { $namespace.GetDetailsOf($item, $lengthIndex) } else { '' }
  $ms = 0
  if ($length -match '^(\d+):(\d{2})$') { $ms = ([int]$Matches[1] * 60 + [int]$Matches[2]) * 1000 }
  elseif ($length -match '^(\d+):(\d{2}):(\d{2})$') { $ms = ([int]$Matches[1] * 3600 + [int]$Matches[2] * 60 + [int]$Matches[3]) * 1000 }
  $items += [pscustomobject]@{
    name = $_.Name
    title = if ($titleIndex -ne $null) { $namespace.GetDetailsOf($item, $titleIndex) } else { '' }
    artist = if ($artistIndex -ne $null) { $namespace.GetDetailsOf($item, $artistIndex) } else { '' }
    album = if ($albumIndex -ne $null) { $namespace.GetDetailsOf($item, $albumIndex) } else { '' }
    durationMs = $ms
  }
}
$items | ConvertTo-Json -Compress
