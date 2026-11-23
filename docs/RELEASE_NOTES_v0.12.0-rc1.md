# Character Companion Android v0.12.0-rc1 — source candidate

**Status:** source-only candidate, not an installable release. No APK has been built or device-tested.

## Changes

- Android 11–17 keyboard visibility now uses platform IME WindowInsets instead of screen-height guessing. Android 10 keeps a fallback.
- Footer visibility is synchronized with fullscreen playback and keyboard state.
- Recover from HTTP 500–599 on the main frame, with a separate system browser fallback on the error screen.
- Explicit copyable, privacy-preserving support diagnostics; bounded fields, no chat content, cookies, identifiers or URLs.
- Tests for new Kotlin report logic, renderer budget and structural integrity.

## Confirmed limits

- Character.AI 1.17.4 original XAPK is React Native/Hermes/WebRTC; this independent app remains a WebView/Custom Tabs shell. Official voice calls and Google OAuth return into WebView are **not** implemented or claimed.
- API 37 compatibility, login, voice, performance and Play Protect behavior **not verified** on Pixel 8a LineageOS.
- `compileSdk=36`, `targetSdk=36`, AGP 8.13 remain until an SDK-backed migration to API 37 can be compiled and tested.
- Do not publish GitHub Release with an APK until build, signing and device QA pass.
- No scheduled tasks, background workers, automatic updates or CI jobs were added.
