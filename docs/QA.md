# Quality assurance · 2026-11-23 · v0.14.0-rc1

## Completed in offline environment

- `bash scripts/test-offline.sh` passed: **48 Python structural/unit tests** and **212 Kotlin assertions**.
- Kotlin checks cover HTTPS and hostname boundaries, rejecting domain spoofing, protected service hosts, strict filter rules/overrides, URL query sanitization, identity URL discrimination, request counts and bounded Reader behavior.
- Static checks cover XML parsing, RU/EN parity, privacy manifest defaults, no scheduled jobs, HTTPS-only surface, atomic settings import, conservative domain rules and no Javascript bridge.
- New offline cases cover URL sharing sanitization (including encoded auth paths), four browser profiles, UI code contracts for external-link and download confirmation, fullscreen, permission cleanup, and legacy storage sanitization.
- `bash scripts/doctor.sh` reports the presence/absence of local SDK/Gradle/ADB without installing anything.
- No APK compilation, device profiling or live Character.AI login was possible; an offline unit test **does not establish functional Android compatibility**.

## Device release gates — NOT completed

1. Build debug APK with Android SDK 36 + AGP 8.13.0 + Kotlin 2.2.0 and check manifest and install on API 29 / 33 / 35 / 36.
2. Test new confirmation dialogs for external links and downloads, all four quick profiles, fullscreen HTML video, back button, safe share/copy of OAuth URLs and interruption of pending mic/camera permissions on navigation.
3. Test page load and chat (with a user-owned test account), link navigation, pop-ups, real sign-in, Chat/Character search, history, settings, share and bookmarks. Treat any embedded Google/Apple OAuth failure as a known external limitation, not a bypass target.
4. Verify voice/audio capabilities *actually exposed by the website*, whether mic permission prompts function, and whether camera prompts are accurate. Never claim mobile app-only functions are available.
5. Compare baseline blocking, strict blocking and exclusions with site sign-in, downloads, ads, voice and payments. Report the domains that break functionality before increasing the default list.
6. Use the Android document picker for uploads and JSON imports (including a 1 MiB malformed input), make sure bounded read returns failure without freezing the UI. Test JSON round-trip and rejection of Google OAuth bookmarks.
7. Trigger network loss, TLS errors and renderer termination; verify recovery and that failures of noncritical third-party assets do not cover the page.
8. Test Android 15/16 edge-to-edge, portrait/landscape, keyboard resize, Android TalkBack, font scaling, 320dp narrow screens, cutouts, tablets and foldables.
9. Profile cold start / time-to-interactive, RSS, battery and data use with standard and strict modes on representative hardware. Report measured values with device model, WebView version and network type; no unmeasured claims.
10. Review release signing, support process, brand/trademark disclosure, app store policies, and dependency supply chain. No automatic release workflows or scheduling permitted.

## Known issues / limitations

- Character.AI can change the website at any time. No public API is available.
- Google embedded OAuth is frequently blocked by provider policy. External browser login will **not** import cookies into WebView.
- The official help center identifies Character Calls as a mobile app feature, so browser voice cannot be assumed.
- Blocklists do not cover in-page first-party ads, WebSockets, encrypted payload contents, redirect destinations or all service-worker behavior; aggressive blocklists can cause regressions.
- Strict ad/tracker rules and image saving modes are deliberately disabled by default.
- No screenshot or APK is supplied because an actual device or emulator runtime and SDK are absent.


## v0.5 offline regression results

- 16 Python structural tests passed. Added download-cookie isolation, OAuth hand-off, service worker Activity lifetime and local statistics UI checks.
- 104 pure Kotlin policy assertions passed with the installed Kotlin 1.9 CLI; including 1000 concurrent blocked requests (four short-lived threads), unsafe filename/path regressions, exception precedence and stats reset.
- Android SDK, Gradle, adb and real Android runtime are missing; the Android app did not compile in this environment. Kotlin 1.9 standalone checks do not prove AGP 8.13 / AndroidX WebKit 1.18 project compilation.
- The UI still needs a real Android build, accessibility inspection, sign-in tests and device performance measurements. Do not distribute this as a verified APK.

