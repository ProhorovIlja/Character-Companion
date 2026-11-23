# Architecture — Native Android first, web-compatible where necessary

## What is genuinely native today

- Kotlin/Android Views shell, navigation, offline launch workspace, bookmarks and local search, manual backup, network-domain policy, privacy dashboard, safe downloads and file picker.
- Custom native workspace starts without a network request; WebView is only navigated when the user elects to use Character.AI, or when native workspace is disabled.
- Settings persisted locally. No scripts injected into first-party pages, no JavaScript credential bridges, no undocumented account tokens, no scheduled tasks/CI/background telemetry.

## Native workspace state machine (v0.14)

- The workspace, browser navigation, lifecycle and permission prompts have explicit boundaries. `NativeWorkspaceNavigation.kt` contains testable, Android-free policy decisions.
- Dismissing an initial offline workspace triggers a safe first-party navigation instead of exposing a blank WebView. New shared links dismiss the workspace before navigating.
- Renderer `onResume()` is gated on Activity visibility and the absence of the native overlay; background media grants are denied. These prevent unnecessary work and unwanted prompts, but device verification is still pending.
- Long-press saved links exposes native actions, with a separate user confirmation for local deletion. The network page is not scraped or modified.

## Integration boundary (Character.AI)

- Official Android version 1.17.4 observed in a legally user-provided XAPK is React Native + Hermes + Skia + WebRTC; this is a reference for architecture, **not** permission or ability to transplant proprietary code. See `OFFICIAL_APK_AUDIT_1.17.4.md`.
- There is no supported public Character.AI chat/voice API. No code here should scrape credentials, intercept OAuth callbacks for an unregistered client, or pretend that browser Custom Tabs can transfer authentication into WebView.
- Full native live chat/calling would require supported integration from Character.AI. The independent native workspace remains functional offline.

## Choices from the 2026 Android ecosystem

- **Android Views / Kotlin**: smallest incremental binary and compatibility risk while build/device tests remain unavailable. Jetpack Compose is a valid option for a future measured, tested migration; avoid mixing two complex UI stacks prematurely.
- **System WebView**: leverages updatable Chromium and existing device implementation. GeckoView is a possible alternative but bundles another rendering engine, has larger distribution overhead and needs real OAuth/voice compatibility testing.
- **Domain blocker**: fast immutable snapshot matcher with user-controlled lists, no third-party SDK or outbound telemetry. A full ABP/EasyList engine is substantially more complex; Brave's MPL-2.0 Rust engine is a candidate for a properly isolated, licensed module **only after benchmarks and functional verification**. No code copied.
- **C++/Rust**: add only after profiling a hot path where native code improves latency without worsening crash risk/ABI size.
- **Android 17**: API 37 compilation requires a compatible AGP/Gradle/Kotlin toolchain. Current compileSdk=36 may run on Android 17 but has not been validated. Test on Pixel 8a/LineageOS before raising targetSdk.
- **Performance proof**: measure cold start, navigation, memory PSS, battery drain and frame jank on device before claiming faster than official client. Baseline Profiles/Macrobenchmark are optional future tools, not automatic CI.

## Publishing gate

1. Manual Gradle build, android SDK, dependency provenance and reproducibility.
2. APK signature, manifest permissions, version and archive validation.
3. Device QA: login, chat, keyboard/IME, media, deep links, network transitions, rendering, Android 17 and LineageOS.
4. Only then: signed APK, ZIP/TAR source, SHA256SUMS, release notes in GitHub Releases.

## v0.15.0: native offline bookmark editor and refresh policy

`OfflineBookmarkEditor` normalizes user-supplied titles and passes links through the strict first-party bookmark URL policy; this also applies to imported bookmarks in `SettingsStore`. `DeferredWebReload` is a pure Kotlin controller that records user-driven settings changes while the Activity is stopped or its offline workspace is visible, and consumes them only when appropriate. Explicit first-party navigation clears the pending refresh before moving to WebView. Neither class contacts Character.AI or invokes a native library.
