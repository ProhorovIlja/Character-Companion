# Character Companion v0.32.0 — source-only checkpoint

23 November 2026. Windows v0.8.0 pre-alpha; Android unchanged at 0.25.0-rc1.

## Changes

- Windows whisper.cpp child-process stdout and stderr are now drained concurrently with **two fixed 4-KiB character buffers**, instead of unbounded `ReadToEndAsync` strings. No audio or transcription data is sent to a remote service. The separate bounded local `-otxt` file remains the only transcript source.
- The existing four-minute timeout, child-process termination and subsequent temporary-file cleanup remain. An unexpectedly chatty CLI no longer causes unbounded host-memory growth through the pipe readers.
- Added `windows/build-windows.ps1`, a **manual foreground-only** Windows x64 .NET 10 publisher. It first runs the source-linked C# PolicySmoke gate, then builds an untrimmed, unpackaged, non-single-file self-contained Windows folder. It rejects a missing EXE or CEF native library/locales and prints the EXE SHA-256. The full folder, not just the EXE, is required.
- Extended PolicySmoke to compile the actual production `WhisperCliTranscriber.cs` in addition to `SitePolicy.cs` and `Pcm16Resampler.cs`; added one fail-closed missing-Whisper-executable test. The C# project is **not compiled or run in this environment**.
- Added static regressions for the manual build script and bounded audio pipe draining.

## Completed checks and limitations

The source and JS checks can run in this Linux container. The .NET SDK, Windows/CefSharp runtime, Android SDK, ADB and Gradle are not installed. Therefore neither platform was compiled here. No Windows EXE, Android APK, signed installer or GitHub binary Release is included. **No claim** of actual Character.AI Google login, CEF runtime, working microphone, Whisper model loading, message delivery or Android 17 / Pixel 8a compatibility.

Android's opt-in GeckoView ARM64 and legacy WebView flavors remain unchanged, targeting Pixel 8a (`akita`) on LineageOS 24 / Android 17 with MindTheGapps and Morphe microG. Android compile/target SDK remains API 36.

The source-only archive intentionally excludes the binary Gradle Wrapper JAR; the GitHub repository contains it. CefSharp does not implement Chromium sandbox. No tasks, CI, monitoring, scheduled actions or background automations were created.