## v0.6 targeted device checks — PENDING

- Android API 29, 33, 35 and 36: back gesture should exit fullscreen, then navigate history, then finish Activity. Ensure the callback unregisters on destruction.
- Verify Android document picker uploads after disabling `allowContentAccess`. Denied local `content://` page navigation should not break selected files.
- Verify direct HTTPS file download with no query; verify signed/query-bearing links show an external-browser consent dialog; reject `http:`, `blob:`, credential URLs and unexpected TLS ports.
- Test process recreation after login navigation and with opt-in resume. No complete WebView navigation state should be restored; no auth URL should be persisted.
- Test long encoded auth-like path and embedded private search query values do not appear in exported settings or shared links.
- Monitor API 33/34 predictive-back behavior and IME coexistence on actual hardware.

## v0.7 manual-filter release gates — NOT device-validated

- Import a small hosts list and domain-only ABP subset, verify supported/unsupported preview, then confirm and test blocked resource domains. Cancel must preserve prior rules.
- Import a malformed list, a >2-million-character file, >100k lines and >40k unique domains; all must fail without replacing prior rules.
- Test repeated import, rotation during file picker, process death during write and safe atomic recovery. Verify removal retains built-in rules and manual exclusions.
- Test long sessions and 10k–40k imported rules on a low-memory Android device: measure heap, UI responsiveness and `shouldInterceptRequest` latency. No benchmark claims from offline JVM tests.
- Verify Character.AI login, chat, image/CDN and WebRTC with filter enabled, then switch to Compatibility mode to troubleshoot. Google OAuth may be blocked independently of filtering.
- Build using a full clone containing the verified Gradle Wrapper JAR and a local Android SDK 36, then install the resulting APK on a test device.

## v0.8 pending manual Android validation

- From a browser or messenger share a public Character.AI character link as `text/plain`; verify Character Companion appears in the system Share Sheet and loads only the sanitized URL. Share a lookalike domain, OAuth callback and oversize text; verify rejection with no external navigation.
- Re-share while Character Companion is foreground (singleTop) and verify it uses the existing Activity. After rotation / process recreation, verify no raw Intent extras are retained.
- Toggle screen protection; check screenshots, screen recording and recent-apps thumbnail behavior. Verify disabling it restores normal screenshots.
- Export JSON to a functional Storage Access Framework provider; simulate a provider returning no stream and check the error message. Roundtrip the screen protection setting.
- Confirm Android 29/33/35/36 app installation and real Character.AI site authentication, messaging and media functions; these remain untested and must not be represented as verified.

## v0.9 browser-backed session verification — PENDING ON ANDROID

- Confirm AndroidX Browser 1.10.0 dependency resolves and Custom Tab launches on Android 10, 13, 15, 16. Test with Chrome and another browser, plus no Custom Tabs-capable browser.
- Verify the tab inherits **only the system browser** cookie session; it must not log the user into the in-app WebView. Test whether Character.AI login works in the browser tab without calling it a WebView fix.
- On Google/Apple authentication attempts inside WebView, verify the prompt opens a fresh `https://character.ai/` tab, *not* a transient OAuth redirect with `state`, `nonce`, or `code`.
- Verify browser mode does **not** block ads or show WebView-only settings, and switching back to the filtered mode does not claim browser cookies transferred.
- Launch through the Share Sheet twice on the same Activity; ensure original untrusted extras cannot be reused.
- Use Android Profiler / tracing to measure whether removing per-navigation cookie flushing improves smoothness without harming session persistence. No benchmark claims from offline tests.

## v0.10.0-rc1 source-only verification — complete (APK pending)

- 30 Python structure tests passed, including RU/EN resource parity, renderer budget hook, filter TXT export integration, release safety gate and no workflow files.
- 185 pure Kotlin assertions passed, including crash-budget window expiry, restrictions of third-party downloads, hostname boundaries, allow/block matching and import limits.
- `scripts/prepare-release.sh` was exercised with no APK and correctly refused to produce release artifacts. This is NOT a substitute for verifying a signed APK.
- Installed tools: JDK and CLI Kotlin. Missing: Android SDK Platform 36, Android build-tools, Gradle distribution, ADB. APK build and on-device compatibility remain unverified.
- Manual device signoff and a user-owned release-signing key are mandatory before creating a published GitHub Release.


