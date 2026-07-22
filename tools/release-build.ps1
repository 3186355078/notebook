param(
    [string[]]$Tasks = @(
        "clean",
        ":app:testDebugUnitTest",
        ":app:compileDebugAndroidTestKotlin",
        ":app:lintDebug",
        ":app:lintRelease",
        ":app:detekt",
        ":app:ktlintCheck",
        ":app:assembleRelease",
        ":app:bundleRelease"
    )
)

$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$keystorePath = "D:\Secure\WorkLogAI\worklog-ai-release.jks"
$keyAlias = "worklog-ai-release"

if (-not (Test-Path -LiteralPath $keystorePath -PathType Leaf)) {
    throw "Release Keystore does not exist. Create it outside the repository first."
}

$storePassword = Read-Host "Release Keystore password" -AsSecureString
$keyPassword = Read-Host "Release key password" -AsSecureString
$storePasswordPointer = [IntPtr]::Zero
$keyPasswordPointer = [IntPtr]::Zero

try {
    $storePasswordPointer =
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($storePassword)
    $keyPasswordPointer =
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($keyPassword)

    $env:WORKLOG_RELEASE_STORE_FILE = $keystorePath
    $env:WORKLOG_RELEASE_STORE_PASSWORD =
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($storePasswordPointer)
    $env:WORKLOG_RELEASE_KEY_ALIAS = $keyAlias
    $env:WORKLOG_RELEASE_KEY_PASSWORD =
        [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPasswordPointer)

    Push-Location $repositoryRoot
    try {
        & .\gradlew.bat `
            --offline `
            --no-daemon `
            --no-configuration-cache `
            @Tasks `
            --rerun-tasks `
            --no-build-cache
        if ($LASTEXITCODE -ne 0) {
            throw "Release build failed with Gradle exit code $LASTEXITCODE."
        }
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
}
