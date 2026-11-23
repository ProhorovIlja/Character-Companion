# Manual Windows x64 local build only. Does not sign, install, publish a GitHub Release,
# schedule, monitor, or start any background agent. Requires trusted .NET SDK 10 + NuGet.
param(
    [ValidateSet('Debug','Release')]
    [string] $Configuration = 'Release',
    [string] $OutputDirectory = ''
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Fail([string] $message) {
    [Console]::Error.WriteLine($message)
    exit 1
}
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$project = Join-Path $PSScriptRoot 'CharacterCompanion.Windows\CharacterCompanion.Windows.csproj'
$smoke = Join-Path $PSScriptRoot 'PolicySmoke\CharacterCompanion.PolicySmoke.csproj'
$published = if ($OutputDirectory) { [IO.Path]::GetFullPath($OutputDirectory) } else {
    Join-Path $root 'release-artifacts\windows\win-x64'
}
if (-not (Get-Command dotnet -ErrorAction SilentlyContinue)) {
    Fail 'Missing .NET SDK. Install the official .NET 10 SDK for Windows x64 first.'
}
$sdkList = & dotnet --list-sdks
if ($LASTEXITCODE -ne 0 -or -not ($sdkList | Where-Object { $_ -match '^10\.' })) {
    Fail '.NET SDK 10.x not found. Refusing unverified build.'
}
Write-Output 'STEP=Compile linked production security/audio code and execute PolicySmoke'
& dotnet run --project $smoke --configuration $Configuration
if ($LASTEXITCODE -ne 0) { Fail 'PolicySmoke failed; publish not attempted.' }
Write-Output 'STEP=Publish WPF+CEF portable folder with native dependencies intact'
# CefSharp/CEF requires native DLLs/subprocess/locales. Never trim, AOT, or single-file it.
& dotnet publish $project --configuration $Configuration --runtime win-x64 --self-contained true `
    -p:PublishSingleFile=false -p:PublishTrimmed=false -p:PublishReadyToRun=false `
    --output $published
if ($LASTEXITCODE -ne 0) { Fail 'Windows publish failed; no verified EXE.' }
$exe = Join-Path $published 'CharacterCompanion.Windows.exe'
$cef = Join-Path $published 'libcef.dll'
# CefSharp .NET Core self-contained publish uses our app EXE as its child host;
# the managed BrowserSubprocess.Core assembly and CEF data files are mandatory.
$requiredCef = @('CefSharp.BrowserSubprocess.Core.dll', 'icudtl.dat',
                 'v8_context_snapshot.bin', 'chrome_100_percent.pak')
if (-not (Test-Path -LiteralPath $exe -PathType Leaf)) { Fail 'Publish returned no EXE.' }
if (-not (Test-Path -LiteralPath $cef -PathType Leaf)) { Fail 'CEF native runtime libcef.dll missing.' }
foreach ($item in $requiredCef) {
    if (-not (Test-Path -LiteralPath (Join-Path $published $item) -PathType Leaf)) {
        Fail "CEF self-host runtime component missing: $item"
    }
}
if (-not (Test-Path -LiteralPath (Join-Path $published 'locales') -PathType Container)) {
    Fail 'CEF locale resources missing; verify NuGet native asset copying.'
}
$bytes = (Get-Item -LiteralPath $exe).Length
if ($bytes -le 0) { Fail 'Published EXE is empty.' }
Write-Output "WINDOWS_BUILD_PATH=$published"
Write-Output "WINDOWS_EXE_SHA256=$((Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash.ToLowerInvariant())"
Write-Output 'SOURCE_VERIFICATION=PASS; CEF_SELF_HOST_FILES=PASS; RUNTIME_AND_SIGNING=NOT_VERIFIED'
Write-Output 'Keep the ENTIRE folder; manually test browser, audio, login, CEF resources and Windows VC++ runtime.'
