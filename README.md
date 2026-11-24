**Current v0.41.0-source (2026-11-24):** Android v0.32.0-rc1 improves native voice dictation: speech can be placed at the caret or replace selected text, with Unicode-safe selection and a scrollable screen that keeps the editor and controls accessible under the keyboard. Added offline Kotlin regression checks; **99 Python tests passed**. **No compiled/device-tested APK or Windows EXE yet.** [Release notes](docs/RELEASE_NOTES_v0.41.0-source.md). No background tasks, CI or automations.

**Current v0.40.0-source (2026-11-24):** Android v0.31.0-rc1 adds one-tap **Copy and return** for reviewed voice drafts, with sensitive clipboard previews on Android 13+. Manual Windows/Linux APK builders now support a pinned Gradle 8.13 install without a Wrapper JAR (lightweight source ZIPs), verify wrapper distribution pins when present, reject stale/non-ZIP APK results, and include fake-toolchain regression tests. **Still no built, installed or device-tested APK/EXE.** [Details](docs/RELEASE_NOTES_v0.40.0-source.md). No CI or automations.

**Current v0.39.0-source (2026-11-23):** Android v0.30.0-rc1 preserves the selected speech language across screen rotation (only in memory) and displays actionable Russian/English error messages for unavailable offline speech models, unsupported languages, missing microphone permission and service errors. 91 Python offline tests and executable Kotlin/JVM PolicySmoke pass. **This is source code, not a built or device-tested APK.** [Release notes](docs/RELEASE_NOTES_v0.39.0-source.md). No background automation or CI.

**Latest v0.37.0-source (2026-11-23):** Android 0.28.0-rc1 now provides a user-controlled **voice draft** on both GeckoView and legacy Android: prefer strictly on-device recognition when an Android provider exists (API 31+), choose Russian/English/device language, edit, copy or save TXT. A system speech recognizer is optional **only after a privacy warning** and may use the network. No audio is stored or auto-sent into Character.AI. Kotlin and 87 Python source tests pass; **no APK/EXE compiled or device-tested**. [Release notes](docs/RELEASE_NOTES_v0.37.0-source.md). No CI/automations.

**v0.36.0-source:** hardened external-browser URL transfer and accurate privacy-filter rule attribution. [Details](docs/RELEASE_NOTES_v0.36.0-source.md).

**v0.35.0-source (2026-11-23):** Repaired the Windows WPF/CEF self-contained subprocess launch path: an explicit `CefSharp.BrowserSubprocess.SelfHost.Main` entry point runs before WPF, `BrowserSubprocessPath` uses the app EXE, and manual publishing checks CEF data/core files. Android remains 0.26.0-rc1. [Release notes](docs/RELEASE_NOTES_v0.35.0-source.md). **No actual Windows or Android build or signed installer.** No CI, automations or scheduled tasks.

**v0.34.0-source (2026-11-23):** Android GeckoView 0.26.0-rc1 now aborts a stalled download stream instead of spinning forever; Windows CEF/Whisper 0.10.0-prealpha applies the complete OAuth sensitive-key set to nested system-browser links. [Release notes](docs/RELEASE_NOTES_v0.34.0-source.md). Source and local tests only: **no compiled or signed APK/EXE**, no verified Google login, and no CI or scheduled tasks.

**v0.33.0-source (2026-11-23):** Windows v0.9.0-prealpha now cancels local Whisper inference when its WPF window closes, rejects new auto-send operations after shutdown, and suppresses unsolicited CEF external-browser prompts lacking a direct user gesture. Android remains 0.25.0-rc1. [Release notes](docs/RELEASE_NOTES_v0.33.0-source.md). **Source only; neither APK nor EXE was compiled. No automated tasks or CI.**

**v0.32.0-source (2026-11-23):** Windows v0.8.0-prealpha now consumes the local Whisper subprocess output through bounded memory and adds a manual PowerShell .NET 10 + CEF Windows build gate. The linked-production C# PolicySmoke will compile the real Whisper transcriber, but was not executable without .NET SDK. Android is unchanged (0.25.0-rc1). [Release notes](docs/RELEASE_NOTES_v0.32.0-source.md). **Source only; no APK/EXE. No scheduled tasks or CI.**

**v0.31.0-source (2026-11-23):** Windows 0.7.0-prealpha adds a second fail-closed check after synchronous site DOM events to prevent sending dictated text if the chat or composer changed mid-script. NAudio 3 recording uses `WaveIn` instead of its deprecated alias. Android GeckoView/legacy remains at 0.25.0-rc1. [Release notes](docs/RELEASE_NOTES_v0.31.0-source.md). **Source only; no compiled APK/EXE.**

