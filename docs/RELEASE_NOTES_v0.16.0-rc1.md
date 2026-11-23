# Character Companion 0.16.0-rc1 — Android source candidate

**Status: SOURCE ONLY, not an installable/verified APK or GitHub Release.**

## New and improved

- **Lazy WebView:** cold launch into the Kotlin native bookmark library does not inflate a WebView or call its static startup APIs. Chromium initialization, service-worker interception and user-agent probing now happen only on the first explicit website navigation. This avoids unnecessary browser-engine startup when the user only manages local bookmarks; performance gains must be measured on real hardware.
- **Shared links:** a validated Character.AI link from Android `ACTION_SEND` takes priority over the optional native start screen, including delivery into an already-running `singleTop` Activity. Unsupported, login and foreign-domain URLs remain rejected.
- **Graceful browser failure:** if an Android device lacks a healthy WebView implementation, the native shell can still show an error and a separate system-browser option. Retry requests WebView initialization rather than dereferencing a missing instance.
- **Lifecycle safety:** onPause does not touch the process-wide cookie manager when WebView was never created. Native hub and Activity destruction both handle an absent WebView. Browser toolbar remains hidden while the offline hub is visible.
- Pure testable `NativeStartupPolicy` separates explicit-share priority from Activity/Android framework lifecycle.

## Technology decisions

- Kotlin/Android Views, with AndroidX WebKit 1.18.0 and Browser 1.10.0 already declared; no new native libraries or SDKs added without build and device profiling.
- Android 17 (API 37) runtime is in the test matrix; `compileSdk = targetSdk = 36` remains unchanged because AGP 8.13 is not an API-37 build configuration. A proper API-37 SDK upgrade requires a compatible Android Gradle Plugin and successful Android build.
- No Character.AI proprietary APK, DEX, resources, stolen credentials or undocumented voice APIs are used or redistributed. The previously supplied XAPK v1.17.4 informed the architecture audit only.
- No automatic updates, CI tasks, background jobs or scheduled monitoring; releases remain manual.

## Validation actually performed

- Python structural, resource, XAPK audit and lifecycle tests: **50/50 passed**.
- Pure Kotlin policy assertions: **243 `check` expressions passed** with CLI `kotlinc` and the JVM smoke test. This does not compile Android framework code.
- XML resource parse checks passed; the layout does not inflate `<WebView>` at startup.
- **Not performed:** Android SDK/Gradle build, APK signing, APK install, real Pixel 8a Android 17 tests, Character.AI Google/Apple login, voice calls, WebView compatibility/performance measurements.

## Required before GitHub Releases

Complete a manual Android SDK build; test signed APK install, privacy behavior, Character.AI website compatibility, browsers/OAuth separation and Pixel 8a/LineageOS API 37; verify APK signature/manifest/SHA-256; then attach only inspected APK plus source archives and these notes to GitHub Release. Play Protect may display normal installation/scan warnings for non-Play APKs; no bypass is provided.
