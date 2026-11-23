# Character Companion v0.23.0-source — GeckoView preview + CEF Windows

Date: 2026-11-23

**Source candidate only. No compiled APK, EXE, installer or signed GitHub Release.**

## Android 0.20.0-rc1 (source candidate)

- Added a **separate GeckoView launcher flavor for ARM64** (Pixel 8a). Mozilla GeckoView AAR is fetched from Mozilla Maven at pinned version `156.0.20260921121718` at build time; the source archive contains no Mozilla binary.
- The initial Gecko screen displays Character.AI through GeckoSession/GeckoRuntime, independent of Android WebView, with native back/forward, reload, home, share, explicit external browser approval and navigation safety.
- Gecko tracking protection toggle; microphone/camera access denied by default until explicit user opt-in, Android permission checks and a per-request site confirmation.
- Legacy flavor retained with its established Android WebView settings and features. No automatic migration of site cookies or identity tokens.
- Manual build commands are now flavor-explicit and require flavor-matched device signoff before release packaging.
- Fixed manifest launcher/share separation between flavors. No exported catch-all URL handling, no broad package visibility, no telemetry and no scheduled tasks.
- Added pure Kotlin navigation-policy tests and Python manifest/Gradle/security checks.

**Known limitations:** Gecko prototype lacks the full WebView feature parity: file picking, downloads, video fullscreen, imported custom blocklist integration, complete settings parity, crash recovery and sign-in verification. Build and device validation required. Android compile/target SDK remains 36; Android 17 API37 runtime testing pending.

## Windows v0.3.0 pre-alpha (unchanged)

- WPF/.NET 10, bundled CEF through CefSharp.Wpf.NETCore 156.0.300; no WebView2 runtime requirement by design.
- Local whisper.cpp dictation to editable draft, manual send, copy/save, Ctrl+Enter, conservative navigation and tracking rules.
- No Windows compilation or real Character.AI login, speech or delivery check; CefSharp has no Chromium sandbox.

## Checks

- Android offline 58 Python tests + Kotlin/JVM policy smoke suite pass.
- Windows 18 Python/offline source tests pass including Node-based JavaScript bridge scenarios.
- No Android SDK, Gradle, .NET SDK or connected physical devices in current execution environment. Results are **not proof** of installability, performance, authentication, call support or production readiness.

[GeckoView manual test plan](GECKOVIEW_ANDROID_PREVIEW.md) · [Renderer decision](RENDERER_DECISION_2026-11-23.md) · [GitHub source](https://github.com/ProhorovIlja/Character-Companion)
