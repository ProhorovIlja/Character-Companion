# Pixel 8a (akita) / LineageOS 24.0 (Android 17): verified research and compatibility

Date: 2026-11-23. **These are device-class facts and source-code observations, not a remote inspection or a passing test on the user's phone.**

## Verified hardware (Google Pixel 8a, not Pixel 8)

- Google's hardware identifier/codename for this device is `akita`.
- Google Tensor G3; **8 GB LPDDR5x RAM**.
- **6.1-inch OLED, up to 120 Hz** (the user can choose another refresh rate).
- **1080 × 2400** is the device-tree target screen size; display density and scale depend on user settings.
- Android `arm64-v8a` is an expected ABI, but use `Build.SUPPORTED_ABIS` at runtime rather than hard-coded device assumptions.
- Google lists a typical **4492 mAh** battery. This does *not* predict measured battery life of Character Companion.

Google reference: https://support.google.com/pixelphone/answer/7158570?hl=en
Google Pixel 8a device tree: https://github.com/LineageOS/android_device_google_akita/tree/lineage-24.0

## Verified LineageOS sources

- Official `LineageOS/hudson/lineage-build-targets` lists `akita userdebug lineage-24.0 W`: weekly Android 17 builds.
- `LineageOS/android_device_google_akita`, branch `lineage-24.0` exists.
- `device-akita.mk` sets `TARGET_LINUX_KERNEL_VERSION := 6.1`, inherits `device/google/zuma/common.mk`, and lists overlays for settings, display, NFC and System UI. **The common platform here is `zuma`, not `shusky`.**
- `lineage.dependencies` names `android_device_google_zuma` and `android_device_google_akita-kernels`.
- `lineage_akita.mk` lists a target screen of 1080 × 2400 and Android 17 build descriptors.
- `PRODUCT_16K_DEVELOPER_OPTION` appears in the device configuration when 16K boot is not selected. **Do not assume the user's kernel currently uses 16-KiB pages.**
- LineageOS source availability does not imply every vendor firmware component, cellular modem or Google application is open source.

Device tree: https://github.com/LineageOS/android_device_google_akita/tree/lineage-24.0
Shared platform: https://github.com/LineageOS/android_device_google_zuma/tree/lineage-24.0
Kernel packaging: https://github.com/LineageOS/android_device_google_akita-kernels
LineageOS target registry: https://github.com/LineageOS/hudson/blob/main/lineage-build-targets

## What Character Companion v0.19 can check on demand

Native interface -> **Device and ROM compatibility** (also in Settings). This is a **manual** report; nothing is collected or uploaded automatically.

- `Build.MANUFACTURER`, `MODEL`, `DEVICE`: generic product class identification. No hardware identifiers or build fingerprints.
- Real SDK level: API 37 indicates Android 17. `Build.VERSION.RELEASE` is informational only.
- ABI list as reported by Android; unfamiliar ABI strings are omitted.
- **Current** display refresh rate, not a forced 120 Hz.
- Actual running kernel page size using `Os.sysconf(_SC_PAGESIZE)` (4/16/64 KiB or unknown).
- Current Android WebView package and version. The provider can differ between LineageOS configurations.
- Presence/version of `com.google.android.gms`, which might be real Google Play Services or a replacement with the same package name. It does **not** establish valid Google OAuth or Play Integrity.
- **Separate** presence/version of Morphe MicroG-RE current `app.revanced.android.gms` and older `app.morphe.android.gms` (from MorpheApp/MicroG-RE). The upstream repository migrated the main Android application ID in commit `2910276` on 2026-08-13. It is intended for modified applications, not evidence of Character.AI authorization. Both IDs differ from MindTheGapps `com.google.android.gms`; the probe checks three exact names without scanning other applications.

The report is shown only on demand and can be copied manually. It contains no account name, serial, fingerprint, chat contents, cookies, auth token or chat URL. It does not establish that the ROM is genuinely LineageOS 24.0, that a WebView is healthy, or that Character.AI services function correctly.

A separate **manual, read-only** `bash scripts/device-android17-preflight.sh` reports API / whether `akita` is detected / ARM64 / a GMS-compatible package / whether LineageOS property is present / whether Character Companion is installed. Requires ADB and a consenting user. It never runs as a scheduled task.

## Compatibility principles

1. **Never assume Google Play services on LineageOS.** Use the browser-backed Custom Tab for Google sign-in, but Character.AI must authorize the chosen client/redirect. A Custom Tab cannot transfer browser cookies into WebView. Only the service owner can support the complete OAuth callback flow.
2. **Do not force 120 Hz.** Native Android controls should follow the system refresh-rate and reduced-motion settings. WebView owns its renderer/compositor; don't claim a native frame-rate optimization without traces.
3. **Preserve Android's system bars, cutout, font scaling, gesture navigation and IME insets.** Test at different display/text sizes and landscape.
4. **Avoid embedding arbitrary native libraries**. If Rust/C++/WebRTC is needed in the future, compile the necessary ABIs and validate 16-KiB ELF alignment and all transitive `.so` dependencies. Android 17 can enforce stricter 16-KiB compatibility. Kotlin/Java-only app code does not require an NDK just because the official Character.AI app uses native libraries.
5. **No guarantee against Play Protect prompts.** A consistent release signing key, minimal permissions, provenance, SHA-256 and appropriate distribution help establish trust but cannot turn off the platform's independent checks.
6. **Current compile SDK = 37**, **target SDK = 36**, AGP **9.3.1** after the GitHub run #2 AAR metadata failure. Android 17 runtime compatibility has not been verified; a successful GitHub build and physical device tests are still necessary.
7. **Watch RAM and battery on an actual device.** The native hub uses lazy WebView startup; no measured battery / memory claims until on-device profiling. Verify process recovery and browser back navigation.
8. Official Character.AI 1.17.4 XAPK was statically audited; its React Native/Hermes/WebRTC inventory is not an open API and its proprietary resources must not be copied into this project.

