## v0.34.0: stalled-download protection

The opt-in GeckoView flavor still saves only after a manual Android document-picker action. If a broken source stream repeatedly returns zero bytes despite a nonempty read buffer, the bounded copier now aborts after eight consecutive zero-length reads rather than looping forever. A subsequent successful read resets the counter. The 256-MiB cap, original-page guard and partial-file cleanup remain. These are pure Kotlin/JVM-tested invariants, not device-tested Gecko/SAF behavior.

**v0.28.0-source security update:** Gecko media permission requests require the same approved first-party HTTPS origin before and after each prompt; system runtime permission and site media callbacks have independent pending-origin snapshots. Switching media off, leaving the app or changing origins revokes pending consent. Nested encoded OAuth callback values in non-sensitive query parameters are redacted on external-browser handoff. For manual Windows-hosted Android builds see [Windows instructions](ANDROID17_MANUAL_BUILD_WINDOWS.md). Neither feature has yet been device-tested.

## v0.26.0-source: safe external-browser handoff

External navigation is still user-confirmed. The new `ExternalBrowserPolicy` prevents transfer of OAuth `code`, `state`, tokens, signed URLs and callback fragments from Gecko into the separate system browser, showing an explicit Character.AI home-page fallback instead. This **does not establish Google OAuth SSO or transfer cookies**. Source candidate only, not device-tested. Android app version `0.24.0-rc1` (`versionCode 24`).

## v0.25.0-source — native library, settings and crash recovery

The Gecko ARM64 flavor now exposes a local searchable/bookmarkable library and a native settings sheet with only implemented options (tracking protection, media consent, FLAG_SECURE, last-page resume, compact toolbar). The renderer crash/kill handlers prompt **manual** recovery with at most two attempts in two minutes; pending permissions and transfers are dropped on failure. The Activity retains the current session across orientation/screen-size changes, but other configuration changes and OS process death can still discard in-progress form content. A real Pixel 8a Android 17 device test remains mandatory. See [v0.25.0 notes](RELEASE_NOTES_v0.25.0-source.md).

# GeckoView Android preview — Pixel 8a / LineageOS 24 / Android 17

**2026-11-23 · source-only milestone. No APK build or physical-device validation.**

## What is actually implemented

Android now has two explicit Gradle flavors:

- `gecko`: launcher is `GeckoActivity` with Mozilla GeckoView as its initial browsing engine, targeting arm64-v8a for Pixel 8a (`akita`, Tensor G3). Pinned `org.mozilla.geckoview:geckoview-arm64-v8a:156.0.20260921121718` from Mozilla Maven. Adds a separate native Gecko toolbar, back/forward/reload/home, manual share, first-party navigation policy, external-browser confirmation, basic Gecko tracking protection and an explicit microphone/camera consent flow. The user's optional public page resume and screenshot-protection setting are shared with the legacy shell. **No system WebView is initialized in the normal Gecko launcher path.** The legacy browser tools are accessible explicitly for features not ported yet.
- `legacy`: original existing MainActivity, Android System WebView/AndroidX WebKit provider, manually imported domain lists, offline hub, picker, download policy and diagnostics. It remains for functional parity and fallbacks.

Both flavors use the same application ID, so **they cannot be installed side by side** without assigning separate application IDs. Gecko's cookies, app permission grants and in-process state are **not** migrated from the existing Android WebView profile. Do not promise SSO across engines. The gecko variant carries embedded ARM64 Gecko libraries and may be substantially larger.

The version in Gradle is Android `0.24.0-rc1`, `versionCode 24`; the latest multi-platform source bundle is `v0.28.0-source`. This is source development versioning, **not a GitHub Release**.

## Manual build (requires proper SDK)

```bash
# Full GitHub repository checkout; Gradle wrapper JAR must be present
bash scripts/test-offline.sh
bash scripts/build-apk.sh gecko  # ARM64 Android GeckoView debug preview
bash scripts/build-apk.sh legacy # Existing WebView fallback
```