## v0.11.0-rc1 — current offline source checks

- 45 Python unit/structural tests and the standalone Kotlin smoke test passed locally. These do not prove Android code compiles or runs.

- Static XAPK/AXML inspector and manual raw-part splitter tested on **synthetic** APK/XAPK payloads, including hostile path names, SHA mismatch, malformed binary manifests and nested native ABI detection. Official XAPK was not downloaded because 278,738,234 bytes exceeds the Drive transfer limit of 268,435,456 bytes.
- Android 17 diagnostic script tests use a mocked `adb`; no real phone was connected and no auth, voice, UI or battery data was gathered.
- AGP 8.13 API support max 36.1; keep target/compile SDK 36 and test runtime API 37 on a real LineageOS build before claiming compatibility.
- Check that manual source packaging rejects unsigned/debug/no-signoff APKs, and that `aapt dump badging` is used for APK metadata.


## Official Character.AI 1.17.4 static XAPK research (post-rc1 addendum)

- Received and inspected the second, 180,553,623-byte XAPK. Confirmed `ai.character.app`, 1.17.4 (162), min API 24, target API 36, 88 split APKs, 14 base DEX and 47 ARM64 libraries. See `docs/OFFICIAL_APK_AUDIT_1.17.4.md`.
- Extracted cert fingerprints from v2/v3 APK Signing Blocks of three modules and compared them to historical APKMirror listing. This is **not** cryptographic signature verification.
- The originally supplied 278,738,234-byte XAPK was never downloaded. Do not assert same content. No Android app was run, no private API was used, no Character Companion APK compiled.

## v0.12.0-rc1 source verification

- 47 Python structural/unit tests and 200 pure Kotlin assertions pass locally.
- New cases cover Android 17 IME WindowInsets usage, native error fallback, explicit diagnostic copy, sanitization of untrusted version/provider text, renderer crash counters and localized Android resources.
- No Android SDK build, lint, emulator, Google OAuth return, calls or device performance benchmark performed.

## v0.13 Android native-workspace validation — PENDING

- Start a fresh installation without internet; the native library must render with no outbound request until the user selects WebView or system browser. Test launch with empty bookmarks.
- Save up to 100 character links, search with case/space variations, close with Android Back and hardware/software keyboard, rotate, then reopen by long-pressing Home.
- Verify only sanitized first-party Character.AI bookmarks are displayed and none contain login/OAuth data or untrusted domains.
- Confirm WebView media/timers pause while hub is visible and resume after navigating back; verify text input and overlay layering on Pixel 8a Android 17.
- Export/import manual JSON; ensure hub preference survives and **camera/microphone request permission remains off after import** even if backup contained `mediaAllowed: true`.
- Measure Android app startup time, frame jank, memory PSS and battery against the v0.12 shell and official client; no performance claim until hardware measurement.
- Install debug APK from a full verified Git checkout only after real Gradle/Android SDK build; do not publish a GitHub Release until an independently signed APK passes device QA.

## v0.14 workspace / lifecycle / bookmark regressions — PENDING ON DEVICE

- Launch offline with native workspace enabled. Press Close or Android Back: the WebView must start a safe Character.AI page instead of showing a blank canvas.
- While the workspace is visible, background/foreground the application repeatedly; embedded media/timers must remain paused until the workspace is dismissed.
- Share a valid Character.AI character link from another app while Character Companion already displays its native workspace; the workspace should close and the shared page should load in WebView.
- Long-press a saved library item: open internally, open in browser, or remove after confirmation. Deleting the local link must not affect the remote Character.AI conversation.
- Import a JSON settings backup from a slow document provider; the application must remain responsive. Export must report errors when no writable SAF stream is supplied.
- From a hidden app or native workspace, ensure WebRTC microphone/camera requests cannot show a permission prompt or be granted.
- Repeat on Android 10, 13, 16 and Pixel 8a LineageOS Android 17. Confirm keyboard insets, predictive Back, rotation and accessibility.
