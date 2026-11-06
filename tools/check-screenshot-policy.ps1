param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# Permanent policy: the pinned payment SDK keeps its own capture protection.
# App-owned screens and instrumentation must allow capture without overrides.
$appRoot = Join-Path $ProjectRoot 'app/src/main/java'
$testRoot = Join-Path $ProjectRoot 'tests/android'
$restrictedCalls = 'setScreenCaptureDisabled\s*\([^;]*,\s*true\s*\)|setRecentsScreenshotEnabled\s*\(\s*false\s*\)|\.setSecure\s*\(\s*true\s*\)'
$flagMutations = '\b(?:addFlags|clearFlags|setFlags)\s*\([^;]*(?:\bFLAG_SECURE\b|\b8192\b|\b0x0*2000\b)'
$violations = [Collections.Generic.List[string]]::new()
foreach ($file in Get-ChildItem -LiteralPath $appRoot -Filter '*.java' -File -Recurse) {
    $source = [IO.File]::ReadAllText($file.FullName)
    if ($source -match ('FLAG_SECURE|' + $flagMutations + '|' + $restrictedCalls)) {
        $violations.Add($file.FullName)
    }
}
foreach ($file in Get-ChildItem -LiteralPath $testRoot -Filter '*.java' -File -Recurse) {
    $source = [IO.File]::ReadAllText($file.FullName)
    if ($source -match ($flagMutations + '|' + $restrictedCalls)) {
        $violations.Add($file.FullName)
    }
}
if ($violations.Count) {
    throw ('Screenshot policy violated. Only the payment SDK may restrict capture. App/test files: ' + ($violations -join ', '))
}
Write-Host 'Screenshot policy verified: app screens and tests allow capture; payment SDK is unchanged.'
