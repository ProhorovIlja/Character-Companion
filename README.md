# Character Companion — Android

**v0.11.0-rc1 source candidate · 23 November 2026** · Unofficial native Android shell for `https://character.ai/`, built in Kotlin. **Android only; there is no Windows build.** MIT licensed. Not affiliated with Character.AI.

**Current delivery status:** project sources, tests, Android resources and build instructions are included. Offline checks pass (45 Python unit/structural tests + the existing Kotlin policy suite). **No APK has been compiled or device-tested** in the current execution environment: the Android SDK and Gradle distribution are unavailable. Therefore real Character.AI login, voice calls, media, site rendering and Google/Apple sign-in are **not verified**. Do not label an APK production ready until these tests pass.

## v0.11.0-rc1 — Android 17 & XAPK research readiness (NOT an APK release)

- **Official XAPK located, not downloaded:** `Character+AI_+Chat,+Talk,+Text_1.17.4_APKPure.xapk` on the user's Google Drive is 278,738,234 bytes; the connected download service rejects files over 268,435,456 bytes (256 MiB). We **have not** inspected its modules, runtime, code, resources or signing certificate. See `docs/XAPK_AUDIT.md`.
- **Manual, local split & read-only audit:** `tools/split_xapk.py` creates sequential 190-MiB raw parts and a SHA-256 manifest for safe transfer. `tools/inspect_xapk.py` reassembles/verifies, inventories nested APK modules, DEX files/native ABI libraries, and decodes a strict subset of Android binary Manifest XML without running or extracting app code. Synthetic fixture tests are included; proprietary XAPK bytes are **never committed**.
- **Android 17 / LineageOS:** API 37 device preflight and Pixel 8a manual test plan in `docs/ANDROID_17.md`. Manifest explicitly allows resizing. Current `compileSdk=36`/`targetSdk=36` are retained because **AGP 8.13 max supported SDK is 36.1**; true API 37 target migration requires a verified newer build toolchain. No device compatibility claims yet.
- **Release safety:** improved `aapt` selection for signing/package validation, release notes for this source candidate, no Play Protect bypass and no background jobs. No APK / GitHub Release was published.

## v0.10.0-rc1 update (23 November 2026) — NOT an APK release

- **Renderer reliability:** bounded auto-recovery after three WebView renderer crashes in two minutes. Repeated crashes pause auto-restarts and present a retry screen rather than cycling indefinitely.
- **Portable filter lists:** user-initiated, background-thread TXT export of imported domain block/allow rules via Android Storage Access Framework, without cookies, chats or authentication data. Original user settings JSON remains separate.
- **Safer downloads:** only clean HTTPS Character.AI-owned URLs may enter Android DownloadManager; other domains are browser-only after confirmation. This reduces exposure to unattended cross-origin downloads.
- **Media permission lifecycle:** deny pending capture requests on `onStop` after the app becomes hidden; do **not** revoke on `onPause`, which can occur during a legitimate Android runtime permission dialog.
- **Manual release gate:** `scripts/prepare-release.sh` refuses unsigned/debug, wrong-package, unverified APKs or missing device signoff, and can package source ZIP/tar.gz with checksums **after** a signed and tested APK exists. It never publishes releases or schedules CI. See `docs/RELEASE_PROCESS.md` and pending `docs/RELEASE_NOTES_v0.10.0-rc1.md`.
- **Evidence:** 30 Python structure tests and 185 standalone Kotlin `check(...)` assertions pass. No Android APK compiled; no device validation or signed release available in this environment.

## v0.9.0 update (23 November 2026)

