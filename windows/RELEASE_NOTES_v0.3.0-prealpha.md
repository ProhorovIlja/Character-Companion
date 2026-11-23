# Character Companion Windows 0.3.0 pre-alpha — SOURCE ONLY

Date: 2026-11-23. Project source revision: v0.22.0. This is **NOT** a compiled Windows installer, signed EXE or stable release.

## Changes

- Removed Microsoft's WebView2 XAML control and `Microsoft.Web.WebView2` NuGet dependency.
- Added in-app Chromium Embedded Framework via `CefSharp.Wpf.NETCore` 156.0.300; x64 WPF/.NET 10 user interface continues unchanged.
- Added CEF initialization/shutdown, private browser cache, first-party top-level policy, external HTTPS popup handling and opt-in tracker-host blocking through CEF request handlers.
- Adapted native-to-site injection to CEF `EvaluateScriptAsync` with strict JSON-stringified results and origin/composer/send checks.
- Preserved NAudio + whisper.cpp private microphone dictation, editable drafts, manual/explicit voice-to-send, Ctrl+Enter, save/copy/clear and cancellation.
- Added structural tests for engine absence/policy, and an executable Node test for the CEF JavaScript envelope. Android source remains unchanged.
- Documented GeckoView as an independent embedded browser option for Android after a full device-specific migration.

## Limitations / blocker

- **No .NET 10 SDK or Windows runner here**; CEF binaries not restored, WPF app not compiled or launched; no real website, OAuth, hardware audio or Windows x64 compatibility tests.
- No Android SDK/Gradle/Pixel 8a test device here either. No APK was produced.
- Embedded CEF costs more storage/memory than reuse of OS runtime and CefSharp does not implement Chromium sandbox. Security review and regularly updated CEF build needed before distributing binaries.
- OAuth in embedded browsers may fail. External browser session does not automatically return to app or share cookies. Actual Character.AI message delivery and Character Calls not proven.
- Do not generate Windows SmartScreen/Android Play Protect bypasses or make guarantees about warning suppression.
- No automation, GitHub CI trigger, scheduled task, background surveillance or monitoring is created.
