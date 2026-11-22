# Character Companion — Android

**v0.3.0 source preview · 22 November 2026** · Unofficial native Android shell for `https://character.ai/`, built in Kotlin. **Android only; there is no Windows build.** MIT licensed. Not affiliated with Character.AI.

**Current delivery status:** project sources, tests, Android resources and build instructions are included. Offline checks pass (9 structural Python tests + 49 Kotlin assertions). **No APK has been compiled or device-tested** in the current execution environment: the Android SDK and Gradle distribution are unavailable. Therefore real Character.AI login, voice calls, media, site rendering and Google/Apple sign-in are **not verified**. Do not label an APK production ready until these tests pass.

## What is implemented

| Area | Feature | Notes |
| --- | --- | --- |
| App | One Android `WebView` loading the official site | No reverse-engineered Character.AI API or credential bridge |
| Navigation | Home, back/forward, reload, share link, open in system browser | Long-press Share to copy URL; long-press Reload for fresh cache |
| Bookmarks | Local 100-entry list, save/open/delete | Only `character.ai` / `c.ai`; removes unknown query fields and fragments to avoid storing authorization tokens |
| Interface | Dark graphite/gold native layout, adaptive launcher icon | Status/loading/error overlays, larger touch targets and Android 15/16 system bar insets |
| Languages | English / Russian native resources | Website language remains controlled by Character.AI |
| Blocking | 21 conservative host rules + optional extra 20-host strict mode | User block/allow domains, filter counter; strict mode may break external widgets |
| Service workers | WebView and supported service-worker request interception | Does not block all first-party ads or WebSocket traffic |
| Privacy | WebView cookies/storage on device, configurable third-party cookies | No application analytics, VPN, device-wide traffic interception or background jobs |
| Media | Camera/mic denied by default; trusted origin + per-request confirmation | Requires Android runtime permission; site voice support is unverified |
| Files | Android system file picker; DownloadManager for HTTPS files | No remote files stored by companion servers |
| Compatibility | Renderer-crash replacement, TLS rejection, retry overlay | Site updates and external Google OAuth may still cause failures |
| Performance | Optional image download saver; compact chrome; text scale; media gesture | No measured performance claims without device benchmarks |
| Session | Optional resume last visited first-party page | OFF by default; stores sanitized URL locally, not in exported backups |
| Diagnostics | User-initiated device/API/WebView versions, rule count and Java VM heap | No chat text, cookies or identifiers collected |
| Backups | Manual JSON export/import of settings and bookmarks only | Limit 200,000 decoded characters, atomic import transaction, no passwords/chat content |

**Shortcuts:** tap shield counter for diagnostics; long-press Shield to edit host rules. Long-press Share to copy the current URL. Long-press Reload to clear cache and reload. The Settings screen provides most controls. No periodic updates, scheduled tasks, notifications or monitoring services exist.

## Build (Android Studio)

Requirements: Android Studio compatible with AGP **8.13.0**, JDK **17**, **Android SDK 36**, Gradle **8.13**, Kotlin plugin **2.2.0**, AndroidX WebKit **1.18.0**. Application target is API 36; minimum Android version API 29 (Android 10). All external dependencies are downloaded by the build tool when networking is available. Source does **not** include executable Gradle wrapper binaries or third-party JARs.

1. Clone/download the repository. Open its root in Android Studio and set Gradle to **8.13** using the IDE or a verified local Gradle distribution. `gradle/wrapper/gradle-wrapper.properties` records the intended distribution, but a `gradlew` executable and wrapper JAR are not shipped.
2. Install SDK Platform 36 and required build tools; accept licenses locally. Sync the project.
3. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)** or run `gradle :app:assembleDebug` with Gradle 8.13 already installed.
4. **Only after the build succeeds**, the debug APK will normally be at `app/build/outputs/apk/debug/app-debug.apk`. Debug APKs are for personal testing; publish a release only after signed builds and device tests.
5. Install and verify an updated Android System WebView, then run the checks in [docs/QA.md](docs/QA.md) with a test account and real device.

Offline checks (requires Python 3; optionally Kotlin CLI):

```bash
bash scripts/test-offline.sh
```

These checks validate policy logic and source structure, **not** Android compilation.

## Important site limitations

- Character.AI does **not** provide an official public API, so the client embeds the real site. Its layout, restrictions and supported features may change.
- Google OAuth commonly rejects authentication in embedded WebView with `disallowed_useragent`. Opening the site in the system browser is supported, but **system-browser cookies will not transfer to WebView**. We do not spoof OAuth claims or extract tokens.
- Character Calls are documented by Character.AI as a mobile-app feature; they may not exist on the browser site or work in WebView. This app does not offer independent voice-call capability.
- Domain blocking is best-effort. It does not unlock premium subscriptions, remove all first-party advertisement, interfere with payment/account protections, or rewrite traffic beyond supported WebView interceptors.
- Use an updated Android System WebView and test after any Character.AI frontend change.

The source tree and technical limitations are documented in [docs/SECURITY.md](docs/SECURITY.md), [docs/RESEARCH.md](docs/RESEARCH.md), [docs/QA.md](docs/QA.md), and [docs/CHANGELOG.md](docs/CHANGELOG.md). Third-party repositories were evaluated as references only: **no code was copied** without verification of licensing and fit.
