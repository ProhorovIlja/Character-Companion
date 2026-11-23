### v0.17.0-rc1 (2026-11-23) — source preview
Added manual Pixel 8a / LineageOS 24.0 compatibility diagnostics and ADB checks, runtime 4/16-KiB page-size detection, GMS package uncertainty and source-backed hardware research. Offline tests only, no APK.

# Changelog

## v0.14.0-rc1 — 2026-11-23 (source candidate; no APK)

- Fixed blank WebView when dismissing a native first-run workspace before browser navigation.
- Fixed hidden WebView being resumed by Android lifecycle while native workspace remains visible.
- Incoming Android Share links now close the workspace before navigating to a sanitized first-party URL.
- Long-press offline bookmark to open internally, open in system browser, or remove with confirmation; RU/EN accessibility strings included.
- Fixed duplicate URL search where the matching later title could be skipped during deduplication.
- User-initiated SAF settings import/export now performs potentially slow I/O off the main UI thread.
- Deny camera/microphone prompts while the Activity is hidden or the native workspace is displayed, including grants that finish after app backgrounding.
- 48 offline Python tests and 224 Kotlin policy assertions passed. No SDK build, APK signing, Google/Apple login verification or Android 17 device testing.


## v0.13.0-rc1 — 2026-11-23 (source candidate; no APK)

- New fully native offline startup workspace and searchable local bookmark library, bilingual UI, privacy shortcuts and explicit browser handoff.
- Native workspace pauses WebView while displayed; first launch defers remote site navigation until user action. Native layout uses Android Views, not Electron.
- Stored configuration can opt out of native startup; long-press Home reopens the workspace.
- Privacy-safe settings import never silently enables media capture consent.
- 48 Python tests, 212 standalone Kotlin assertions. No verified Android binary, GitHub Release, or Android 17 on-device validation.


## v0.12.0-rc1 — 2026-11-23 (source candidate; no APK)

- Android 11–17 IME tracking uses system WindowInsets, with Android 10 layout fallback.
- Footer state preserved across video fullscreen and keyboard transitions.
- Main-frame HTTP 5xx error handling with a separate browser fallback.
- Manual, bounded privacy-safe support report copy, no chat URLs or credentials.
- 47 Python tests and 200 pure Kotlin assertions passed. Android SDK build remains unavailable.


## v0.11.0-rc1 — 2026-11-23 (source candidate; no APK release)

- Added isolated, read-only XAPK inspection with basic binary Android Manifest analysis and ZIP traversal safeguards; SHA-256 verified reconstruction from original raw chunks; synthetic fixtures and tests.
- Added manual 190-MiB XAPK splitter for files larger than the 256-MiB Drive connector transfer limit. The original 278,738,234-byte Character.AI XAPK **was not downloaded**. A second smaller 180,553,623-byte XAPK was later downloaded and audited statically; findings are in `docs/OFFICIAL_APK_AUDIT_1.17.4.md`.
- Android 17 / LineageOS read-only preflight and Pixel 8a compatibility plan. The current AGP 8.13 toolchain supports `compileSdk 36` only; target SDK remains 36 pending verified migration.
- Explicit resizeable Activity and corrected manual release `aapt` choice. No signed APK, tests on hardware, scheduled operations or GitHub Release.

## v0.10.0-rc1 — 2026-11-23 (source candidate; no APK release)
- Bounded renderer restart policy with explicit retry after repeated WebView crashes.
- Manual local export of imported blocking rules to TXT, without cookies or private chat data.
- Third-party downloads use user-confirmed browser handoff rather than DownloadManager.
- Capture permission requests are denied when the Activity stops, not merely pauses for Android permission UI.
- Manual APK/signature/package/QA release packaging with source ZIP, tar.gz and SHA-256 (will refuse missing APK/device signoff). No automation, uploads or scheduled processes.
- 30 structural tests and 185 Kotlin assertions passed offline; no real APK or device tests yet.

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

## v0.16.0-rc1 — 2026-11-23 (Android source candidate)

- Lazy WebView: native offline library no longer inflates Chromium at startup; browser initialized on explicit first-party navigation only.
- Android Share Sheet links override the optional native launch screen; foreground `singleTop` sharing works without a preexisting WebView.
- WebView initialization errors offer native error/retry/browser fallback; retry cannot dereference an uninitialized WebView.
- Lifecycle handles absence of WebView during backgrounding and destruction; cookie flushing is avoided before browser creation.
- New pure Kotlin startup policy and regression tests; 50 Python tests and 243 Kotlin checks passed. Full Android APK compilation, install and Android 17 performance/compatibility still pending.
