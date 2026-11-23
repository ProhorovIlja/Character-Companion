# Character Companion v0.35.0-source — 2026-11-23

Source-only Windows packaging repair based on the official CefSharp .NET Core self-host deployment example. Android has not changed from v0.34.0-source.

## Windows 0.11.0 pre-alpha: CEF child-process self-host

The previous Windows project published a **self-contained** WPF/CEF folder but relied on the generated WPF `Main` and did not set up `BrowserSubprocessPath`. This is a release blocker: when CefSharp runs its renderer/GPU/utility subprocesses, they need a working executable entry point rather than another WPF browser window. CefSharp's official guidance for self-contained .NET Core applications documents self-hosting the subprocess within the application executable.

- Added `windows/CharacterCompanion.Windows/Program.cs`: an explicit `[STAThread]` entry point calls `CefSharp.BrowserSubprocess.SelfHost.Main(args)` **before** constructing WPF `App`, and immediately returns the exit code for CEF subprocess invocations.
- App startup sets `CefSettings.BrowserSubprocessPath` to the running **CharacterCompanion.Windows.exe**, rather than a separate Edge/WebView2 runtime, and rejects a dotnet.exe-style launch path so the subprocess cannot silently start the wrong binary.
- Set `StartupObject`, `UseAppHost` and `CefSharpExcludeSubProcessExe` explicitly in the WPF project. These settings target the complete, self-contained `win-x64` **folder** published by the manual build command; they are not a single-file or Native AOT build.
- Hardened `windows/build-windows.ps1` to fail if the published folder is missing managed CefSharp subprocess support or CEF resource files: `CefSharp.BrowserSubprocess.Core.dll`, `libcef.dll`, `icudtl.dat`, `v8_context_snapshot.bin`, `chrome_100_percent.pak`, and `locales/`.
- Added four cross-platform source audits in `windows/tests/test_cef_self_host.py`. They check process ordering and deployment prerequisites but do not execute CEF.

Official design references:

- https://github.com/cefsharp/CefSharp/wiki/SelfHost-BrowserSubProcess
- https://github.com/cefsharp/CefSharp.MinimalExample/blob/master/CefSharp.MinimalExample.Wpf/Program.PublishSingleFile.cs
- https://github.com/cefsharp/CefSharp/blob/master/CefSharp.Core/BrowserSubprocess/SelfHost.cs
- https://github.com/cefsharp/CefSharp/wiki/Output-files-description-table-%28Redistribution%29

## Android unchanged

Android app 0.26.0-rc1 (GeckoView ARM64 `gecko` and WebView fallback `legacy`, compile/target SDK 36) remains in the repository. Pixel 8a `akita` / LineageOS 24 / Android 17, MindTheGapps and Morphe microG are still device-compatibility targets, not verified runtime results.

## What has actually been verified

- Windows Python source tests: **37/37 PASS**, including four new self-host / build-asset gates.
- Windows Node synthetic DOM tests **16/16 PASS** and CEF JavaScript-envelope tests **2/2 PASS**.
- Android Python tests **83/83 PASS**; standalone pure Kotlin/JVM PolicySmoke **PASS**.
- Bash syntax, XML parsing and archive/hash checks performed offline.

**Not verified:** .NET 10 / WPF compilation, native CEF subprocess launch, `win-x64` publish output, microphone/Whisper hardware, Google or Character.AI sign-in, Android Gradle build or installation. The manual Windows publishing script itself was not executed because .NET SDK/PowerShell/CEF native binaries are unavailable in this container. CEF's lack of Chromium sandbox remains an important security limitation. **No signed APK/EXE/installer or GitHub binary Release exists.** No scheduled jobs, CI, background monitoring or automations were created.