- **Two browsing paths:** the in-app WebView retains local privacy rules; a separate AndroidX Browser 1.10.0 Custom Tab runs the first-party Character.AI website with the user's **browser session** for compatibility with provider-managed Google/Apple authentication. The tab is an app-launched browser surface, not a credential bridge or a replacement for native WebView filtering.
- **Correct OAuth handoff:** any identity redirect encountered inside WebView prompts for a browser-backed session that begins at `https://character.ai/` rather than forwarding a state/nonce-bearing Google or Apple authorization URL from the wrong session. Browser cookies do **not** synchronize back to WebView.
- **Bounded browser URL policy:** Custom Tab URLs must be sanitized public Character.AI content links; the fallback is the fixed HTTPS home URL. No identity, login, password, callback or third-party URL is passed through this browser-session path.
- **Performance/privacy:** eliminated a redundant cookies-to-disk flush at the end of every page load; still flush on app pause. Incoming Android Share intents are cleared on `onNewIntent`, not only on initial activity creation. Optional saved URL is assigned its canonical sanitized form.
- **Build-chain audit:** updated the manual doctor script to verify the Gradle Wrapper JAR SHA-256. The full GitHub checkout includes the official JAR, whereas the lightweight source ZIP does not. No CI or scheduled updates.
- **Offline checks:** 27 Python structural tests + 174 Kotlin policy assertions pass on the available JVM. Android SDK/Gradle compilation and on-device browser-session behavior **have not** been tested.

**Browser-mode tradeoff:** Custom Tabs use the default browser's cookies and security model, *not* this app's WebView cookie jar, domain blocker, request statistics, or appearance settings. Do not promise filtering or silent sign-in transfer for browser-mode sessions.

## v0.8.0 update (23 November 2026)

- **Android Share Sheet receiver (opt-in):** select Character Companion from another app's "Share" menu for `text/plain`. Only public `https://character.ai/` or `https://c.ai/` content links that pass existing URL sanitization are opened; identity, foreign domains, login/callback/token paths, malformed and oversized inputs are rejected. Safe links can also be received while the Activity is on top. No broad `ACTION_VIEW` link takeover, no site scraping, and no scheduled jobs.
- **Optional screenshot/recents-thumbnail protection:** a Privacy settings toggle applies Android `FLAG_SECURE` only when the user enables it. It is off by default and does not promise to stop external camera photos or OS-level capture vulnerabilities. This setting is included in manual JSON backup and restore.
- **Less persistent sensitive state:** WebView disables automatic Android view-hierarchy state snapshots, and untrusted Share intent extras are discarded after use. The user-selected resume feature still stores only sanitized first-party page URLs.
- **More reliable file export:** writing settings now requires an actual document output stream; otherwise it reports failure rather than incorrectly confirming success. External browser launch handles security exceptions.
- **Offline validation:** 23 Python structural tests and 162 standalone Kotlin assertions. **APK and device tests remain pending** because Android SDK 36, Gradle and ADB are not available in this execution environment. These tests do not prove that website authorization or voice works.

## v0.7.0 update (22 November 2026)

- User-triggered local import of ad-host blocklists from Android's document picker. Supports bare hostnames, `hosts` entries (`0.0.0.0`/`127.0.0.1`/`::1`) and domain-only Adblock Plus `||host^` / `@@||host^` rules. This is intentionally **not** a full EasyList, cosmetic hiding, regex or URL-path filtering engine.
- Imports are bounded to 2,000,000 decoded characters, 100,000 lines and 40,000 distinct rules. Unsupported lines are counted and shown before the user confirms replacement. Invalid/oversized files never replace the old list.
- Rules are canonicalized and saved with Android `AtomicFile` in private local storage. They are **not** exported in the small settings JSON; transfer the original blocklist separately. No auto-updates or remote rule downloads.
- Filter snapshots are reused when settings do not affect the rules, so dragging the text-size slider no longer rebuilds a large ruleset. First-party Character.AI and core Google/Apple sign-in origins cannot be blocked through imported rules.
- Complete GitHub repository now includes the official Gradle 8.13 Wrapper binary verified against Gradle's published SHA-256, plus a pinned Gradle distribution SHA-256. The lightweight downloadable source ZIP deliberately omits that binary: clone GitHub for a complete wrapper, or use a verified installed Gradle 8.13.
- Offline validation: **20 structure tests and 149 standalone Kotlin assertions**. These do not establish a working APK, sign-in or Android device compatibility.

