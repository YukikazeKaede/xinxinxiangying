param(
    [string]$JavaHome = '',
    [string]$Keystore = '',
    [string]$KeyAlias = 'xinxin'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$project = $PSScriptRoot
$build = Join-Path $project 'build'
$dist = Join-Path $project 'dist'
$tools = Join-Path $project 'toolchain/sdk/android-16'
$androidJar = Join-Path $project 'toolchain/sdk/android-36/android.jar'
if (-not $JavaHome) {
    $candidate = Get-ChildItem -LiteralPath (Join-Path $project 'toolchain/jdk') -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin/javac.exe') } | Select-Object -First 1
    if ($candidate) { $JavaHome = $candidate.FullName }
    elseif ($env:JAVA_HOME) { $JavaHome = $env:JAVA_HOME }
}
function Invoke-Checked([string]$Program, [string[]]$Arguments) {
    & $Program @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Command failed ($LASTEXITCODE): $Program" }
}
function Write-AlignedZip([string]$InputPath, [string]$OutputPath) {
    # Preserve compressed bytes and CRCs, adding a valid private extra field
    # to each local header so every entry's data starts on a 4-byte boundary.
    $bytes = [IO.File]::ReadAllBytes($InputPath)
    $end = $bytes.Length - 22
    while ($end -ge 0 -and [BitConverter]::ToUInt32($bytes, $end) -ne 0x06054b50) { $end-- }
    if ($end -lt 0) { throw 'Invalid ZIP end record.' }
    $count = [BitConverter]::ToUInt16($bytes, $end + 10)
    $central = [BitConverter]::ToUInt32($bytes, $end + 16)
    $records = [Collections.Generic.List[byte[]]]::new()
    $stream = [IO.File]::Create($OutputPath)
    $writer = [IO.BinaryWriter]::new($stream)
    try {
        $cursor = [int]$central
        for ($i = 0; $i -lt $count; $i++) {
            if ([BitConverter]::ToUInt32($bytes, $cursor) -ne 0x02014b50) { throw 'Invalid ZIP central record.' }
            $nameLength = [BitConverter]::ToUInt16($bytes, $cursor + 28)
            $extraLength = [BitConverter]::ToUInt16($bytes, $cursor + 30)
            $commentLength = [BitConverter]::ToUInt16($bytes, $cursor + 32)
            $length = 46 + $nameLength + $extraLength + $commentLength
            $record = [byte[]]::new($length)
            [Array]::Copy($bytes, $cursor, $record, 0, $length)
            # Windows aapt2 can use backslashes inside nested asset names.
            # Android AssetManager requires ZIP paths with forward slashes.
            if ([Text.Encoding]::UTF8.GetString($record, 46, $nameLength).StartsWith('assets/')) {
                for ($n = 46; $n -lt 46 + $nameLength; $n++) {
                    if ($record[$n] -eq 92) { $record[$n] = 47 }
                }
            }
            $offset = [BitConverter]::ToUInt32($record, 42)
            $compressedSize = [BitConverter]::ToUInt32($record, 20)
            $localName = [BitConverter]::ToUInt16($bytes, $offset + 26)
            $localExtra = [BitConverter]::ToUInt16($bytes, $offset + 28)
            $headerLength = 30 + $localName + $localExtra
            $newOffset = [uint32]$stream.Position
            $padding = [int]((4 - (($stream.Position + $headerLength + 4) % 4)) % 4)
            $header = [byte[]]::new($headerLength)
            [Array]::Copy($bytes, $offset, $header, 0, $headerLength)
            [Array]::Copy($record, 46, $header, 30, $nameLength)
            $flags = [uint16]([BitConverter]::ToUInt16($header, 6) -band 0xfff7)
            [Array]::Copy([BitConverter]::GetBytes($flags), 0, $header, 6, 2)
            [Array]::Copy([BitConverter]::GetBytes($flags), 0, $record, 8, 2)
            [Array]::Copy($record, 16, $header, 14, 12)
            [Array]::Copy([BitConverter]::GetBytes([uint16]($localExtra + 4 + $padding)), 0, $header, 28, 2)
            $writer.Write($header)
            $writer.Write([uint16]0xd935)
            $writer.Write([uint16]$padding)
            if ($padding) { $writer.Write([byte[]]::new($padding)) }
            $writer.Write($bytes, [int]($offset + $headerLength), [int]$compressedSize)
            [Array]::Copy([BitConverter]::GetBytes($newOffset), 0, $record, 42, 4)
            $records.Add($record)
            $cursor += $length
        }
        $newCentral = [uint32]$stream.Position
        foreach ($record in $records) { $writer.Write($record) }
        $endRecord = [byte[]]::new($bytes.Length - $end)
        [Array]::Copy($bytes, $end, $endRecord, 0, $endRecord.Length)
        [Array]::Copy([BitConverter]::GetBytes($newCentral), 0, $endRecord, 16, 4)
        $writer.Write($endRecord)
        $writer.Flush()
        $stream.Flush($true)
    } finally { $writer.Dispose(); $stream.Dispose() }
}
foreach ($required in @('bin/java.exe', 'bin/javac.exe', 'bin/keytool.exe')) {
    if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome $required))) { throw 'JDK 17 missing. Extract the supplied JDK into toolchain/jdk or supply -JavaHome.' }
}
foreach ($required in @($androidJar, (Join-Path $tools 'aapt2.exe'), (Join-Path $tools 'zipalign.exe'), (Join-Path $tools 'core-lambda-stubs.jar'), (Join-Path $tools 'lib/d8.jar'), (Join-Path $tools 'lib/apksigner.jar'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Required Android tool missing: $required" }
}
$classes = Join-Path $build 'classes'
$dex = Join-Path $build 'dex'
$generated = Join-Path $build 'generated'
$flat = Join-Path $build 'flat'
foreach ($dir in @($build, $dist, $classes, $dex, $generated, $flat)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
# Remove only prior generated class/dex files in this project's build directory.
foreach ($dir in @($classes, $dex, $generated, $flat)) {
    $resolved = [IO.Path]::GetFullPath($dir)
    if (-not $resolved.StartsWith([IO.Path]::GetFullPath($build) + [IO.Path]::DirectorySeparatorChar)) { throw 'Unsafe build cleanup path.' }
    Get-ChildItem -LiteralPath $resolved -File -Recurse | Remove-Item -Force
}
$unsigned = Join-Path $build 'unsigned.apk'
$aligned = Join-Path $build 'aligned.apk'
# Build the Chinese file name from code points so this script stays pure ASCII:
# Windows PowerShell 5.1 decodes BOM-less UTF-8 as ANSI and would mangle it.
$appName = -join ([char]0x5FC3, [char]0x5FC3, [char]0x76F8, [char]0x5E94)
$manifestFile = Join-Path $project 'app/src/main/AndroidManifest.xml'
$manifestText = [IO.File]::ReadAllText($manifestFile, [Text.Encoding]::UTF8)
$version = [regex]::Match($manifestText, 'android:versionName="([^"]+)"').Groups[1].Value
if (-not $version) { throw 'Cannot read versionName from AndroidManifest.xml.' }
$output = Join-Path $dist ($appName + '-' + $version + '.apk')
$resourceRoot = Join-Path $project 'app/src/main/res'
# aapt2 must write the APK itself: it stores resources.arsc with ZIP method 0
# (stored) and aligns it, which Android 11+ requires for targetSdk 30+.
# Packaging the linked directory with .NET instead produces method 8
# "stored deflate" blocks even at CompressionLevel.NoCompression, and
# Android 14 rejects that with "-124 ... resources.arsc ... uncompressed".
foreach ($resource in Get-ChildItem -LiteralPath $resourceRoot -File -Recurse) {
    Invoke-Checked (Join-Path $tools 'aapt2.exe') @('compile', $resource.FullName, '-o', $flat)
}
$compiledFiles = @(Get-ChildItem -LiteralPath $flat -Filter '*.flat' -File | ForEach-Object { $_.FullName })
if (Test-Path -LiteralPath $unsigned) { Remove-Item -LiteralPath $unsigned -Force }
$linkArgs = @('link', '-o', $unsigned, '-I', $androidJar, '--manifest', (Join-Path $project 'app/src/main/AndroidManifest.xml'), '--java', $generated, '--min-sdk-version', '24', '--target-sdk-version', '36') + $compiledFiles
$assets = Join-Path $project 'app/src/main/assets'
if (Test-Path -LiteralPath $assets) { $linkArgs += @('-A', $assets) }
Invoke-Checked (Join-Path $tools 'aapt2.exe') $linkArgs
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$sources = @(Get-ChildItem -LiteralPath (Join-Path $project 'app/src/main/java'), $generated -Filter '*.java' -Recurse -File)
if (-not $sources.Count) { throw 'No Java sources found.' }
$sourceList = Join-Path $build 'sources.txt'
$sourceLines = $sources | ForEach-Object { '"' + $_.FullName.Replace('\', '/') + '"' }
[IO.File]::WriteAllLines($sourceList, [string[]]$sourceLines, [Text.UTF8Encoding]::new($false))
$javaBootClasspath = $androidJar + [IO.Path]::PathSeparator + (Join-Path $tools 'core-lambda-stubs.jar')
Invoke-Checked (Join-Path $JavaHome 'bin/javac.exe') @('-encoding', 'UTF-8', '-source', '8', '-target', '8', '-bootclasspath', $javaBootClasspath, '-d', $classes, ('@' + $sourceList))
$classFiles = @(Get-ChildItem -LiteralPath $classes -Filter '*.class' -Recurse -File | ForEach-Object { $_.FullName })
Invoke-Checked (Join-Path $JavaHome 'bin/java.exe') (@('-cp', (Join-Path $tools 'lib/d8.jar'), 'com.android.tools.r8.D8', '--release', '--min-api', '24', '--lib', $androidJar, '--output', $dex) + $classFiles)
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open($unsigned, [IO.Compression.ZipArchiveMode]::Update)
try {
    foreach ($file in Get-ChildItem -LiteralPath $dex -Filter '*.dex' -File) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, $file.Name, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $archive.Dispose() }
Write-AlignedZip $unsigned $aligned
Invoke-Checked (Join-Path $tools 'zipalign.exe') @('-c', '-v', '4', $aligned)
# Signing material is never part of the repository. Supply it either with
# -Keystore / XINXIN_KEYSTORE, or keep a private key at _private/signing/.
if ($Keystore) { $keystore = $Keystore }
elseif ($env:XINXIN_KEYSTORE) { $keystore = $env:XINXIN_KEYSTORE }
else { $keystore = Join-Path $project '_private/signing/development.p12' }
if (-not (Test-Path -LiteralPath $keystore)) {
    throw 'Signing keystore not found. Pass -Keystore, set XINXIN_KEYSTORE, or place a private key at _private/signing/development.p12.'
}
if (-not $env:XINXIN_KEYSTORE_PASSWORD) {
    throw 'Set XINXIN_KEYSTORE_PASSWORD to the keystore/key password before building.'
}
Invoke-Checked (Join-Path $JavaHome 'bin/java.exe') @('-jar', (Join-Path $tools 'lib/apksigner.jar'), 'sign', '--ks', $keystore, '--ks-key-alias', $KeyAlias, '--ks-pass', 'env:XINXIN_KEYSTORE_PASSWORD', '--key-pass', 'env:XINXIN_KEYSTORE_PASSWORD', '--out', $output, $aligned)
Invoke-Checked (Join-Path $tools 'zipalign.exe') @('-c', '-v', '4', $output)
Invoke-Checked (Join-Path $JavaHome 'bin/java.exe') @('-jar', (Join-Path $tools 'lib/apksigner.jar'), 'verify', '--verbose', '--print-certs', $output)
Get-FileHash -LiteralPath $output -Algorithm SHA256
Write-Host "Built: $output"
exit 0
