# Character Companion v0.38.0-source — 2026-11-23

Android-only stability and privacy repair, based on the existing v0.37.0 source tree. Android application source is **0.29.0-rc1** (`versionCode = 29`). Windows client remains unchanged.

## Fixed

1. **Cancelled microphone sessions cannot modify a later draft.** Recognition services may enqueue callbacks before cancellation; the old code accepted `onResults`/`onError` unconditionally. A pure Kotlin `VoiceRecognitionGuard` now associates every session with a generation and rejects old, duplicate and cancelled results and errors. The guard invalidates before invoking provider cancellation, preventing a stale response from destroying the newly started recognizer or adding text after Stop/Pause.
2. **Safe TXT save across Android configuration changes.** Save now captures the selected draft at the moment of explicit user action. An Android configuration recreation retains the draft and pending snapshot only in volatile Activity non-configuration memory; no saved-instance Bundle or local disk draft is written. If Android kills the app while the SAF document picker is open, its returning result has no snapshot and the app refuses to open/truncate its output stream instead of writing empty text (the document provider may already have created an empty file).
3. **Regression coverage.** Kotlin/JVM tests exercise cancellation, new-session overwrite protection, one-shot completion and duplicate callback rejection. Python checks ensure the production Activity uses the guard in callbacks and preserves the pending save snapshot. Source version assertions updated.

## Verification boundaries

- Offline Android tests and Kotlin/JVM smoke are executable without the Android SDK; results are recorded in the accompanying test log.
- **No Android compilation, APK signing or Pixel 8a (akita)/LineageOS Android 17/API 37 device testing** was possible in this runtime: Android SDK and Gradle distribution are not installed and the SDK/Gradle download endpoints are unreachable here.
- Gradle config still uses compile/target SDK 36; migrating to API 37 without an actual compilation would hide build failures. This is a source repair, not a signed installable app. Play Protect behavior, recognition-provider availability, OAuth login and Character.AI compatibility have not been verified.
- Canonical repository includes the Gradle wrapper JAR. Lightweight downloadable archives may omit it; use the full checkout for local builds.
- No GitHub Actions, scheduled checks, monitoring, background agents or automated CI created.

## Platform reference

https://developer.android.com/reference/android/speech/SpeechRecognizer
