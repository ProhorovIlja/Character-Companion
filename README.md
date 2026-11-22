# Character Companion · Android

**Unofficial Android-only Character.AI web client**, v0.2.0 (source preview, 22 November 2026). MIT-licensed native Kotlin shell that loads the official `https://character.ai/` website. Not affiliated with Character.AI. No private Character.AI API, account scraping, subscription bypass, APK repackaging or credential bridge.

> **Build status:** Sources and offline tests were checked; **no Android APK has been compiled or device-tested in this environment** because the Android SDK/Gradle toolchain is unavailable. Google/Apple SSO, character calls, voices, in-chat uploads, premium features, and first-party advertising cannot be guaranteed. Do not advertise this as a production-ready APK.

## Native features (implemented in source)

| Category | Implementation |
|---|---|
| UI | Single-activity dark gold/slate interface, adaptive app icon, compact navigation mode, loading progress, error + retry |
| Navigation | In-app safe HTTPS browsing, back/forward, reload, home, open in default browser, share current URL |
| Bookmarks | Save/open/remove Character.AI URLs; stored locally, bounded to 100 |
| Privacy | Conservative host-only filter for 21 domains and subdomains, service-worker interception, allowlist and user rules, blocked requests count |
| Permissions | Deny media by default; opt in to per-request mic/video permission prompts for trusted Character.AI origins |
| Persistence | Browser cookies and local storage in WebView profile; option to clear local session, clear cache; no chat copies |
| Files | System document picker for website uploads, Android DownloadManager for HTTPS downloads |
| Personalization | Text zoom (75–175%), third-party cookies, user gesture for media, desktop mode, dark browser canvas, compact chrome |
| Portability | On-demand JSON export/import of **settings and bookmarks only** via Storage Access Framework |
| Reliability | WebView renderer-crash recovery, state restoration, no mixed content, safe browsing, untrusted links open externally |
| Languages | Browser shell uses system language for framework controls and available RU/EN string resources; some advanced settings labels are currently English-only |

The filter is not a full browser extension. It does **not** remove in-page ad containers, block every first-party ad, intercept WebSockets or bypass provider-side restrictions. Rules are static and updates are manual. No automated tasks or background monitoring.

## Build from Android Studio

1. Install recent stable Android Studio with **Android SDK Platform 36** and **JDK 17**. Gradle **8.13**, Android Gradle Plugin **8.13.0**, Kotlin **2.2.0**. The project doesn't bundle binary toolchains or Gradle wrapper JAR, to avoid distributing unverifiable binaries.
2. Open the repository directory in Android Studio and configure Gradle 8.13 in its Gradle settings. Let Android Studio sync dependencies from Google's Maven and Maven Central.
3. Use **Build → Build Bundle(s) / APK(s) → Build APK(s)** or run `gradle :app:assembleDebug` from the project directory when a compatible Gradle executable is installed.
4. Expected output after a **successful actual build**: `app/build/outputs/apk/debug/app-debug.apk`.
5. Install on Android 10+ with an updated Android System WebView. Sign in only on the real `character.ai` website.

**Offline tests:** `bash scripts/test-offline.sh` (Python 3 + optional Kotlin CLI). The Kotlin test checks domain matching, exclusions, URL policy and filter counters; structural tests inspect XML and privacy defaults. These are not substitutes for Android instrumentation tests.

## Important compatibility limitations

- Character.AI's official help center states no public API is available. The application leaves website communication to Character.AI's real web frontend.
- **Google OAuth often refuses embedded WebView sign-in (`disallowed_useragent`)**. The app has a button to open Character.AI in the user's system browser for full web compatibility, but browser cookies do not migrate back into the WebView. This cannot be safely fixed by spoofing OAuth claims or copying tokens.
- Voice calls and camera access depend on the system WebView version and what Character.AI exposes to its website. Permissions are denied by default and then can be granted per request.
- Character.AI can change its page scripts, web auth, endpoints or policies. The wrapper may need manual updates; no guarantee of future compatibility.
- Avoid distributing under an official Character.AI name or with its registered trademarks. Check the provider's terms and local laws before publication.

## Project map

`app/src/main/java/dev/caicompanion/app/`: WebView Activity, domain filter, URL policy, preferences/bookmarks. `app/src/main/res/`: dark XML interface and adaptive icon. `docs/`: security, QA, research notes. `tests/`: pure Kotlin and structural smoke tests.

## Attribution / reuse

External projects were evaluated for architecture (see `docs/RESEARCH.md`) but **no third-party source code was copied**. In particular, GPL/LGPL libraries were not directly merged into this MIT repository. Any future integration should check license compatibility, maintenance and user privacy.
