$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$projectRoot = Split-Path -Parent $PSScriptRoot
$checker = Join-Path $projectRoot 'tools/check-screenshot-policy.ps1'
$fixtureRoot = Join-Path $projectRoot 'build/screenshot-policy-fixtures'
$cases = @(
    @{ Name = 'normal'; App = 'class Page {}'; Test = 'assert (window.flags & FLAG_SECURE) == 0;'; Allowed = $true },
    @{ Name = 'sdk-exception'; App = 'class Page {}'; Test = 'assert (window.flags & FLAG_SECURE) == 0;'; Allowed = $true },
    @{ Name = 'app-secure'; App = 'window.addFlags(FLAG_SECURE);'; Test = ''; Allowed = $false },
    @{ Name = 'app-numeric'; App = 'window.setFlags(0x00002000, 0x00002000);'; Test = ''; Allowed = $false },
    @{ Name = 'recents'; App = 'setRecentsScreenshotEnabled(false);'; Test = ''; Allowed = $false },
    @{ Name = 'surface'; App = 'surface.setSecure(true);'; Test = ''; Allowed = $false },
    @{ Name = 'device-policy'; App = 'manager.setScreenCaptureDisabled(admin, true);'; Test = ''; Allowed = $false },
    @{ Name = 'test-restore'; App = 'class Page {}'; Test = "window.addFlags(`n FLAG_SECURE);"; Allowed = $false },
    @{ Name = 'test-bypass'; App = 'class Page {}'; Test = 'window.clearFlags(FLAG_SECURE);'; Allowed = $false },
    @{ Name = 'test-numeric'; App = 'class Page {}'; Test = 'window.addFlags(8192);'; Allowed = $false }
)
foreach ($case in $cases) {
    $fixture = Join-Path $fixtureRoot $case.Name
    foreach ($relative in @('app/src/main/java', 'tests/android', 'toolchain/alipay')) {
        New-Item -ItemType Directory -Path (Join-Path $fixture $relative) -Force | Out-Null
    }
    [IO.File]::WriteAllText((Join-Path $fixture 'app/src/main/java/Page.java'), $case.App)
    [IO.File]::WriteAllText((Join-Path $fixture 'tests/android/PageTest.java'), $case.Test)
    [IO.File]::WriteAllText((Join-Path $fixture 'toolchain/alipay/H5PayActivity.java'), 'window.addFlags(FLAG_SECURE);')
    $allowed = $true
    try { & $checker -ProjectRoot $fixture } catch { $allowed = $false }
    if ($allowed -ne $case.Allowed) { throw ('Unexpected policy result: ' + $case.Name) }
    Write-Host ('PASS: ' + $case.Name)
}
& $checker -ProjectRoot $projectRoot
Write-Host ('PASS: ' + $cases.Count + ' policy cases and current repository')