**v0.30.0-source (2026-11-23):** Windows CEF/Whisper auto-send now requires the **same conversation** at recording start, transcription end, and inside the actual DOM script. Navigation or edits (even if undone) cancel auto-send while retaining the editable transcript. Cancel Recording discards audio without resampling. Android remains unchanged at 0.25.0-rc1 (`gecko`/`legacy`), with no verified APK/EXE build. [Source notes](docs/RELEASE_NOTES_v0.30.0-source.md) · [Windows](windows/RELEASE_NOTES_v0.6.0-prealpha.md). No scheduled jobs, CI, or background monitoring.

**v0.29.0-source (2026-11-23):** Android GeckoView now cancels pending SAF file choosers and unconfirmed downloads upon navigation/reload; Windows CEF/Whisper source now tries multiple native PCM16 microphone formats and converts captures locally to 16-kHz mono PCM16 for offline transcription. Production conversion logic is linked into a manual .NET PolicySmoke console test, but **no APK/EXE or C# build has been produced/tested**. [Release notes](docs/RELEASE_NOTES_v0.29.0-source.md) · [Windows changes](windows/RELEASE_NOTES_v0.5.0-prealpha.md). No scheduled jobs or CI.

**v0.28.0-source (2026-11-23):** Android GeckoView now binds media approval to the exact first-party HTTPS origin at request time and keeps Android runtime permission and Gecko media prompts independent; nested OAuth values are redacted from external-browser links. Added a manual Windows PowerShell Android build path with a SHA-256-checked Gradle Wrapper. **Still source-only: no compiled APK/EXE, device tests or signed GitHub Release.** [Release notes](docs/RELEASE_NOTES_v0.28.0-source.md) · [Windows build instructions](docs/ANDROID17_MANUAL_BUILD_WINDOWS.md). No automations or CI.

**v0.27.0-source (2026-11-23):** Windows CEF now redacts sensitive OAuth handoff URLs consistently with the Android Gecko policy, including encoded query keys, callback paths and fragment credentials. Added an actual C# 24-case source-linked policy smoke project for manual .NET 10 builds, plus source-level regressions. Android Gecko/legacy app 0.23.0-rc1 remains unchanged. **No signed APK or Windows EXE; .NET policy smoke has not yet been compiled.** [Source notes](docs/RELEASE_NOTES_v0.27.0-source.md). No CI, background checks or scheduled tasks.

**v0.26.0-source (2026-11-23):** Android GeckoView no longer forwards OAuth codes, session tokens, callback URLs or signed redirect parameters into a separate system-browser session; sensitive handoffs become an explicit Character.AI-homepage offer. A crashed Gecko session cannot receive a new Share-intent navigation before manual recovery. Android app version 0.23.0-rc1; Windows CEF/Whisper is unchanged. **No compiled, signed or device-tested APK/EXE.** [Source notes](docs/RELEASE_NOTES_v0.26.0-source.md). No CI, monitoring, scheduled tasks or automations.

**v0.25.0-source (2026-11-23):** Android GeckoView now has a native searchable offline bookmark library, persistent privacy/media/compact navigation settings and **manual** Gecko process crash recovery with a bounded retry budget. Android app version 0.22.0-rc1. Windows WPF/CEF with local Whisper is unchanged. **No APK or EXE was compiled or tested on devices.** [Release notes](docs/RELEASE_NOTES_v0.25.0-source.md). No CI or scheduled tasks.

**v0.24.0-source (2026-11-23):** GeckoView now has Android SAF attachment picking (single/multiple, explicit consent), bounded first-party streamed document saves (256 MiB), and basic JavaScript dialog UI. Android app version 0.21.0-rc1; the legacy WebView variant and the Windows WPF/CEF offline Whisper source remain available. **No compiled/tested APK or EXE yet.** [Release notes](docs/RELEASE_NOTES_v0.24.0-source.md). No scheduled jobs or CI.

**v0.23.0-source / Android 0.20.0-rc1 GeckoView preview (2026-11-23):** Added a distinct `gecko` ARM64 Android flavor with Mozilla GeckoView as its launcher/browser and `legacy` flavor preserving the previous Android WebView implementation. Native navigation, media permission gate and Gecko tracking protection are implemented in source; missing feature parity is documented, **no APK has been built or installed**. Windows CEF/Whisper prototype remains at v0.3.0. [Gecko preview and limitations](docs/GECKOVIEW_ANDROID_PREVIEW.md) · [New source notes](docs/RELEASE_NOTES_v0.23.0-source.md).

**v0.22.0 source / Windows v0.3.0 pre-alpha (2026-11-23):** Migrated Windows from WebView2 to bundled CEF (CefSharp.Wpf.NETCore 156.0.300). The design needs no separately installed WebView2 Runtime but carries a larger browser bundle and CefSharp has no Chromium sandbox. The native Whisper dictation and editable draft remain. Android is unchanged; GeckoView is a researched migration candidate, **not implemented**. **Neither EXE nor APK has been built or device-tested.** [Renderer decision](docs/RENDERER_DECISION_2026-11-23.md) · [Windows](windows/README.md).

