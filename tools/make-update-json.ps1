param(
    [string]$Apk = '',
    [Parameter(Mandatory = $true)][string]$Url,
    [string]$Notes = '',
    [string]$NotesFile = '',
    [string]$OutFile = '',
    [switch]$AllAbis,
    [string[]]$Mirrors = @()
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$root = Split-Path -Parent $PSScriptRoot
if (-not $Url.StartsWith('https://')) { throw 'Url must start with https://' }
$Mirrors = @($Mirrors | ForEach-Object {
    $mirror = $_.Trim()
    $parsed = $null
    if (-not [Uri]::TryCreate($mirror, [UriKind]::Absolute, [ref]$parsed) -or $parsed.Scheme -ne 'https' -or -not $parsed.Host -or $parsed.UserInfo) { throw 'Mirrors must be absolute HTTPS URLs without credentials.' }
    if ($AllAbis -and -not $mirror.EndsWith('-arm64-v8a.apk')) { throw 'For -AllAbis, mirrors must end with -arm64-v8a.apk.' }
    $mirror
} | Select-Object -Unique)
if ($NotesFile) {
    if (-not (Test-Path -LiteralPath $NotesFile)) { throw "Notes file not found: $NotesFile" }
    $Notes = [IO.File]::ReadAllText($NotesFile, [Text.Encoding]::UTF8).TrimEnd()
}

if (-not $Apk) {
    $candidate = Get-ChildItem -LiteralPath (Join-Path $root 'dist') -Filter '*.apk' -File |
        Sort-Object @{ Expression = { if ($_.BaseName.EndsWith('-arm64-v8a')) { 0 } else { 1 } } }, @{ Expression = 'LastWriteTime'; Descending = $true } | Select-Object -First 1
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
if ($Mirrors.Count) { $manifest.mirrors = @($Mirrors) }
if ($AllAbis) {
    if (-not $Apk.EndsWith('-arm64-v8a.apk')) { throw 'For -AllAbis, -Apk must be the arm64-v8a APK.' }
    if (-not $Url.EndsWith('-arm64-v8a.apk')) { throw 'For -AllAbis, -Url must end with -arm64-v8a.apk.' }
    $filePrefix = $Apk.Substring(0, $Apk.Length - '-arm64-v8a.apk'.Length)
    $urlPrefix = $Url.Substring(0, $Url.Length - '-arm64-v8a.apk'.Length)
    $artifacts = [ordered]@{}
    foreach ($abi in @('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64')) {
        $file = $filePrefix + '-' + $abi + '.apk'
        if (-not (Test-Path -LiteralPath $file)) { throw "Missing architecture APK: $file" }
        $info = @(& $aapt2 dump badging $file 2>&1)
        $infoPackage = $info | Where-Object { $_ -match '^package:' } | Select-Object -First 1
        if (-not $infoPackage -or $infoPackage -notmatch ("versionCode='" + $code + "'") -or $infoPackage -notmatch ("versionName='" + [regex]::Escape($name) + "'")) {
            throw "Architecture APK has a different version: $file"
        }
        if (-not ($info -contains ("native-code: '" + $abi + "'"))) { throw "APK must contain exactly one expected ABI: $file" }
        $artifacts[$abi] = [ordered]@{
            url = $urlPrefix + '-' + $abi + '.apk'
            sha256 = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
            size = (Get-Item -LiteralPath $file).Length
        }
        if ($Mirrors.Count) {
            $artifacts[$abi].mirrors = @($Mirrors | ForEach-Object { $_.Substring(0, $_.Length - '-arm64-v8a.apk'.Length) + '-' + $abi + '.apk' })
        }
    }
    $manifest.artifacts = $artifacts
}
$json = $manifest | ConvertTo-Json -Depth 6

if (-not $OutFile) { $OutFile = Join-Path $root 'update.json' }
$dir = Split-Path -Parent $OutFile
if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
[IO.File]::WriteAllText($OutFile, $json + "`n", [Text.UTF8Encoding]::new($false))

Write-Host "APK: $Apk"
Write-Host "versionCode: $code  versionName: $name  size: $size"
Write-Host "sha256: $hash"
Write-Host "manifest: $OutFile"
Get-Content -LiteralPath $OutFile -Raw
