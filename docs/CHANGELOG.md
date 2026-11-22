# Changelog

## v0.6.0 — 2026-11-22 (source only)

- Download policy: refuse non-HTTPS or invalid URLs; route query/fragment/auth/signed links to an explicitly confirmed external browser instead of DownloadManager, which can follow cross-origin redirects.
- Disable WebView `content://` access while preserving standard Android System File Picker support.
- Android 13+ back-dispatcher handling and cleanup for fullscreen/history navigation.
- Do not save WebView state, including potentially sensitive history and login redirects, into Activity instance-state Bundles; optional resume remains a sanitized first-party URL.
- Restrict bookmark/share query keys and values and reject deeper nested encoded paths.
- 17 offline Python tests and 119 Kotlin assertions pass; APK compilation and live-site compatibility remain unverified.

## v0.5.0 — 2026-11-22 (source only)

- Faster O(host-labels) adblock matching with correctly prioritized allowlist exceptions, no O(rule-count) scan on every resource request.
- In-memory per-rule blocking counters, maximum 128 entries; manually resettable via diagnostics/settings; no URL/query/chat telemetry.
- Removed WebView cookies from system download headers (DownloadManager can follow cross-domain redirects).
- OAuth identity provider navigations show explicit external-browser consent and session-isolation limitation. No cookie bridging or User-Agent spoof workaround.
- Stronger shared/bookmarked URL validation against double/triple percent encoded paths, dot segments, controls, backslash and credential URLs.
- Sanitized downloaded file names and removed Activity capture in the process-wide ServiceWorker interception hook.
- Improved settings buttons and TalkBack descriptions for toggles; RU/EN UI strings.
- 16 structural tests, 104 standalone Kotlin assertions (including a 1000-request concurrency test) passed offline; SDK APK build remains unverified.


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
