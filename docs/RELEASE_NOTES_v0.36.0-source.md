# Character Companion v0.36.0-source — 2026-11-23

Android-focused source update to the existing public project. The Android app candidate becomes **0.27.0-rc1** (`versionCode=27`), with both GeckoView ARM64 and legacy browser flavors preserved. The Windows CEF/Whisper prototype is unchanged from v0.35.0-source.

## Changes

1. **Accurate host-blocking rule attribution** — a request matching `ads.example` and `specific.ads.example` is now attributed to the more specific `specific.ads.example` rule. Explicit allowed host/suffix rules still cancel blocking, including the narrower rule. Only matched domain names are counted, never full URLs or chat IDs.
2. **Safer external-browser handoff** — deeply encoded query parameters that contain nested HTTP(S) URL wrappers are redirected to the clean Character.AI home URL instead of being passed outside the app. This closes a case where an embedded OAuth/login endpoint without a recognisable token key might otherwise be forwarded. Harmless text queries and ordinary HTTPS URLs remain usable. This is intentionally conservative and may block legitimate URL-wrapper links; users can open their final public destinations directly.
3. **Regression coverage** — executable Kotlin/JVM checks cover rule specificity, allowed exceptions and two levels of URL encoding. Python integration assertions guard the Android browser policy; version and renderer-flavor checks updated.

## Verification

- `bash scripts/test-offline.sh`: 83 Android Python tests and standalone Kotlin/JVM PolicySmoke passed.
- Android/Gradle build: **NOT RUN**. Android SDK, Gradle distribution and ADB unavailable in the working environment. No installer is claimed.
- Runtime QA: **NOT RUN** on Pixel 8a (`akita`) / LineageOS 24 (Android 17 / API 37); sign-in, permissions, downloads, audio, performance and Play Protect require real device testing.
- Windows source: unchanged; self-contained CEF publish is still unverified with .NET 10 and Windows hardware.

## Release policy

This is a source snapshot, not a signed or verified APK/EXE. Do not publish a binary GitHub Release without a genuine compilation, signing verification and hardware signoff. Source-archive SHA-256 files accompany the local source bundle. Nothing installs background services or schedules CI/jobs. Continue using the existing official Character.AI service with supported login; do not impersonate the official app or attempt to defeat Play Protect.
