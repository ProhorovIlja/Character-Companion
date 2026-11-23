# Manual foreground-only Android APK debug build from Windows PowerShell.
# No task scheduler, CI, background job, auto-installation, signing or publishing.
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('gecko', 'legacy')]
    [string] $Flavor
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Fail([string] $message) {
    [Console]::Error.WriteLine($message)
    exit 1
}

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$wrapperJar = Join-Path $root 'gradle\wrapper\gradle-wrapper.jar'
$wrapperBat = Join-Path $root 'gradlew.bat'
if (-not (Test-Path -LiteralPath $wrapperJar -PathType Leaf)) {
    Fail 'Missing gradle-wrapper.jar: clone the full GitHub repository, not the source-only ZIP.'
}
if (-not (Test-Path -LiteralPath $wrapperBat -PathType Leaf)) {
    Fail 'Missing gradlew.bat. Use the complete GitHub checkout.'
}
$expectedJar = '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f'
$actualJar = (Get-FileHash -Algorithm SHA256 -LiteralPath $wrapperJar).Hash.ToLowerInvariant()
if ($actualJar -cne $expectedJar) {
    Fail 'Gradle Wrapper JAR checksum mismatch: refusing to execute it.'
}
$javaExe = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java.exe' }
if (-not (Get-Command $javaExe -ErrorAction SilentlyContinue)) {
    Fail 'Java not found. Install a trusted JDK 17+ and configure JAVA_HOME.'
}
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else {
    Join-Path $env:LOCALAPPDATA 'Android\Sdk'
}
if (-not (Test-Path -LiteralPath (Join-Path $sdk 'platforms\android-36\android.jar') -PathType Leaf)) {
    Fail "Android SDK platform 36 not found at '$sdk'. Install that platform first. Android 17 is a separate runtime test target."
}
$task = if ($Flavor -eq 'gecko') { ':app:assembleGeckoDebug' } else { ':app:assembleLegacyDebug' }
$apk = Join-Path $root "app\build\outputs\apk\$Flavor\debug\app-$Flavor-debug.apk"
Write-Output "BUILD_FLAVOR=$Flavor"
Write-Output "SDK_PATH=$sdk"
Write-Output "TASK=$task"
Push-Location $root
try {
    & $wrapperBat '--no-daemon' $task
    $result = $LASTEXITCODE
} finally { Pop-Location }
if ($result -ne 0) { Fail "Gradle failed with exit code $result. No verified APK created." }
if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { Fail 'Build ended without its expected flavor-specific APK.' }
if ((Get-Item -LiteralPath $apk).Length -le 0) { Fail 'APK is empty.' }
Write-Output "APK_READY=$apk"
Write-Output "APK_SHA256=$((Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash.ToLowerInvariant())"
Write-Output 'DEBUG_ONLY=YES; not signed for production and not device-tested.'
