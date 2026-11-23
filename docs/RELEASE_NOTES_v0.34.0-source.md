# Character Companion v0.34.0-source — 2026-11-23

Source-only stability and security update for the existing Android and Windows clients. Neither platform has been compiled or tested on physical devices in the available environment.

## Android 0.26.0-rc1 — GeckoView and legacy

- A pathological `InputStream` that returns zero bytes forever can no longer spin indefinitely while saving a GeckoView download. The bounded streaming copier now rejects eight consecutive empty reads with `IOException(download_stream_stalled)`; a transient zero is tolerated and the counter resets as soon as progress resumes.
- The existing 256-MiB size limit, first-party HTTPS checks, user-selected Android document destination, and best-effort deletion of partial files remain.
- Added executable Kotlin/JVM regression cases for a permanently stalled stream and a stream recovering after one zero-length read. No Android SDK is needed to run those pure Kotlin tests.
- Android versionCode advanced to 26; compileSdk/targetSdk remain API 36. Pixel 8a (`akita`) / LineageOS 24 / Android 17 compatibility has **not** been verified by installation.

## Windows 0.10.0 pre-alpha — CEF + offline whisper.cpp

- Corrected external-browser handoff analysis of nested OAuth redirect parameters. The previous implementation matched a hardcoded subset of keys after decoding a nested query; e.g. an encoded `redirect_uri`, `callback`, or `code_verifier` could be missed. It now compares every discovered nested parameter key against the same complete `SensitiveKeys` set used for top-level query parameters.
- The production-linked C# `PolicySmoke` project gains six URL cases including harmless nested parameter names to avoid accidental substring matching. The C# tests **cannot be executed without .NET 10**; only Python source-level assertions are available in this environment.
- Windows remains WPF/.NET 10 with bundled CefSharp/CEF, NAudio and local whisper.cpp, **not WebView2**. No browser runtime, microphone or Character.AI account integration test was performed.

## Verification

- Android Python: **83/83 PASS**; pure Kotlin/JVM policy smoke: **PASS**.
- Windows Python: **33/33 PASS**; Node synthetic composer runtime: **16/16 PASS**; CEF script-envelope cases: **2/2 PASS**.
- Archive source byte comparisons, archive CRC checks and SHA-256 manifests are supplied in the verification report.
- **No compiled APK, EXE, signed installer or GitHub binary Release.** Android SDK/Gradle/ADB and .NET SDK/Windows runtime are unavailable; CEF lacks Chromium sandbox; Character.AI's public API is not currently offered and OAuth/web UX cannot be guaranteed.
- No CI, scheduled tasks, background checks, monitors or notifications were created.