**Windows v0.2.0 source revision:** speech-to-send now avoids sending existing or edited draft; concurrent send requests are blocked. Ctrl+Enter and Clear Draft added. The 8-scenario JS DOM harness can be run with Node. [Details](windows/README.md). **No tested installers yet.**

**Windows client added (source prototype):** See [windows/README.md](windows/README.md) for native WPF/CEF and offline Whisper dictation. The original Android client remains separate and unchanged in v0.19.0-rc1. No Windows executable has been built or tested yet.

> **v0.19.0-rc1 source candidate:** updated Morphe MicroG-RE package detection for the current `app.revanced.android.gms` and legacy IDs; fixed manual APK release-version drift. No tested APK or guaranteed Google login yet.

# Character Companion — Android

**v0.17.0-rc1 — Pixel 8a / LineageOS 24.0 runtime compatibility (2026-11-23).** Added opt-in native device diagnostics (real Android API, `akita` detection, WebView provider, ABI, display refresh, memory page size and GMS-compatible package visibility), read-only manual ADB checks and 16-KiB/Android 17 compatibility research. **Google OAuth, Android 17 device validation, APK build and Play Protect prompts remain unverified.** [Pixel 8a / LineageOS dossier](docs/PIXEL8A_LINEAGEOS_24.md) · [Release notes](docs/RELEASE_NOTES_v0.17.0-rc1.md).

**v0.16.0-rc1 — source candidate (23 November 2026).** Native cold start now defers WebView creation, Chromium startup, service worker and user-agent initialization until the first explicit website navigation. Android Share links bypass the offline home when valid; missing WebView provider errors can be retried without dereferencing an uninitialized WebView. 50 Python tests and 243 Kotlin smoke assertions pass offline. **APK not built or device tested.** [Notes](docs/RELEASE_NOTES_v0.16.0-rc1.md) · [Android 17](docs/ANDROID_17.md).

**v0.15.0-rc1 source candidate · 23 November 2026.** Native offline bookmark creation and rename, deferred website refresh while offline, stricter imported bookmark titles, Russian/English strings and 49 passing Python tests plus JVM Kotlin policy tests. **Android-only; no signed or device-tested APK yet.** [Release notes](docs/RELEASE_NOTES_v0.15.0-rc1.md) · [Native architecture](docs/NATIVE_ARCHITECTURE.md) · [Android 17 checklist](docs/ANDROID_17.md).

**v0.14.0-rc1 source candidate (23 November 2026).** Fixes native workspace lifecycle, shared-link routing, local bookmark actions, and asynchronous manual settings backups; microphone/camera prompts are blocked while the app or workspace is hidden. Offline tests pass. No verified APK or Android 17 device test. See [native architecture](docs/NATIVE_ARCHITECTURE.md) and [release notes](docs/RELEASE_NOTES_v0.14.0-rc1.md).


**Previous v0.12.0-rc1 source candidate · 23 November 2026** · Unofficial native Android shell for `https://character.ai/`, built in Kotlin. **Android only; there is no Windows build.** MIT licensed. Not affiliated with Character.AI.

**Current delivery status:** project sources, tests, Android resources and build instructions are included. Current offline checks pass (50 Python tests + 243 pure Kotlin `check` expressions). **No APK has been compiled or device-tested** in the current execution environment: the Android SDK and Gradle distribution are unavailable. Therefore real Character.AI login, voice calls, media, site rendering and Google/Apple sign-in are **not verified**. Do not label an APK production ready until these tests pass.

## v0.11.0-rc1 — Android 17 & XAPK research readiness (NOT an APK release)

- **Official APK research now performed:** the original 278,738,234-byte XAPK remains above the Drive connector download limit, but a separately supplied 180,553,623-byte `character-ai-1-17-4.xapk` was successfully read. Static inventory confirms Character.AI v1.17.4 / code 162 / target API 36, 88 APK splits and React Native/Hermes/WebRTC components. The build was not executed or signature-verified. See [docs/OFFICIAL_APK_AUDIT_1.17.4.md](docs/OFFICIAL_APK_AUDIT_1.17.4.md).
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

## v0.12.0-rc1 (source-only) — Android 17 usability and explicit diagnostics

- Android 11–17 use WindowInsets IME visibility rather than a fixed keyboard-height guess; Android 10 retains a fallback. Fullscreen media and keyboard visibility now share footer state.
- Main-frame HTTP 5xx errors provide a retry and a browser-session fallback. OAuth and errors on subresources are not treated as a page failure.
- Diagnostics may be copied **only on explicit user action**. The bounded report contains app/WebView version, Android API, rule counts, session-only block and renderer counters. It omits URLs, account identifiers, cookies, message content and device IDs.
- Source-only RC; no built/tested APK or GitHub Release. Android SDK/Gradle/ADB and real Android 17 hardware tests are still required.
