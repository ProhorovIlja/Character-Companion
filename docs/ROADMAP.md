# Priorities — verified work before feature volume

1. **P0: Android SDK build + manual live test.** Confirm AGP/Kotlin/webkit compatibility; resolve errors; debug/release APK signing (user-owned key); run smoke matrix, reproduce Character.AI login and WebRTC behavior.
2. **P1: Authentication realism.** Investigate Chrome Custom Tabs / Auth Tab for a separate full-browser mode. A Chrome session is not automatically a WebView session; cannot silently transfer OAuth tokens or claim account parity.
3. **P1: UI finish.** Native Material 3 migration if measured/validated, accessibility + navigation/icons, foldables/tablets, real screenshots, dynamic colors, consistent RU/EN.
4. **P1: Filter quality.** Manual (never scheduled) user-provided lists, checksum validation, compatible third-party filter engine review, optional cosmetic filters only after site safety tests. Protect login/purchase/voice requests.
5. **P2: Performance benchmarks.** Cold start, first meaningful paint, battery/thermal, heap use under long chats, renderer recovery on representative ARM64 devices.
6. **P2: Security.** Independent Android lint/static analysis and first-party account-flow review. No fake premium unlocks or undocumented API scraping.

**No background timers, scheduled workflows, recurring tasks, notifications or events have been created.** All changes happen only during explicit user-requested work.


## v0.5 follow-through and release acceptance criteria

Before a production release: compile with the declared Android SDK and Gradle dependencies, perform a real installation test and ensure Character.AI login works for the intended account path. Google OAuth embedded-WebView restrictions remain unresolved. Measure cold start, scroll performance, peak memory and filter overhead on actual devices, and make an explicit manual release decision.
