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
# Full GitHub clones contain the verified wrapper. Lightweight source ZIPs do not,
# so they require a preinstalled Gradle 8.13; do not download or install silently.
$expectedJar = '81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f'
$expectedDist = '20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78'
$gradleCommand = $null
if (Test-Path -LiteralPath $wrapperJar -PathType Leaf) {
    if (-not (Test-Path -LiteralPath $wrapperBat -PathType Leaf)) {
        Fail 'Missing gradlew.bat alongside the Gradle Wrapper JAR.'
    }
    $actualJar = (Get-FileHash -Algorithm SHA256 -LiteralPath $wrapperJar).Hash.ToLowerInvariant()
    if ($actualJar -cne $expectedJar) {
        Fail 'Gradle Wrapper JAR checksum mismatch: refusing to execute it.'
    }
    $props = Join-Path $root 'gradle\wrapper\gradle-wrapper.properties'
    if (-not (Test-Path -LiteralPath $props -PathType Leaf)) {
        Fail 'Gradle Wrapper distribution properties are missing.'
    }
    $lines = @(Get-Content -LiteralPath $props)
    if (($lines -cnotcontains 'distributionUrl=https\://services.gradle.org/distributions/gradle-8.13-bin.zip') -or
        ($lines -cnotcontains "distributionSha256Sum=$expectedDist")) {
        Fail 'Gradle 8.13 distribution URL/checksum pin is invalid.'
    }
    $gradleCommand = $wrapperBat
    Write-Output 'GRADLE_PROVIDER=VERIFIED_WRAPPER'
} else {
    $candidate = Get-Command 'gradle' -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $candidate) {
        Fail 'Missing Gradle Wrapper JAR and Gradle 8.13. Clone full GitHub repo or install Gradle 8.13 locally.'
    }
    $gradleCommand = $candidate.Source
    $versionText = (& $gradleCommand '--version' 2>&1) -join "`n"
    if (($LASTEXITCODE -ne 0) -or ($versionText -notmatch '(?m)^Gradle 8\.13\s*$')) {
        Fail 'Installed Gradle version is not 8.13. Refusing an unverified toolchain.'
    }
    Write-Output 'GRADLE_PROVIDER=LOCAL_GRADLE_8.13'
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
# Refuse stale output from previous build attempts.
if (Test-Path -LiteralPath $apk -PathType Leaf) { Remove-Item -LiteralPath $apk -Force }
Push-Location $root
try {
    & $gradleCommand '--no-daemon' $task
    $result = $LASTEXITCODE
} finally { Pop-Location }
if ($result -ne 0) { Fail "Gradle failed with exit code $result. No verified APK created." }
if (-not (Test-Path -LiteralPath $apk -PathType Leaf)) { Fail 'Build ended without its expected flavor-specific APK.' }
if ((Get-Item -LiteralPath $apk).Length -le 0) { Fail 'APK is empty.' }
# A fake/non-ZIP output should never be labeled APK_READY.
Add-Type -AssemblyName System.IO.Compression.FileSystem
try {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($apk)
    try {
        $manifest = $archive.GetEntry('AndroidManifest.xml')
        $dex = $archive.GetEntry('classes.dex')
        if (($null -eq $manifest) -or ($null -eq $dex) -or
            ($manifest.Length -le 0) -or ($dex.Length -le 0)) {
            Fail 'Build output is missing AndroidManifest.xml or classes.dex.'
        }
    } finally { $archive.Dispose() }
} catch {
    Fail "Build output failed APK ZIP inspection: $_"
}
Write-Output "APK_READY=$apk"
Write-Output "APK_SHA256=$((Get-FileHash -Algorithm SHA256 -LiteralPath $apk).Hash.ToLowerInvariant())"
Write-Output 'DEBUG_ONLY=YES; not signed for production and not device-tested.'
