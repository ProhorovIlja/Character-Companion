# Character Companion v0.37.0-source — 2026-11-23

Android **0.28.0-rc1** (`versionCode=28`) introduces a **native voice draft** available in both Android flavors. Windows CEF/Whisper remains unchanged.

## New feature: privacy-aware Android dictation

- In the GeckoView ARM64 flavor, the microphone button opens a standalone native voice-draft screen; in the legacy flavor it is accessible from the native workspace and Settings. It does not read Character.AI chats or use a page DOM bridge.
- On Android API 31+ the default button checks for an available on-device speech service and uses `SpeechRecognizer.createOnDeviceSpeechRecognizer`; microphone permission is requested at the moment of use. `onPause` cancels microphone listening and cleanup destroys the recognizer.
- Choose device language, Russian or English. Edit a bounded 16,000-character draft and explicitly copy to Android clipboard or save TXT using the Android document picker. No audio files are stored; the app never uploads/transmits draft text on its own. The text is not persisted in app settings.
- If on-device recognition is absent, the optional system recognizer is available only after an explicit disclosure/confirmation. A system recognizer **may use remote servers**, and an offline preference flag is not a guarantee. Cancelled recognition never erases the prior draft.
- Automatic Character.AI send / OAuth / messaging integration is deliberately not claimed or enabled. To send, the user must paste the copied text manually. The system clipboard is handled by Android and may be accessible to other applications; copying is explicitly user-initiated.
- Added Russian/English localization, Android 11+ recognition-service package visibility, a non-exported draft Activity, bounded draft merge policy and executable pure-Kotlin tests.

## Verification

- Android Python tests: **87/87 passed**; pure Kotlin/JVM `PolicySmoke` passed after adding draft policy checks.
- XML resources and manifests parsed without errors. Both Android flavor entrypoints and the explicit recognition/privacy gates verified structurally.
- **Android SDK, Gradle distribution and ADB are unavailable in this environment.** No APK was compiled or signed. Actual recognition-provider availability, audio permission behavior, session recovery, performance and Android 17/LineageOS 24 on Pixel 8a (`akita`) require hardware verification.
- Source archive omits the binary Gradle Wrapper JAR; the canonical GitHub repository contains it. An existing full checkout plus Gradle/Android SDK 36 remains necessary for manual builds.
- Windows source remains at 0.11.0 pre-alpha without a tested EXE. No CI, timers, background monitoring, jobs or automations were created.

## Official documentation

- Android SpeechRecognizer: https://developer.android.com/reference/android/speech/SpeechRecognizer
- Android RecognizerIntent: https://developer.android.com/reference/android/speech/RecognizerIntent
