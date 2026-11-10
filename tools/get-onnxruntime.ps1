param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
# Approved exception: one pinned inference runtime for the on-device captcha model.
# No Gradle or Maven tooling is added; the AAR is fetched from Maven Central and
# verified against a fixed digest, exactly like the pinned payment SDK.
$runtimeVersion = '1.31.1'
$runtimeSha256 = 'd4d50660300ed2c4f38ffdfd5ab5bfa3a22f995f282dda24f42ea3ea55467b49'
# Only arm64-v8a ships. Adding armeabi-v7a grows the APK by about 8.8 MB.
$abis = @('arm64-v8a')
$runtimeRoot = Join-Path $ProjectRoot ('toolchain/onnxruntime/' + $runtimeVersion)
$archivePath = Join-Path $runtimeRoot ('onnxruntime-android-' + $runtimeVersion + '.aar')
$runtimeUrl = 'https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/' + $runtimeVersion + '/onnxruntime-android-' + $runtimeVersion + '.aar'
New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
if (-not (Test-Path -LiteralPath $archivePath)) {
    $temporaryPath = $archivePath + '.download'
    Invoke-WebRequest -UseBasicParsing -Uri $runtimeUrl -OutFile $temporaryPath
    if ((Get-FileHash -LiteralPath $temporaryPath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $runtimeSha256) {
        Remove-Item -LiteralPath $temporaryPath -Force
        throw 'ONNX Runtime SHA-256 mismatch; runtime was not installed.'
    }
    Move-Item -LiteralPath $temporaryPath -Destination $archivePath -Force
}
if ((Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $runtimeSha256) {
    throw 'Cached ONNX Runtime SHA-256 mismatch; replace it from the official source.'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$classesPath = Join-Path $runtimeRoot 'classes.jar'
if (-not (Test-Path -LiteralPath $classesPath)) {
    $archive = [IO.Compression.ZipFile]::OpenRead($archivePath)
    try {
        $entry = $archive.GetEntry('classes.jar')
        if (-not $entry) { throw 'ONNX Runtime AAR has no classes.jar.' }
        [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $classesPath, $true)
    } finally { $archive.Dispose() }
}
foreach ($abi in $abis) {
    $abiRoot = Join-Path $runtimeRoot $abi
    New-Item -ItemType Directory -Path $abiRoot -Force | Out-Null
    foreach ($library in @('libonnxruntime.so', 'libonnxruntime4j_jni.so')) {
        $target = Join-Path $abiRoot $library
        if (Test-Path -LiteralPath $target) { continue }
        $archive = [IO.Compression.ZipFile]::OpenRead($archivePath)
        try {
            $entry = $archive.GetEntry('jni/' + $abi + '/' + $library)
            if (-not $entry) { throw ('ONNX Runtime AAR has no jni/' + $abi + '/' + $library + '.') }
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
        } finally { $archive.Dispose() }
    }
}
if (-not (Test-Path -LiteralPath $classesPath)) { throw 'Incomplete ONNX Runtime archive.' }
[PSCustomObject]@{ ClassesJar = $classesPath; Root = $runtimeRoot; Abis = $abis }
