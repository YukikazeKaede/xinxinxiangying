param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
# Approved exception: one pinned payment SDK, without Gradle or Maven tooling.
$sdkVersion = '15.8.42'
$sdkSha256 = 'a6c301708b59a3a7cda843cf02e7b3b0b44afd96819f0a18890322e9868abf28'
$sdkRoot = Join-Path $ProjectRoot ('toolchain/alipay/' + $sdkVersion)
$archivePath = Join-Path $sdkRoot ('alipaysdk-android-' + $sdkVersion + '.aar')
$sdkUrl = 'https://repo.maven.apache.org/maven2/com/alipay/sdk/alipaysdk-android/' + $sdkVersion + '/alipaysdk-android-' + $sdkVersion + '.aar'
New-Item -ItemType Directory -Path $sdkRoot -Force | Out-Null
if (-not (Test-Path -LiteralPath $archivePath)) {
    $temporaryPath = $archivePath + '.download'
    Invoke-WebRequest -UseBasicParsing -Uri $sdkUrl -OutFile $temporaryPath
    if ((Get-FileHash -LiteralPath $temporaryPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sdkSha256) {
        Remove-Item -LiteralPath $temporaryPath -Force
        throw 'Alipay SDK SHA-256 mismatch; SDK was not installed.'
    }
    Move-Item -LiteralPath $temporaryPath -Destination $archivePath -Force
}
if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sdkSha256) {
    throw 'Cached Alipay SDK SHA-256 mismatch; replace it from the official source.'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$resourcePath = Join-Path $sdkRoot 'res'
if (Test-Path -LiteralPath $resourcePath) { Remove-Item -LiteralPath $resourcePath -Recurse -Force }
$archive = [IO.Compression.ZipFile]::OpenRead($archivePath)
try {
    foreach ($entry in $archive.Entries) {
        $name = $entry.FullName.Replace('\', '/')
        if ($name -ne 'classes.jar' -and -not $name.StartsWith('res/')) { continue }
        if ($name.EndsWith('/')) { continue }
        $destination = [IO.Path]::GetFullPath((Join-Path $sdkRoot $name))
        if (-not $destination.StartsWith([IO.Path]::GetFullPath($sdkRoot) + [IO.Path]::DirectorySeparatorChar)) { throw 'Unsafe SDK archive entry.' }
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $destination, $true)
    }
} finally { $archive.Dispose() }
$classesPath = Join-Path $sdkRoot 'classes.jar'
if (-not (Test-Path -LiteralPath $classesPath) -or -not (Test-Path -LiteralPath $resourcePath)) { throw 'Incomplete Alipay SDK archive.' }
[PSCustomObject]@{ ClassesJar = $classesPath; Resources = $resourcePath }
