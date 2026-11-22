# Quality assurance · 2026-11-22 · v0.3.0

## Completed in offline environment

- `bash scripts/test-offline.sh` passed: **9 Python structural tests** and **49 Kotlin assertions**.
- Kotlin checks cover HTTPS and hostname boundaries, rejecting domain spoofing, protected service hosts, strict filter rules/overrides, URL query sanitization, identity URL discrimination, request counts and bounded Reader behavior.
- Static checks cover XML parsing, RU/EN parity, privacy manifest defaults, no scheduled jobs, HTTPS-only surface, atomic settings import, conservative domain rules and no Javascript bridge.
- No APK compilation, device profiling or live Character.AI login was possible; an offline unit test **does not establish functional Android compatibility**.

## Device release gates — NOT completed

1. Build debug APK with Android SDK 36 + AGP 8.13.0 + Kotlin 2.2.0 and check manifest and install on API 29 / 33 / 35 / 36.
2. Test page load and chat (with a user-owned test account), link navigation, pop-ups, real sign-in, Chat/Character search, history, settings, share and bookmarks. Treat any embedded Google/Apple OAuth failure as a known external limitation, not a bypass target.
3. Verify voice/audio capabilities *actually exposed by the website*, whether mic permission prompts function, and whether camera prompts are accurate. Never claim mobile app-only functions are available.
4. Compare baseline blocking, strict blocking and exclusions with site sign-in, downloads, ads, voice and payments. Report the domains that break functionality before increasing the default list.
5. Use the Android document picker for uploads and JSON imports (including a 1 MiB malformed input), make sure bounded read returns failure without freezing the UI. Test JSON round-trip and rejection of Google OAuth bookmarks.
6. Trigger network loss, TLS errors and renderer termination; verify recovery and that failures of noncritical third-party assets do not cover the page.
7. Test Android 15/16 edge-to-edge, portrait/landscape, keyboard resize, Android TalkBack, font scaling, 320dp narrow screens, cutouts, tablets and foldables.
8. Profile cold start / time-to-interactive, RSS, battery and data use with standard and strict modes on representative hardware. Report measured values with device model, WebView version and network type; no unmeasured claims.
9. Review release signing, support process, brand/trademark disclosure, app store policies, and dependency supply chain. No automatic release workflows or scheduling permitted.

## Known issues / limitations

- Character.AI can change the website at any time. No public API is available.
- Google embedded OAuth is frequently blocked by provider policy. External browser login will **not** import cookies into WebView.
- The official help center identifies Character Calls as a mobile app feature, so browser voice cannot be assumed.
- Blocklists do not cover in-page first-party ads, WebSockets, encrypted payload contents, redirect destinations or all service-worker behavior; aggressive blocklists can cause regressions.
- Strict ad/tracker rules and image saving modes are deliberately disabled by default.
- No screenshot or APK is supplied because an actual device or emulator runtime and SDK are absent.