16-KiB native alignment: https://developer.android.com/guide/practices/page-sizes
Android Gradle Plugin minimums: https://developer.android.com/build/releases/about-agp

## Mandatory manual Pixel 8a / LineageOS 24.0 acceptance checks

- Launch in the offline library with cellular/Wi-Fi disabled; test adding/searching/renaming bookmarks without Chromium starting.
- Turn the network on; open site in WebView and default browser; test Google/email login paths **separately**, documenting whether the redirect returns (don't save OAuth URLs).
- Check portrait/landscape, 60/120 Hz, gesture navigation, multiple display sizes, large text, IME open/close and split-screen.
- Check full-screen video, audio, microphone/camera permission denials and approved grants, and return from the background.
- Check whether actual WebView package is installed/updated and record if Google Apps or microG is present; no assumption that Google sign-in will work.
- Try 4-KiB and (if the ROM explicitly supports it) 16-KiB mode, especially if future releases introduce NDK libraries.
- Verify device's Play Protect UI rather than claiming no warnings in advance.
- Measure cold launch, PSS memory, WebView renderer crashes, scrolling/frame pacing and battery impact before describing it as faster than the official app.

**Status:** all of the above are planned device tests, not passed device tests. No APK or GitHub Release is claimed.

## Morphe microG + MindTheGapps note (v0.19 source revision)

The user's reported combination is not the same as installing two apps named `com.google.android.gms`. Morphe MicroG-RE currently builds with `app.revanced.android.gms` (verified in the upstream main branch `build.gradle`); older builds have used `app.morphe.android.gms`. MindTheGapps supplies `com.google.android.gms`. Detect all three package IDs separately in manual on-device diagnostics. No phone installation is inferred until the report is run. A package name alone does not establish software provenance or a Google sign-in capability.

Verified upstream renaming commit: https://github.com/MorpheApp/MicroG-RE/commit/29102769010c48e4027a4159760dbf1396285a57

References:
- https://github.com/MorpheApp/MicroG-RE
- https://morpheapp.net/microg
- https://lineageos.github.io/lineage_wiki/gapps/

No installed package data is uploaded; there are no new background services or scheduled scans.


## 2026-11-23 GeckoView ARM64 development flavor (not an installed APK)

The project includes a separate `gecko` ARM64 Gradle flavor whose launcher is GeckoActivity; legacy Android System WebView remains as an explicit fallback flavor. GeckoView is pinned to 159.0.20261102144711 for ARM64 Pixel 8a (`akita`). No Android SDK, Gradle or device was available for compiling and running this variant. Pixel 8a Android 17/LineageOS, Google sign-in, media, downloads and memory impact remain unverified. Refer to [`docs/GECKOVIEW_ANDROID_PREVIEW.md`](GECKOVIEW_ANDROID_PREVIEW.md). MindTheGapps and Morphe MicroG-RE are separate components; no app blindly imports Google sessions or treats package visibility as functional Google sign-in.

## 2026-11-24 device stack refresh
Official target registry still lists akita on lineage-24.0 weekly. The LineageOS GApps guide lists MindTheGapps for Android 17, with ARM64 packages at https://github.com/MindTheGapps/17.0.0-arm64/releases . MorpheApp/MicroG-RE is an independent fork intended for patched apps (https://github.com/MorpheApp/MicroG-RE). Do not replace the installed GMS stack, infer installed patch versions, or route Character.AI OAuth through Morphe. Latest exact phone build cannot be inferred from the user's statement; official downloads page was inaccessible during research. Current release pages also have differing cache dates, so do not pin unconfirmed latest addon version numbers.

Runtime compatibility report now available in all three session modes, distinguishing actual current session engine from installed WebView provider. Manual snapshot reports OS total RAM, app heap class, low-RAM class, current battery saver, thermal state and on-device speech service availability. No background listeners, telemetry, forced refresh rates, heap enlargement, thermal bypass or automatic ROM changes. The heap class does not measure WebView/Gecko child process memory. Android reference: https://developer.android.com/topic/performance/memory/guide/webview-memory ; https://developer.android.com/reference/android/os/PowerManager . Hardware benchmark and Android build still required.

## Native APK alignment audit (2026-11-24)
The manual debug verification script now emits `native-audit-gecko.txt` or `native-audit-legacy.txt` alongside its APK, including independent ELF64 PT_LOAD and official zipalign -P 16 checks. Debug APKs remain available for diagnosis if either check fails; the report explicitly says device runtime is not tested. Production packaging fails closed on missing tools or failed alignment. The Python checker reads bounded program headers without extracting native libraries, validates ARM64/x86_64 machine IDs, segment bounds and power-of-two alignment, rejects duplicate native entries, and requires ARM64 libraries for Gecko. This does not prove actual loader/RELRO/runtime compatibility, nor replace on-device 16 KiB QA. References: https://developer.android.com/guide/practices/page-sizes ; https://developer.android.com/tools/zipalign ; https://github.com/ankidroid/Anki-Android/blob/main/.github/workflows/tests_emulator.yml . No dependency or workflow trigger added.
