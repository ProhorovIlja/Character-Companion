# Character.AI Android 1.17.4 — read-only XAPK analysis

**Date:** 2026-11-23. **Method:** ZIP inventory, limited binary AndroidManifest decoder, native library filename inventory, static inspection of APK signing-block certificate fields. **No app code executed, no network calls, no credential extraction, and no proprietary module redistributed.**

## Provenance and reproducibility

- User supplied a second, smaller XAPK in their private Drive folder: `character-ai-1-17-4.xapk` (180,553,623 bytes); SHA-256 `f8721d48986d0ac5142c0017ba9e090b55a605d79406d3bd505df218feeb73fe`. This is **not** assumed byte-identical to the previously supplied 278,738,234-byte XAPK; that other file still exceeded the connector's 256-MiB download limit.
- Full nested inventory: **88 split APK files** (base + ABI, locale and screen-density configurations), 466,308,142 bytes if all nested modules are extracted. No path-traversal entries were reported by the ZIP auditor.
- Base module `ai.character.app.apk` SHA-256 `305be7f79bc9db9e41a591a8c118f38c04778c105636db45be0321fed2b14d30`. ARM64 module `config.arm64_v8a.apk` SHA-256 `22b1b9d9afab6fd558b05f9883b781870b7fe647b5b1f8c540f97316179e6f58`.

## Application metadata

| Metadata | Observed value |
| --- | --- |
| Android package | `ai.character.app` |
| Version / code | `1.17.4` / `162` |
| Minimum / target SDK | 24 / 36 |
| DEX files in base APK | 14 |
| Native ARM64 `.so` libraries | 47 |
| JS engine bundle | `assets/index.android.bundle` (38,103,000 bytes) |
| Declared permissions in base manifest | 42 (declarations, not necessarily granted or exercised) |

## Architecture and practical implications

Evidence includes `libNitroPlayer.so, libhermesvm.so, libjingle_peerconnection_so.so, libjsi.so, libnoise.so, libreact-native-mmkv.so, libreactnative.so, libreanimated.so, librnskia.so, libworklets.so`, plus `assets/index.android.bundle`. Together these indicate **React Native + Hermes**, native **Skia**, animations and **WebRTC** components. The official application is **not merely an Android WebView**. Therefore Character Companion's current WebView/Custom Tabs approach cannot be advertised as implementing all the official app's native calls or media functions. Creating a native UI is a separate implementation effort and requires a supported, authorized server protocol/API; never copy the proprietary bundled JS or endpoint secrets into the public client.

The base APK contains indicators of third-party advertising/attribution SDKs (examples: `assets/ad-viewer/omid-session-client-v1.js, assets/ad-viewer/omsdk-v1.js, assets/braze-html-bridge.js, assets/braze-html-in-app-message-bridge.js, assets/com/appsflyer/internal/28445207a6f5af97c-, assets/com/appsflyer/internal/2b7867f15e66c02ea-`). This is **presence only**; no network behavior, tracking activity or ad serving was observed in this offline analysis.

Declared permissions include `INTERNET`, `RECORD_AUDIO`, `CAMERA`, `POST_NOTIFICATIONS`, `AD_ID`, `SCHEDULE_EXACT_ALARM` and foreground-service permissions. Character Companion should **not** mirror unnecessary permissions or add scheduled tasks. Actual Android permission grants and runtime use were not measured.

## Identity and Android 17

- Static manifest components expose first-party `https://character.ai`, `share.character.ai` and `links.*.character.ai` deep-link families and Firebase Authentication components. These intent filters do **not** grant third-party apps the right to reuse the official app's OAuth client or callback ownership.
- A read-only parser extracted SHA-256 certificate fingerprint `e5081939b16dfa672bc3d4a02b47399205480445350e8f138730badd9fe25a5c` from APK Signing Block **v2 and v3** of the base, ARM64 and Russian split modules. This value matches older Character.AI APK metadata on [APKMirror](https://www.apkmirror.com/apk/character-ai/character-ai-chat-ask-create/character-ai-chat-ask-create-1-7-0-release/). **This is NOT proof of signature validity**; `apksigner verify --verbose --print-certs` must be run with the Android SDK when available.
- Official build targets Android API 36; the user's reported Android 17 runtime is API 37. Target 36 does not guarantee Android 17 compatibility, even for the official app. Test on the user's LineageOS device. Current Character Companion has `compileSdk=36`, `targetSdk=36`, AGP 8.13 (max supported compile API 36.1). API 37 compilation needs coordinated AGP 9.1.1+, Gradle, SDK and source migration.

## Next concrete engineering priorities

1. **Compile and device-test Character Companion** using an installed, verified SDK before announcing any APK release. Check actual login, rendering, scrolling and WebView/Custom Tab behavior on Android 17 LineageOS.
2. **Build a native accessibility and UI parity matrix** of official flows after a device walkthrough; do not assume they are web-accessible. Reuse only independently implemented UI patterns and legitimately licensed code.
3. **Treat voice/calls as a separate capability** requiring a legally usable service interface and functional device tests; bundled native WebRTC binaries alone do not provide an integration contract.
4. **Keep privacy strict but compatible:** compare permission footprint and performance *with measurements*. Do not transplant proprietary ad SDKs, analytics, signing keys or official identities.
5. **Maintain installation integrity:** stable developer signing key, `apksigner` verification, audited dependencies, no Play Protect bypass. Google Play Protect cannot guarantee no sideload prompts.

## What this report does not prove

No full JADX/Apktool decompilation, no dynamic tracing or packet capture, no `apksigner` verification, no login or OAuth return tests, no benchmark and no Android 17 installation. This report contains metadata and filenames only, **not** copyrighted APK/DEX/bundle content.
