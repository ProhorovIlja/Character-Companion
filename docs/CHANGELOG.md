# Changelog

## v0.4.0 — 2026-11-22 (source only)

- Security: sanitized URL sharing, copying and external-browser opening. Reject account, authorization, callback and session paths. No third-party URL is exported accidentally.
- Security: sanitize even legacy stored bookmarks at JSON export, and sanitize older last-visited preferences at read time. Renderer recovery uses sanitized first-party URLs.
- Navigation: explicit user confirmation for third-party links and HTTPS downloads. Background redirects cannot silently launch an external app.
- Media: HTML fullscreen handling in WebChromeClient, status bar fullscreen flag, back navigation and teardown. Revoke outstanding microphone/camera requests when navigation changes.
- Convenience: four one-tap, atomic browser profiles (balanced, compatibility, private, data saver) without clearing existing bookmarks/accounts.
- Manual build readiness: read-only `scripts/doctor.sh` that reports Java, Gradle, Android SDK 36 and adb. No CI, background checks, automatic tasks or event triggers.
- Expanded security and translation tests to 12 Python structural tests + 64 Kotlin policy assertions. No APK was compiled or run on an Android device.


## v0.3.0 — 2026-11-22 (source only)

- Android-only: no Windows components or service proxy.
- Bookmark sanitizer rejects OAuth/provider URLs and unknown query keys, drops fragments, requires HTTPS and standard TLS port.
- Bounded JSON input reader stops at 200,000 characters before creating the full string; import now writes one atomic SharedPreferences transaction.
- Added opt-in last page restoration; sanitized local URL is **never** included in the settings export.
- Added optional stricter tracker list (20 extra host rules, disabled by default) and optional image data saver (disabled by default).
- Better SSL error containment for third-party requests; edge-to-edge system bar insets; more accessible navigation touch targets.
- Added on-demand diagnostics of Android API, WebView package/version, Java heap size, blocked requests and filter rule count.
- Native long-press shortcuts: copy page URL; clear cache/reload; shield settings.
- Expanded RU/EN translations for advanced settings and navigation/footer.
- Added Gradle distribution metadata; do not mistake this for a complete Gradle wrapper.
- Removed accidentally tracked Python bytecode and updated `.gitignore`.
- Offline validation now includes 9 Python structural tests + 49 Kotlin assertions.

**Unresolved:** SDK build and device verification, embedded OAuth, actual site chat/voice compatibility, accessibility tests and real measured performance.

## v0.2.0 — 2026-11-22

- Initial Kotlin Android WebView client with bookmarks, privacy host rules, media permissions, file picker, downloads and manual settings backups.
