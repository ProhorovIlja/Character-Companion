# Quality gates · 2026-11-22

**Offline completed:** XML resource and manifest parse, Android-only package, privacy manifest defaults, absence of scheduled/background jobs, permissions inventory, blocked-host list sanity, pure Kotlin policy + domain-filter smoke tests.

**Not completed:** Android/Gradle compilation, Android instrumentation, device UI screenshots, login with Character.AI, Google/Apple OAuth, Android System WebView compatibility, voice calls and microphone/camera, file uploads/downloads, performance/memory profiling, Pixel/Samsung/OEM testing and Google Play policy review.

## Manual smoke test matrix

1. Android 10, 13, 15, 16 with current Android System WebView: open, navigate, back/forward, home, reload, keyboard, orientation and app resume.
2. Character.AI unauthenticated pages; sign-in routes including email, Google/Apple; verify no app password collection. If Google SSO fails in WebView, use system browser; **do not claim that its browser login signs the user into WebView**.
3. Chat typing and history, images, site menus, character search, voice and microphone if available on website. Validate on a real account only with owner's consent.
4. Enable/disable filter; ensure chat, sign-in and payments still work with static blocklist; test custom exclusions and service-worker resource loads.
5. Bookmark current URL, open saved URL, remove; export and import JSON through SAF. Reject malformed and oversized files.
6. Download HTTPS file, upload via document picker. Confirm cookies are not sent to third-party download hosts.
7. Trigger network failure, bad TLS, renderer death (`chrome://crash` in a dedicated test harness), rotation and low-memory restoration. Confirm no credential leak.
8. Profile cold-start, RAM after long session, page load times, jank, battery drain and media playback on real phones. No performance claims before measurement.
9. Accessibility: TalkBack labels, large text, landscape, color contrast, keyboard and navigation buttons.

## Known issues / next fixes

- Embedded Google OAuth may be rejected by Google itself (platform restriction).
- Embedded WebView may lack certain Character.AI native Android features, or render some calls differently.
- RU/EN base strings are present, advanced dynamic settings labels currently English.
- Advanced multi-window OAuth and in-page cosmetic ad hiding are intentionally not implemented until compatibility/legal review.
- Settings dialog native styling and edge-to-edge handling require device-based visual polish.
