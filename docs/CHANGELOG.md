# Changelog

## v0.9.0 — 2026-11-23 (source only)
- Added separate first-party AndroidX Browser 1.10.0 Custom Tab browser session; login compatibility fallback shares the **browser's** cookies, not WebView's. No ad filtering or credentials are shared between them.
- Identity-provider handoff starts at the Character.AI homepage instead of forwarding an in-progress OAuth URL. Only sanitized first-party URLs can open this browser-session path.
- Fixed stale Android Share Intent retention in `onNewIntent`, avoided redundant WebView cookie flushing after each load, and made page-resume storage explicitly use a canonical sanitized URL.
- Improved manual doctor script: verify official Gradle 8.13 wrapper JAR checksum when present; no automatic downloads, CI or event triggers.
- 27 offline structure tests and 174 standalone Kotlin checks passed. APK compilation and live site still unverified.

## v0.8.0 — 2026-11-23
- Accept validated first-party Character.AI links explicitly shared as `text/plain` via the Android Share Sheet, including when the Activity is on top; discard untrusted extras after use.
- Optional `FLAG_SECURE` switch to protect screenshots/recents snapshots; default off; restore/export supported.
- Disable automatic WebView view-hierarchy state storage; keep session restoration explicitly opt-in and sanitized.
- Correct document-export error reporting when no output stream is provided; catch external-launch security exceptions.
- Offline tests: 23 structural, 162 Kotlin assertions; no Android SDK/Gradle/APK/device validation available.


- Added bounded manual ad-host list import, preview, exceptions and atomic on-device storage; no automatic subscriptions. Supports hosts and simple Adblock Plus domain rules only.
- Cached filter rule snapshots to avoid unnecessary rebuilds during UI-only settings changes. Protected key identity service hosts.
- Added verified official Gradle 8.13 Wrapper binary on GitHub, manual POSIX launcher and pinned distribution checksum; lightweight ZIP omits wrapper JAR.
- 20 Python structural tests and 149 Kotlin policy assertions pass offline. No Android APK compiled or device-validated.


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
