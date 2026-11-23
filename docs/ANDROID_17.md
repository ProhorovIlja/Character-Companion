# Android 17 / LineageOS device verification — source-only plan

**Status: UNVERIFIED on Android hardware.** The user reports a Pixel 8a running a recent LineageOS build with Android 17 (API 37). No device has been connected during this work. Do not represent a source review as proof of support.

## SDK/toolchain compatibility

- Android 17 = **API 37**. Android 17 was publicly released in June 2026. [Android official release](https://developer.android.com/blog/posts/android-17-is-here).
- This project intentionally retains `compileSdk = 36` and `targetSdk = 36` for the current candidate. An Android 17 runtime can normally install apps targeting earlier APIs; running is **not yet tested**.
- AGP **8.13** lists its maximum supported API as **36.1** ([AGP 8.13 notes](https://developer.android.com/build/releases/agp-8-13-0-release-notes)). Do **not** change `compileSdk = 37` alone. When changing to target API 37, migrate Android SDK Platform 37, build-tools, AGP **9.4.0** (or another verified API-37-compatible stable release) (and a compatible Gradle/JDK/Kotlin configuration) as a coordinated, separately verified upgrade. [AGP 9.4 support](https://developer.android.com/build/releases/agp-9-4-0-release-notes).
- Android 17 adds changes to audio focus/background playback and, when targeting API 37, certificate transparency and mandatory large-screen resizability. This app does not start a background audio service. Validate Character.AI's browser-controlled audio/voice features while foregrounded. [All apps](https://developer.android.com/about/versions/17/behavior-changes-all), [Target API 37](https://developer.android.com/about/versions/17/behavior-changes-17).
- Manifest marks the Activity as resizeable and does not lock orientation; device rotation, IME insets, split screen, and edge-to-edge padding require on-device tests.

## Read-only device preflight

With ADB already installed and USB debugging explicitly authorized by the user:

```bash
bash scripts/device-android17-preflight.sh
```

The command only reads OS API, whether a LineageOS version property exists, and whether `dev.caicompanion.app` is installed. It does not install, launch, collect logcat, upload, or access user data. It does **not** validate authentication or compatibility.

## Mandatory manual signoff on Pixel 8a / Android 17

1. Install the signed candidate, inspect signature fingerprint and requested permissions; record Play Protect behavior without disabling it.
2. Login using Character.AI's supported flows in system-browser mode; remember a browser Custom Tab and embedded WebView do **not** share cookies. Do not claim OAuth auto-return into the app unless the service supports and registers the corresponding app redirect.
3. Test Character.AI chat loading, scrolling, multiple conversations, media, voice/recording and permissions; compare WebView Compatibility mode and Custom Tab.
4. Rotate, switch apps, open multiwindow, open/close keyboard, try predictive back, screen-protection toggle, network-loss recovery, renderer recovery, accessibility font scales and contrast.
5. Import a 10–40k-domain list, measure WebView memory/CPU/battery with Android Profiler and check for authentication or image breakage. Compare against baseline with filter off; do not invent benchmark improvements.
6. Verify manual backup, export, file picker, downloads and service-worker requests. Test WebView version in diagnostics.
7. Add explicit device signoff only if all mandatory checks pass. Review certificate fingerprint and APK hash before shipping.

## Google Play Protect

There is **no legitimate technical switch that guarantees suppressing installation warnings** for a sideloaded APK. Google may prompt for a scan even if the APK is properly signed and harmless. Stable developer signing identity, clean permissions, reproducible artifacts and optional distribution through Google Play improve trust, but cannot promise zero prompts. Avoid disabling Play Protect or impersonating the official Character.AI app. [Google guidance](https://developers.google.com/android/play-protect/warning-dev-guidance).

## v0.12.0 source update

For Android 11–17, the shell now checks `WindowInsets.Type.ime()` instead of using window-height heuristics. The footer remains hidden until both fullscreen media and IME are dismissed. This is source-level behavior and remains unverified on the owner's Pixel 8a. The error screen can open an isolated browser session; the diagnostic report is copied only by explicit user action and excludes account/URL content.

## v0.15.0 source check

The native library can create and rename sanitized first-party bookmarks without WebView traffic. Configuration changes while the offline library is visible are queued until the web surface resumes; explicit navigation consumes the queued refresh instead of loading twice. These are JVM-tested policies only, **not Android 17 device validation**.
