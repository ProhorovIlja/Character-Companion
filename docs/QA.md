# Quality assurance · 2026-11-23 · v0.9.0

## Completed in offline environment

- `bash scripts/test-offline.sh` passed: **27 Python structural tests** and **174 Kotlin assertions**.
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
