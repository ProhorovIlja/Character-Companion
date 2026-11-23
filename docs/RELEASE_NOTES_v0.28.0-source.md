# Character Companion v0.28.0-source — permission-origin safety and local Windows build preparation

Date: 2026-11-23. **Source-only checkpoint; not a production APK, Windows EXE or signed release.**

## Android GeckoView preview — app 0.24.0-rc1 (versionCode 24)

- Fixed an origin-confusion window in media permission prompts. A request made by `character.ai` may not be approved after navigation to a *different* `*.character.ai` or `*.c.ai` origin. Approval requires the originally visible origin and the requesting Gecko origin to match exactly (HTTPS, host, effective port 443). The same first-party content origin may change paths; cross-origin requests are declined and must be requested again.
- Retain **separate** origin snapshots for the Android runtime permission dialog and for the per-site Gecko microphone/camera confirmation. This avoids one asynchronous prompt overwriting the other's origin. Navigating to another origin, hiding the Activity, disabling media in either UI, losing the renderer or destroying the Activity revokes unresolved callbacks.
- External-browser transfer now recursively inspects the values of *all* URL query parameters, including apparently ordinary keys with nested percent-encoded OAuth callback URLs, and substitutes the fixed `https://character.ai/` homepage for sensitive handoffs. It does **not** share cookies or solve unsupported embedded Google OAuth.
- Existing `legacy` Android flavor is retained. GeckoView is embedded ARM64-only; Android Pixel 8a (`akita`), LineageOS 24, Android 17/API 37 remain **unverified on a physical device**. The build still uses compileSdk/targetSdk 36 with AGP 8.13.

## Manual Windows-hosted Android build path

- Added `gradlew.bat` for full GitHub checkouts and `scripts/build-apk-windows.ps1 gecko|legacy`. These are foreground **manual** local commands; no automation or CI is configured. The PowerShell helper verifies the expected SHA-256 of `gradle-wrapper.jar`, presence of Java and Android SDK platform 36, runs the flavor-specific `assemble...Debug` task, rejects failed/empty outputs and prints the resulting APK SHA-256.
- These scripts do not install toolchains, bypass Play Protect, sign production APKs, deploy to the phone, or run tests in the background. The lightweight source ZIP intentionally omits the Gradle wrapper JAR; use a complete GitHub checkout for an actual build.

## Windows CEF/Whisper

Windows WPF/.NET 10/CefSharp source remains **v0.4.0 pre-alpha**, no WebView2. The separate local transcription workflow (`whisper.cpp`) and editable/send-on-demand draft are unchanged. `windows/PolicySmoke` still needs a real .NET SDK to compile its linked production C# policy; source-level Python/Node checks are not equivalent.

## Verification / release blockers

- Offline Android Python unit tests and pure Kotlin/JVM PolicySmoke executed, including new nested OAuth and same-origin media assertions. Windows Python/Node tests executed. Record exact counts in the accompanying `verification.json` once packaging is complete.
- A real Android SDK, Gradle distribution, ADB/device and .NET SDK are **not available** in the execution environment; direct downloading is not available either. No actual Android or Windows app was compiled, run or tested on the user's hardware. No signed APK/EXE or stable GitHub Release has been created.
- Future manual acceptance: build both flavor APKs on a real Windows host, run on Pixel 8a/Android 17, test Character.AI login, voice/calls and permission navigation across origins, attachments and download cancellation, benchmark CPU/memory, inspect official Play Protect messages. Do not guarantee absence of installation warnings.
- CefSharp's lack of Chromium sandbox remains a Windows shipping risk. The official Character.AI 1.17.4 XAPK was previously inspected statically; no proprietary code, keys or internal APIs are copied into this client.
- No scheduled tasks, event-driven jobs, automation, CI triggers, monitoring or notifications were created.
