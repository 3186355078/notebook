param(
    [string]$SdkRoot = "D:\SDK",
    [string]$KeystorePath = "D:\Secure\WorkLogAI\worklog-ai-release.jks",
    [string]$KeyAlias = "worklog-ai-release",
    [string]$CertificateSha256 = "15668D9F84061C17CF099A99FF84E1610113204F86311C1044454A30CFC3E801"
)

$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$tempRoot = [IO.Path]::GetFullPath($env:TEMP)
$clonePath = [IO.Path]::GetFullPath((Join-Path $tempRoot "worklog-ai-clean-release-$PID"))
if (-not $clonePath.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or
    [IO.Path]::GetFileName($clonePath) -notlike "worklog-ai-clean-release-*") {
    throw "Refusing to use an unexpected clean-clone path."
}
if (-not (Test-Path -LiteralPath $KeystorePath -PathType Leaf)) {
    throw "Release Keystore does not exist outside the repository."
}

if (Test-Path -LiteralPath $clonePath) {
    Remove-Item -LiteralPath $clonePath -Recurse -Force
}

$storePassword = Read-Host "Release Keystore password" -AsSecureString
$keyPassword = Read-Host "Release key password" -AsSecureString
$storePasswordPointer = [IntPtr]::Zero
$keyPasswordPointer = [IntPtr]::Zero
$completed = $false

try {
    & git clone --no-hardlinks $repositoryRoot $clonePath
    if ($LASTEXITCODE -ne 0) {
        throw "Local clean clone failed."
    }

    $sdkProperty = $SdkRoot.Replace("\", "\\").Replace(":", "\:")
    "sdk.dir=$sdkProperty" |
        Set-Content -LiteralPath (Join-Path $clonePath "local.properties") -Encoding ascii

    $storePasswordPointer =
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($storePassword)
    $keyPasswordPointer =
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($keyPassword)
    $env:WORKLOG_RELEASE_STORE_FILE = $KeystorePath
    $env:WORKLOG_RELEASE_STORE_PASSWORD =
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($storePasswordPointer)
    $env:WORKLOG_RELEASE_KEY_ALIAS = $KeyAlias
    $env:WORKLOG_RELEASE_KEY_PASSWORD =
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPasswordPointer)

    Push-Location $clonePath
    try {
        & .\gradlew.bat `
            --offline `
            --no-daemon `
            --no-configuration-cache `
            clean `
            :app:testDebugUnitTest `
            :app:lintRelease `
            :app:assembleRelease `
            :app:bundleRelease `
            --rerun-tasks `
            --no-build-cache
        if ($LASTEXITCODE -ne 0) {
            throw "Clean-clone Release build failed."
        }

        $suiteFiles = Get-ChildItem "app\build\test-results\testDebugUnitTest" -Filter "TEST-*.xml"
        $tests = 0
        $failures = 0
        $errors = 0
        $skipped = 0
        foreach ($suiteFile in $suiteFiles) {
            [xml]$suiteXml = Get-Content -LiteralPath $suiteFile.FullName
            $tests += [int]$suiteXml.testsuite.tests
            $failures += [int]$suiteXml.testsuite.failures
            $errors += [int]$suiteXml.testsuite.errors
            $skipped += [int]$suiteXml.testsuite.skipped
        }
        if ($tests -ne 275 -or $failures -ne 0 -or $errors -ne 0 -or $skipped -ne 0) {
            throw "Clean-clone JVM test totals do not match the accepted 275/275 baseline."
        }

        & .\tools\package-release.ps1 `
            -Version "0.4.0" `
            -VersionCode 5 `
            -CertificateSha256 $CertificateSha256 `
            -SdkRoot $SdkRoot `
            -InstrumentationTests 65 `
            -RoomVersion 2 `
            -BackupFormatVersion 2 `
            -SchemaSha256 "944C04F92F7633FCFCA2DB407588B850B981AC7B2E4541A0498505330D40862D" `
            -PullRequestNumber 3 `
            -MergeCommit "f47baf3b9a22c2e5f3b35f5ffb35f4eebca89645"
        if ($LASTEXITCODE -ne 0) {
            throw "Clean-clone artifact signature verification failed."
        }
        $completed = $true
        [pscustomobject]@{
            Head = (& git rev-parse HEAD).Trim()
            Suites = $suiteFiles.Count
            Tests = $tests
            Failures = $failures
            Errors = $errors
            Skipped = $skipped
            ReleaseApk = Test-Path "app\build\outputs\apk\release\app-release.apk"
            ReleaseAab = Test-Path "app\build\outputs\bundle\release\app-release.aab"
            SignatureVerified = $true
        } | Format-List
    } finally {
        Pop-Location
    }
} finally {
    Remove-Item Env:WORKLOG_RELEASE_STORE_FILE -ErrorAction SilentlyContinue
    Remove-Item Env:WORKLOG_RELEASE_STORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:WORKLOG_RELEASE_KEY_ALIAS -ErrorAction SilentlyContinue
    Remove-Item Env:WORKLOG_RELEASE_KEY_PASSWORD -ErrorAction SilentlyContinue

    if ($storePasswordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($storePasswordPointer)
    }
    if ($keyPasswordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPasswordPointer)
    }

    if ($completed -and (Test-Path -LiteralPath $clonePath)) {
        $resolvedClone = [IO.Path]::GetFullPath($clonePath)
        if ($resolvedClone.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -and
            [IO.Path]::GetFileName($resolvedClone) -like "worklog-ai-clean-release-*") {
            $extendedClonePath = "\\?\$resolvedClone"
            [IO.Directory]::Delete($extendedClonePath, $true)
        }
    }
}
