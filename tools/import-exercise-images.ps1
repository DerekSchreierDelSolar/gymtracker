# Mehrere Uebungsbilder auf einmal einspielen (Ablauf siehe docs/bild-prompts.md).
#
#   .\tools\import-exercise-images.ps1 -Folder "C:\Pfad\uebungsbilder"   # alle Bilder im Ordner
#   .\tools\import-exercise-images.ps1 -Status                            # Grunduebungen ohne Bild
#
# Dateiname = Bild-Schluessel, z. B. kniebeugen.png (Grunduebung, gilt fuer alle Varianten) oder
# kniebeugen-kurzhantel.png (nur diese Variante). Erlaubt: .png .jpg .jpeg (kein WebP - System.Drawing
# kann es nicht lesen, vorher als PNG speichern).
# Jedes Bild geht durch add-exercise-image.ps1 (512 x 512 JPEG). Danach: node web/tools/gen-data.mjs

param(
    [string]$Folder,
    [switch]$Status
)

# Gleicher Schluessel wie exerciseImageKey() in ExerciseImages.kt (Umlaute als Zeichencodes, siehe add-exercise-image.ps1)
function Get-ImageKey([string]$name) {
    $k = $name.ToLower().Replace([string][char]0xE4, 'ae').Replace([string][char]0xF6, 'oe').Replace([string][char]0xFC, 'ue').Replace([string][char]0xDF, 'ss')
    return ($k -replace '[^a-z0-9]+', '-').Trim('-')
}

$root = Join-Path $PSScriptRoot '..'
$catalog = Get-Content (Join-Path $root 'app\src\main\java\com\example\gymtracker\Catalog.kt') -Encoding UTF8 -Raw
$entries = [regex]::Matches($catalog, 'CatalogExercise\("([^"]+)",\s*"([^"]+)"')
$baseKeys = [ordered]@{}      # Schluessel -> Name der Grunduebung (Katalog-Reihenfolge)
$allKeys = @{}
foreach ($m in $entries) {
    $b = Get-ImageKey $m.Groups[2].Value
    if (-not $baseKeys.Contains($b)) { $baseKeys[$b] = $m.Groups[2].Value }
    $allKeys[$b] = $true
    $allKeys[(Get-ImageKey $m.Groups[1].Value)] = $true
}
$dir = Join-Path $root 'app\src\main\assets\exercises'

if ($Folder) {
    $files = Get-ChildItem $Folder -File | Where-Object { $_.Extension -match '^\.(png|jpe?g)$' }
    $ok = 0
    foreach ($f in $files) {
        $key = Get-ImageKey $f.BaseName
        if (-not $allKeys.ContainsKey($key)) {
            Write-Warning "Unbekannte Uebung, uebersprungen: $($f.Name)"
            continue
        }
        & (Join-Path $PSScriptRoot 'add-exercise-image.ps1') -Source $f.FullName -Exercise $key
        $ok++
    }
    Write-Output "$ok von $($files.Count) Bildern eingespielt. Jetzt: node web/tools/gen-data.mjs"
}

if ($Status -or -not $Folder) {
    $have = @{}
    if (Test-Path $dir) { Get-ChildItem $dir -Filter *.jpg | ForEach-Object { $have[$_.BaseName] = $true } }
    $missing = @($baseKeys.Keys | Where-Object { -not $have.ContainsKey($_) })
    Write-Output "Grunduebungen mit Bild: $($baseKeys.Count - $missing.Count) von $($baseKeys.Count)"
    foreach ($k in $missing) { Write-Output "  fehlt: $k.png  ($($baseKeys[$k]))" }
}
