# Character Companion 0.15.0-rc1 — Android source candidate

**Status: SOURCE ONLY. This is NOT a verified APK or a published GitHub Release.**

## Added

- Offline-first native bookmark editor: enter a Character.AI URL and a title without opening the website. First-party HTTPS URLs are canonicalized before storage; login, OAuth, external and unsafe URLs are refused.
- Rename saved bookmarks from the native workspace's long-press menu. Local search refreshes immediately without loading a web page.
- `DeferredWebReload` lifecycle coordinator. When the offline workspace is visible or the Activity is paused, settings changes mark the WebView for a later refresh instead of starting network traffic. Explicit navigation consumes the pending refresh to avoid a double load.

## Fixed / hardened

- Saving a bookmark from the offline launch screen no longer closes and recreates the native workspace or accidentally starts Character.AI when the WebView is blank.
- Native workspace and Android IME keep browser chrome hidden when inappropriate.
- Stored or imported bookmark titles strip ISO control characters and Unicode bidirectional control characters; external/account/OAuth URLs are rejected by the common URL policy.
- English and Russian strings for offline bookmark editing and renaming.
- Release packaging version code/name advanced together to 15 / `0.15.0-rc1`.

## Technical choices

- Kotlin/JVM + Android Views remain the primary stack; AndroidX WebKit 1.18.0 and Browser 1.10.0 are pinned. No Windows, Electron, third-party Character.AI assets, secret APIs or copied APK code.
- API 37 / Android 17 runtime testing is **pending**. `compileSdk` and `targetSdk` remain 36 until a coordinated AGP 9.x / Gradle / SDK upgrade can actually be built and tested. AGP 9.4 can support API 37, but an uncompiled config bump would be misleading.
- A sideloaded APK cannot bypass or guarantee silence from Play Protect. No misleading app impersonation or bypass flags.

## Checks in this source session

- Python `unittest` suite: 49/49 passed, including structural/resources/XAPK tooling tests.
- Pure Kotlin policy assertions (239 `check(...)` calls) and JVM compilation: passed; see `tests/PolicySmoke.kt`.
- No Android SDK, Gradle distribution, adb, signing or physical Pixel 8a session available here; **no APK built or signed**.
- Character.AI browser login, chat, media, voice, OAuth redirect and Android 17 installation **not verified**.

## Before release

Build a release-signed APK with a trusted key using the official Android toolchain, run `scripts/doctor.sh`, `scripts/test-offline.sh`, conduct manual tests on Pixel 8a/LineageOS Android 17 and other supported releases, document device signoff and run `scripts/prepare-release.sh`. Only then create a GitHub Release with the APK, source archives, checksums and these notes. Do not schedule or enable CI/background jobs.