## v0.6.0 update (22 November 2026)

- Safe-download routing: any download URL with query strings, fragments, identity-provider hostnames or decoded authentication path segments stays out of Android DownloadManager. The user can explicitly open those links in an external browser; invalid/non-HTTPS URLs are rejected. This reduces credential leakage, but authenticated downloads remain unverified.
- WebView no longer has `content://` access (`allowContentAccess=false`); Android's system file picker is a separate mechanism and remains enabled.
- Added Android 13+ system back-dispatcher support. Fullscreen exits first, then in-app history navigation; on older Android versions the legacy back callback remains.
- Stopped saving/restoring WebView browser state to system Bundles. Optional resume uses only a sanitized first-party URL; no browsing history or OAuth redirect is put into the Bundle.
- Further restricted bookmarks and link sharing to opaque `char` / `id` query values and reject deeply encoded sensitive paths. Private search query strings are not exported.
- Offline verification: 17 Python structure checks + 119 standalone Kotlin assertions passed. **No APK was built or tested on Android**, due to missing SDK and Gradle in this runtime.

## v0.5.0 update (22 November 2026)

- Optimized ad-host matching to domain suffix walks rather than scanning every rule, retaining allowlist precedence.
- Added volatile, resettable per-domain rule statistics (128 entries max); no visited URLs, query strings or conversation content recorded.
- Removed all WebView cookies from system downloads, which may follow external redirects. Authenticated downloads may need a full browser.
- Added explicit Google/Apple sign-in hand-off notice; external browser sessions do not transfer to WebView automatically.
- Hardened saved/shared URLs against nested percent encoding, path traversal, controls and backslashes; sanitized download filenames.
- Removed Activity capture from the process-wide service worker hook; improved settings action buttons and switch accessibility descriptions.
- Offline checks passed: 16 structural tests and 104 Kotlin assertions. **No APK compiled or device validated**: SDK/Gradle unavailable in this runtime.

## What is implemented

| Area | Feature | Notes |
| --- | --- | --- |
| App | One Android `WebView` loading the official site | No reverse-engineered Character.AI API or credential bridge |
| Navigation | Home, back/forward, reload, share link, receive safe Android Share Sheet links, open first-party Custom Tab | External Share text is validated before loading; long-press Share copies the current link |
| Bookmarks | Local 100-entry list, save/open/delete | Only sanitized content URLs; exports and resume also sanitize legacy stored URLs |
| Interface | Dark graphite/gold native layout, adaptive launcher icon | Status/loading/error overlays, larger touch targets and Android 15/16 system bar insets |
| Profiles | Balanced / Compatibility / Private / Data saver | One-tap, atomic browser-setting changes; no background action |
| Navigation safety | External-link confirmation, safe share/copy | Background redirects cannot launch external apps; OAuth/account URLs not shared |
| Fullscreen | Native WebChromeClient HTML-media fullscreen | Website support depends on its own HTML and WebView; device test outstanding |
| Languages | English / Russian native resources | Website language remains controlled by Character.AI |
| Blocking | 21 conservative host rules + optional extra 20-host strict mode | User block/allow domains, one-off manual hostlist import, in-memory per-rule filter stats; strict mode may break widgets |
| Service workers | WebView and supported service-worker request interception | Does not block all first-party ads or WebSocket traffic |
| Privacy | WebView cookies/storage on device, configurable third-party cookies; optional FLAG_SECURE | Screenshots/recents protection is user-controlled and off by default; no analytics, VPN or background jobs |
| Media | Camera/mic denied by default; trusted origin + per-request confirmation | Requires Android runtime permission; site voice support is unverified |
| Files | Android system file picker; manual confirmation of HTTPS downloads | Query-bearing or auth-related URLs open externally; no WebView cookies sent to DownloadManager |
| Compatibility | Renderer-crash replacement, TLS rejection, retry overlay, browser-backed session | Site updates and external Google OAuth may still cause failures |
| Performance | Optional image download saver; compact chrome; text scale; media gesture | No measured performance claims without device benchmarks |
| Session | Optional resume last visited first-party page | OFF by default; stores sanitized URL locally, not in exported backups |
| Diagnostics | User-initiated device/API/WebView versions, rule count and Java VM heap | No chat text, cookies or identifiers collected |
| Backups | Manual JSON export/import of settings and bookmarks only | Limit 200,000 decoded characters, atomic import transaction, sanitized export, no passwords/chat content |

