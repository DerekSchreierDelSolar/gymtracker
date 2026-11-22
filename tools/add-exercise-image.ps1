# Übungsbild hinzufügen: verkleinert ein Bild auf 512 × 512 (JPEG, Qualität 85) und legt es unter dem
# richtigen Namen in app/src/main/assets/exercises/ ab. Danach `node web/tools/gen-data.mjs` ausführen.
#
# Aufruf (PowerShell, im Projektordner):
#   .\tools\add-exercise-image.ps1 -Source "C:\Pfad\bild.png" -Exercise "Bankdrücken (Langhantel)"
#   .\tools\add-exercise-image.ps1 -Source "C:\Pfad\bild.png" -Exercise "Bankdrücken"   # Grundübung = alle Varianten
#
# Nicht quadratische Bilder werden mittig quadratisch zugeschnitten.

param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$Exercise
)

Add-Type -AssemblyName System.Drawing

# Gleicher Schlüssel wie exerciseImageKey() in ExerciseImages.kt / pictograms.js
# Umlaute als Zeichencodes: Windows PowerShell 5.1 liest Skripte ohne BOM nicht als UTF-8
$key = $Exercise.ToLower().Replace([string][char]0xE4, 'ae').Replace([string][char]0xF6, 'oe').Replace([string][char]0xFC, 'ue').Replace([string][char]0xDF, 'ss')
$key = ($key -replace '[^a-z0-9]+', '-').Trim('-')

$dir = Join-Path $PSScriptRoot '..\app\src\main\assets\exercises'
New-Item -ItemType Directory -Force $dir | Out-Null
$target = Join-Path $dir "$key.jpg"

$srcPath = (Resolve-Path -LiteralPath $Source).Path
if ([IO.Path]::GetExtension($srcPath) -eq '.webp') {
    # System.Drawing kann kein WebP: ueber WPF (Windows-Codec) dekodieren und als PNG im Speicher weitergeben
    Add-Type -AssemblyName PresentationCore
    $fs = [IO.File]::OpenRead($srcPath)
    try {
        $dec = [System.Windows.Media.Imaging.BitmapDecoder]::Create($fs, 'None', 'OnLoad')
        $enc = New-Object System.Windows.Media.Imaging.PngBitmapEncoder
        $enc.Frames.Add($dec.Frames[0])
        $ms = New-Object IO.MemoryStream
        $enc.Save($ms)
    } finally { $fs.Close() }
    $img = [System.Drawing.Image]::FromStream($ms)
} else {
    $img = [System.Drawing.Image]::FromFile($srcPath)
}
try {
    $side = [Math]::Min($img.Width, $img.Height)
    $srcRect = New-Object System.Drawing.Rectangle ([int](($img.Width - $side) / 2)), ([int](($img.Height - $side) / 2)), $side, $side
    $bmp = New-Object System.Drawing.Bitmap 512, 512
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($img, (New-Object System.Drawing.Rectangle 0, 0, 512, 512), $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
    $codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
    $p = New-Object System.Drawing.Imaging.EncoderParameters 1
    $p.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality), 85L
    $bmp.Save($target, $codec, $p)
    $g.Dispose(); $bmp.Dispose()
} finally {
    $img.Dispose()
}

$kb = [Math]::Round((Get-Item $target).Length / 1KB)
Write-Output "Gespeichert: assets/exercises/$key.jpg ($kb KB)"
