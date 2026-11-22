# Quality assurance · 2026-11-22 · v0.6.0

## Completed in offline environment

- `bash scripts/test-offline.sh` passed: **17 Python structural tests** and **119 Kotlin assertions**.
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