**Shortcuts:** tap shield counter for diagnostics; long-press Shield to edit host rules. Long-press Share to copy the current URL. Long-press Reload to clear cache and reload. The Settings screen provides most controls. No periodic updates, scheduled tasks, notifications or monitoring services exist.

## Build (Android Studio)

Requirements: Android Studio compatible with AGP **8.13.0**, JDK **17**, **Android SDK 36**, Gradle **8.13**, Kotlin plugin **2.2.0**, AndroidX WebKit **1.18.0**, AndroidX Browser **1.10.0**. Application target is API 36; minimum Android version API 29 (Android 10). All external dependencies are downloaded by the build tool when networking is available. The full GitHub repository includes the official verified Gradle 8.13 Wrapper JAR and a POSIX launcher. The lightweight source ZIP omits the binary JAR; use a full GitHub clone or a verified installed Gradle 8.13.

1. Clone the **full GitHub repository** (recommended), or extract the source ZIP and supply a local verified Gradle 8.13. The GitHub version includes `gradlew`, `gradle-wrapper.jar` and distribution checksum verification. Open the project root in Android Studio.
2. Install SDK Platform 36 and required build tools; accept licenses locally. Sync the project.
3. Select **Build → Build Bundle(s) / APK(s) → Build APK(s)**, or run `bash scripts/build-apk.sh` manually. With a full clone you can also run `./gradlew --no-daemon :app:assembleDebug`.
4. **Only after the build succeeds**, the debug APK will normally be at `app/build/outputs/apk/debug/app-debug.apk`. Debug APKs are for personal testing; publish a release only after signed builds and device tests.
5. Install and verify an updated Android System WebView, then run the checks in [docs/QA.md](docs/QA.md) with a test account and real device.

Manual environment check (read-only; does not install or schedule anything): `bash scripts/doctor.sh`.

Offline checks (requires Python 3; optionally Kotlin CLI):

```bash
bash scripts/test-offline.sh
```

These checks validate policy logic and source structure, **not** Android compilation.

## Important site limitations

- Character.AI does **not** provide an official public API, so the client embeds the real site. Its layout, restrictions and supported features may change.
- External link and download confirmation reduces accidental redirects, but a website can still change its markup and flows at any time. Compatibility mode disables domain blocking when troubleshooting.
- Google OAuth commonly rejects authentication in embedded WebView with `disallowed_useragent`. Opening the site in the system browser is supported, but **system-browser cookies will not transfer to WebView**. We do not spoof OAuth claims or extract tokens.
- Character Calls are documented by Character.AI as a mobile-app feature; they may not exist on the browser site or work in WebView. This app does not offer independent voice-call capability.
- Manually imported lists accept only a small domain-rule subset, without cosmetic hiding, path matching or ABP options. Aggressive lists may break chat/CDN dependencies; choose Compatibility mode or remove the import if needed.
- Domain blocking is best-effort. It does not unlock premium subscriptions, remove all first-party advertisement, interfere with payment/account protections, or rewrite traffic beyond supported WebView interceptors.
- Use an updated Android System WebView and test after any Character.AI frontend change.

The source tree and technical limitations are documented in [docs/SECURITY.md](docs/SECURITY.md), [docs/RESEARCH.md](docs/RESEARCH.md), [docs/QA.md](docs/QA.md), and [docs/CHANGELOG.md](docs/CHANGELOG.md). Third-party repositories were evaluated as references only: **no code was copied** without verification of licensing and fit.
