param(
    [Parameter(Mandatory = $true)][string]$ManifestUrl,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if (-not $ManifestUrl.StartsWith('https://')) { throw 'ManifestUrl must start with https://' }
$root = Split-Path -Parent $PSScriptRoot
$strings = Join-Path $root 'app/src/main/res/values/strings.xml'
if (-not (Test-Path -LiteralPath $strings)) { throw "strings.xml not found: $strings" }

$text = [IO.File]::ReadAllText($strings, [Text.Encoding]::UTF8)
$pattern = '(<string name="update_manifest_url"[^>]*>)[^<]*(</string>)'
$escaped = [Security.SecurityElement]::Escape($ManifestUrl)
if (-not [regex]::IsMatch($text, $pattern)) { throw 'update_manifest_url string not found.' }
$updated = [regex]::Replace($text, $pattern, ('${1}' + $escaped + '${2}'))

Write-Host "old: " ([regex]::Match($text, $pattern).Value)
Write-Host "new: " ([regex]::Match($updated, $pattern).Value)
if ($DryRun) { Write-Host 'DryRun: no files changed.'; return }

$utf8 = New-Object System.Text.UTF8Encoding($false)
[IO.File]::WriteAllText($strings, $updated, $utf8)
Write-Host "updated $strings"

& (Join-Path $root 'build.ps1')
if ($LASTEXITCODE -ne 0) { throw "build.ps1 failed: $LASTEXITCODE" }