These are **manual commands**; no GitHub Actions, background jobs, schedules, or automatic downloads are configured by the project. The Gecko AAR must be resolved from `https://maven.mozilla.org/maven2/` and its binary integrity and provenance checked by the build operator. An official Android SDK, JDK 17+, Gradle 9.3.1, Android SDK 37 and an actual Android 17 device are needed. This development environment does not include the Android SDK or Gradle distribution and **did not compile any APK**.

## Security and compatibility boundaries

- First-party navigation only: HTTPS `character.ai` or `c.ai`; unknown HTTPS domains and user-gesture popups require explicit external-browser approval. Identity-provider redirects without a clear user gesture are blocked rather than silently opening authentication URLs. Userinfo, `javascript:`, `intent:`, `file:` and non-HTTPS schemes are not navigated. OAuth through the user's browser does **not** create a Gecko authenticated session.
- Gecko's built-in tracking protection is used; **custom imported host rules from legacy WebView are not implemented in Gecko preview**. Do not claim rule parity, full cosmetic ad blocking or no ads on the site.
- Microphone/camera are off by default. The user first enables media requests, then Android permission is checked, then each Gecko media request gets its own visible prompt; no location, notifications or local-network permission is auto-approved.
- As of v0.24.0-source Gecko implements explicit SAF file selection and bounded, user-confirmed first-party downloads and basic JS prompts. It still lacks complete fullscreen video, cross-origin CDN download handling, session restore across engine crashes, full settings parity and seamless OAuth. Its toolbar includes an explicit way into the established native tools / legacy browser.
- Native Gecko profile is separate from WebView cookies. No raw cookie export/import, account token injection, private Character.AI API, or copied proprietary XAPK code.
- The current API 37 / Android 17 runtime is a **test target**; build now uses compile SDK 37, target SDK 36 and AGP 9.1.1 and must not be represented as API-37-targeted.
- Pixel 8a uses `akita`, not Pixel 8 `shiba`. Both MindTheGapps and Morphe microG may be present; they are different applications, not proof that Google OAuth works in Gecko.

## Manual acceptance plan before promoting Gecko to stable

1. Build `assembleGeckoDebug` with Gradle and verify package, ABI, signature, manifest launcher and Android 17 install without downgrading the current app accidentally. Check both Gecko and legacy flavors separately.
2. Start offline, go online, load Character.AI and navigate public pages. Confirm reflow on 120 Hz/60 Hz, large fonts, virtual keyboard, orientation and split-screen.
3. Test account login by Character.AI email, Google/Apple browser handoff, character selection, text messages, chat history, scrolling and logout. Verify no claim that the browser session transfers to Gecko.
4. Exercise media prompts with consent on/off and revoke system RECORD_AUDIO/CAMERA. Test cancelling and entering background. Record failures instead of bypassing permission prompts.
5. Compare Gecko tracking protection, API requests, downloads/uploads, media and first-party functionality with legacy; unimplemented features are release blockers for parity.
6. Profile cold start, CPU, PSS, native heap, disk/cache, battery and crash recovery on the actual Pixel 8a; do not claim Gecko is lighter/faster until measured.
7. Run accessibility/TalkBack, Play Protect and package signing tests. Android privacy and Play Protect behavior cannot be guaranteed or suppressed by app-side code.
8. Only if all tests pass, choose the default stable renderer and publish signed APK with source archives, exact hashes and full release notes.

## Evidence and sources

- [Mozilla getting started / Maven repository / Java 17](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/geckoview-quick-start.html)
- [Mozilla current navigation delegate API](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSession.NavigationDelegate.html)
- [Mozilla site and device permissions](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/permissions.html)
- [Published ARM64 GeckoView 156.0.20260921121718 artifact](https://mvnrepository.com/artifact/org.mozilla.geckoview/geckoview-arm64-v8a/156.0.20260921121718)
- [LineageOS `akita` device repository](https://github.com/LineageOS/android_device_google_akita/tree/lineage-24.0)

## v0.29.0 — stale file prompt prevention

File picker/upload responses and unconfirmed downloads are owned by the exact trusted page that initiated them. Changing the top-level location invalidates the operation immediately; any new top-level page load cancels it even if the URL is identical. Accepted document save selections still run as bounded user-initiated copies. Android SDK and Pixel 8a runtime checks remain outstanding.
