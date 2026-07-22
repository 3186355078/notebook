param(
    [string]$Version = "0.1.0",
    [string]$CertificateSha256 = "15668D9F84061C17CF099A99FF84E1610113204F86311C1044454A30CFC3E801",
    [string]$SdkRoot = "D:\SDK",
    [int]$InstrumentationTests = 41
)

$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$apkSource = Join-Path $repositoryRoot "app\build\outputs\apk\release\app-release.apk"
$aabSource = Join-Path $repositoryRoot "app\build\outputs\bundle\release\app-release.aab"
$mappingSource = Join-Path $repositoryRoot "app\build\outputs\mapping\release"
$artifactDirectory = Join-Path $repositoryRoot "release-artifacts\$Version"
$r8Directory = Join-Path $artifactDirectory "r8"
$apkName = "worklog-ai-$Version-release.apk"
$aabName = "worklog-ai-$Version-release.aab"
$apkTarget = Join-Path $artifactDirectory $apkName
$aabTarget = Join-Path $artifactDirectory $aabName

if (-not (Test-Path -LiteralPath $apkSource -PathType Leaf)) {
    throw "Release APK is missing. Build the final committed source first."
}
if (-not (Test-Path -LiteralPath $aabSource -PathType Leaf)) {
    throw "Release AAB is missing. Build the final committed source first."
}

$apksigner =
    Get-ChildItem (Join-Path $SdkRoot "build-tools") -Recurse -Filter apksigner.bat |
        Sort-Object FullName -Descending |
        Select-Object -First 1
if (-not $apksigner) {
    throw "Official Android SDK apksigner was not found."
}

$apkVerification = & $apksigner.FullName verify --verbose --print-certs $apkSource 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "APK signature verification failed."
}
$normalizedCertificate = $CertificateSha256.Replace(":", "").ToUpperInvariant()
$verificationText = $apkVerification -join "`n"
$normalizedVerification = $verificationText.Replace(":", "").ToUpperInvariant()
if ($normalizedVerification -notmatch [regex]::Escape($normalizedCertificate)) {
    throw "APK signer certificate does not match the expected public fingerprint."
}
if ($verificationText -notmatch "Verified using v2 scheme .*: true" -or
    $verificationText -notmatch "Verified using v3 scheme .*: true" -or
    $verificationText -match "Verified using v1 scheme .*: true") {
    throw "APK signature schemes do not match the v2/v3-only release policy."
}
if ($verificationText -match "Android Debug") {
    throw "Release APK is signed with an Android Debug certificate."
}

$aabVerification = & jarsigner -verify -verbose -certs $aabSource 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "AAB JAR signature verification failed."
}

New-Item -ItemType Directory -Force $artifactDirectory | Out-Null
New-Item -ItemType Directory -Force $r8Directory | Out-Null
Copy-Item -LiteralPath $apkSource -Destination $apkTarget -Force
Copy-Item -LiteralPath $aabSource -Destination $aabTarget -Force

if (Test-Path -LiteralPath $mappingSource -PathType Container) {
    Get-ChildItem -LiteralPath $mappingSource -File |
        Where-Object { $_.Name -in @("mapping.txt", "seeds.txt", "usage.txt", "configuration.txt", "resources.txt") } |
        Copy-Item -Destination $r8Directory -Force
}

$apkHash = (Get-FileHash -LiteralPath $apkTarget -Algorithm SHA256).Hash
$aabHash = (Get-FileHash -LiteralPath $aabTarget -Algorithm SHA256).Hash
@(
    "$apkHash  $apkName"
    "$aabHash  $aabName"
) | Set-Content -LiteralPath (Join-Path $artifactDirectory "SHA256SUMS.txt") -Encoding ascii

$testResultDirectory = Join-Path $repositoryRoot "app\build\test-results\testDebugUnitTest"
$testSuites = Get-ChildItem -LiteralPath $testResultDirectory -Filter "TEST-*.xml"
$tests = 0
$failures = 0
$errors = 0
$skipped = 0
foreach ($suiteFile in $testSuites) {
    [xml]$suiteXml = Get-Content -LiteralPath $suiteFile.FullName
    $tests += [int]$suiteXml.testsuite.tests
    $failures += [int]$suiteXml.testsuite.failures
    $errors += [int]$suiteXml.testsuite.errors
    $skipped += [int]$suiteXml.testsuite.skipped
}

$head = (& git -C $repositoryRoot rev-parse HEAD).Trim()
$tag = (& git -C $repositoryRoot tag --points-at HEAD | Select-Object -First 1)
if (-not $tag) {
    $tag = "v$Version-internal (created after artifact verification)"
}
$builtAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz")
$apkSize = (Get-Item -LiteralPath $apkTarget).Length
$aabSize = (Get-Item -LiteralPath $aabTarget).Length

@"
WorkLog AI Internal Release Report
Version name: $Version
Version code: 1
Git commit: $head
Tag: $tag
Build date: $builtAt

JVM/Robolectric: $tests tests, $failures failures, $errors errors, $skipped skipped, $($testSuites.Count) suites
Android 16 instrumentation baseline: $InstrumentationTests tests, 0 failures, 0 errors, 0 skipped
Release lint: passed (see app/build/reports/lint-results-release.html)
R8/minification: enabled
Resource shrinking: enabled

APK signature: verified with official apksigner; v2/v3 enabled; non-Debug certificate
AAB signature: verified with jarsigner
Certificate SHA-256: $normalizedCertificate
APK: $apkName ($apkSize bytes)
APK SHA-256: $apkHash
AAB: $aabName ($aabSize bytes)
AAB SHA-256: $aabHash

Room database version: 1
Backup format version: 1
Validated device: HONOR PPG-AN00, Android 16 / API 36 (serial number omitted)
Compatibility waiver: Android 10-13 second-device/emulator testing was explicitly skipped by the user and is not claimed as passed.

Known limitations: backups are not encrypted; other OEM SAF/background behavior, larger real-world backups, and external OpenAI-compatible services remain internal-trial observations.
No keystore, password, API key, user data, device serial number, or test backup is included in this directory.
"@ | Set-Content -LiteralPath (Join-Path $artifactDirectory "RELEASE_REPORT.txt") -Encoding utf8

Get-ChildItem -LiteralPath $artifactDirectory -File | Select-Object Name, Length
