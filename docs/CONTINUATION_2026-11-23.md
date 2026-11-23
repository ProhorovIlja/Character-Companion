# Character Companion — Android continuation checkpoint, 2026-11-23

This document is a source-continuity checkpoint, not a finished APK or a claim that any device is connected.

## Product direction

- **Android-only** independent Character.AI client, prioritizing actual site compatibility, speed, stability, privacy and an adjustable native interface. No Windows/Electron components.
- Primary target: Pixel 8a (`akita`) running LineageOS 24 (Android 17 / API 37). Do not assume the device's exact installed apps, update channel, WebView implementation, kernel page size, or browser configuration until measured on the phone.
- User-reported setup: MindTheGapps plus a separate Morphe microG variant. System Google Play Services (`com.google.android.gms`) and Morphe/ReVanced-compatible app IDs (`app.revanced.android.gms`, older `app.morphe.android.gms`) must be treated as distinct, non-attested package presence signals.
- No bypass or suppression of Google Play Protect, no privileged API replacement, no transfer of third-party identity tokens, and no plagiarism/redistribution of proprietary Character.AI code or assets. Preserve minimum permissions.
- No autonomous background jobs, scheduled tasks, CI workflows or triggered monitoring.

## Evidence verified in this iteration

- Morphe MicroG-RE upstream build on 2026-11-23 declares `ext.basePackageName = "app.revanced"`; the application `applicationId` is `${basePackageName}.android.gms`. The rename commit is `29102769010c48e4027a4159760dbf1396285a57` (2026-08-13).
- Official LineageOS GApps table lists MindTheGapps for LineageOS 24 (Android 17).
- Android Gradle Plugin 9.4 supports compile SDK API 37, but the currently pinned project AGP 8.13, Kotlin plugin and Gradle 8.13 must be migrated and compiled as a coordinated, tested change. Source-only changes do not demonstrate Android 17 runtime success.
- Previous audit of the **official Character.AI 1.17.4 XAPK** is preserved in `docs/OFFICIAL_APK_AUDIT_1.17.4.md`; it documents React Native/Hermes/Skia/WebRTC library indicators. It provides no right to reuse private code or an official login callback.

## What was changed

- Fixed current/older Morphe-compatible package visibility for opt-in device diagnostics and manual ADB preflight (no installed-app enumeration).
- Corrected release packaging versionCode/versionName drift: release script now compares its signed APK against the source Gradle metadata.
- Expanded offline Kotlin/Python regression coverage and release notes.

## Explicitly unverified

- No Android SDK or Gradle runtime was available to generate an APK here; only JDK and standalone Kotlin tooling were available.
- No device-side Pixel 8a tests, Google OAuth return, voice calls, remote Character.AI chat, performance benchmarks or Play Protect prompts have been verified.
- Do not publish a signed GitHub Release until source, signature, package identity, and manual hardware signoff are all verified.
