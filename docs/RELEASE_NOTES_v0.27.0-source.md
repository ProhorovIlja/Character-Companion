# Character Companion v0.27.0-source — Windows authentication handoff hardening

Date: 2026-11-23. **Source-only checkpoint**, not a build, installation package or stable release.

## Windows v0.4.0 pre-alpha

- CEF/WPF remains the Windows browser engine, without WebView2. The native URL policy rejects HTTP/file/javascript URLs, userinfo, alternate ports, Unicode control/bidi characters and overlong addresses.
- System-browser handoff no longer forwards OAuth codes, state, token fragments, sensitive callback paths, nested percent-encoded redirect parameters or signed URLs. The user may instead confirm opening the fixed `https://character.ai/` homepage. Ordinary non-sensitive HTTPS external links still require explicit confirmation.
- Both popup interception and explicit system-browser commands pass through the policy at the point where the browser process is started.
- Added `windows/PolicySmoke/` which compiles the **exact production `Security/SitePolicy.cs`** with .NET 10, no CEF/WPF dependencies, and checks 24 cases. This is runnable on demand by the user; no scheduled or automatic actions were created.
- Android Gecko (0.23.0-rc1, API 36 compile/target, Android 17 intended) and Android legacy fallback are unchanged; Windows offline Whisper, microphone and draft composer are unchanged.

## Verification and caveats

- 78 Android Python tests, pure Kotlin/JVM PolicySmoke and 21 Windows Python/Node tests pass. Source/JavaScript/manifest checks **do not prove** production C# compiles.
- There is no installed .NET SDK, Gradle distribution, Android SDK, emulator, ADB, Windows runtime, or Pixel 8a connected in this environment. **No APK, EXE or installer was compiled, signed or tested.** The 24-case production-C# console test was authored but not executed here.
- Google and Apple OAuth may reject embedded browsers; cookies and login state are not shared from the external browser back into Gecko/CEF. The official Character.AI Android XAPK was previously inspected statically, not used to copy proprietary code.
- CefSharp lacks a Chromium sandbox; any distribution requires a security review and frequent browser-engine patch updates. The user cannot be promised absence of Play Protect or SmartScreen warnings.

## Manual verification

`dotnet run --project windows/PolicySmoke/CharacterCompanion.PolicySmoke.csproj`

Then, on a real Windows 10/11 x64 machine, build and run `windows/CharacterCompanion.Windows` with the full CEF distribution and complete `windows/QA.md`. For Android, install and test the `gecko` and `legacy` flavors on Pixel 8a (`akita`), LineageOS 24 (Android 17). No CI or scheduled work without explicit permission.
