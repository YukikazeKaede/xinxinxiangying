param(
    [string]$Apk = '',
    [Parameter(Mandatory = $true)][string]$Url,
    [string]$Notes = '',
    [string]$NotesFile = '',
    [string]$OutFile = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
if (-not $Url.StartsWith('https://')) { throw 'Url must start with https://' }
if ($NotesFile) {
    if (-not (Test-Path -LiteralPath $NotesFile)) { throw "Notes file not found: $NotesFile" }
    $Notes = [IO.File]::ReadAllText($NotesFile, [Text.Encoding]::UTF8).TrimEnd()
}

if (-not $Apk) {
    $candidate = Get-ChildItem -LiteralPath (Join-Path $root 'dist') -Filter '*.apk' -File |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $candidate) { throw 'No APK found in dist; pass -Apk.' }
    $Apk = $candidate.FullName
}
if (-not (Test-Path -LiteralPath $Apk)) { throw "APK not found: $Apk" }

$aapt2 = Join-Path $root 'toolchain/sdk/android-16/aapt2.exe'
if (-not (Test-Path -LiteralPath $aapt2)) { throw "aapt2 not found: $aapt2" }
$badging = & $aapt2 dump badging $Apk 2>&1
$package = $badging | Where-Object { $_ -match '^package:' } | Select-Object -First 1
if (-not $package) { throw 'Cannot read package info from APK.' }
$code = [int]([regex]::Match($package, "versionCode='(\d+)'").Groups[1].Value)
$name = [regex]::Match($package, "versionName='([^']+)'").Groups[1].Value
if ($code -le 0 -or -not $name) { throw 'Cannot read versionCode/versionName.' }

$hash = (Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash.ToLowerInvariant()
$size = (Get-Item -LiteralPath $Apk).Length
$manifest = [ordered]@{
    versionCode = $code
    versionName = $name
    url = $Url
    sha256 = $hash
    size = $size
    notes = $Notes
}
$json = $manifest | ConvertTo-Json -Depth 4

if (-not $OutFile) { $OutFile = Join-Path $root 'update.json' }
$dir = Split-Path -Parent $OutFile
if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
[IO.File]::WriteAllText($OutFile, $json + "`n", [Text.UTF8Encoding]::new($false))

Write-Host "APK: $Apk"
Write-Host "versionCode: $code  versionName: $name  size: $size"
Write-Host "sha256: $hash"
Write-Host "manifest: $OutFile"
Get-Content -LiteralPath $OutFile -Raw
