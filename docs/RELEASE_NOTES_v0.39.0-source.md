# Character Companion v0.39.0-source — 2026-11-23

Android 0.30.0-rc1 source update. Builds remain unverified because the current execution container does not have the Android SDK and cannot reach Google's Android SDK servers. The Windows source code is unchanged.

## Voice draft usability fixes

- **Selected language survives rotation**: RU/EN/device-language selection now travels with the in-memory unsent draft and in-progress SAF save snapshot through `onRetainNonConfigurationInstance`. The selection is validated and defaults to device language if corrupted. No speech text or language choice is saved to a Bundle, preferences, a remote service or disk.
- **Actionable speech diagnostics**: public Android `SpeechRecognizer.ERROR_*` codes are categorized into no speech, unsupported language, missing offline language model, recognizer busy/rate-limited, microphone permission, audio capture error and service/network error. The screen shows localized RU/EN suggestions while preserving the existing draft. Unknown errors retain the numeric fallback for debugging. No telemetry or transcript logging added.
- **Offline regression tests**: pure Kotlin/JVM checks for the public error codes and language index, plus two additional Python source/UI tests. Existing Geckoview/legacy tests unchanged except for the Android app version.

## Build readiness and platform facts

- Android 17 uses API 37. The Android Gradle Plugin compatibility matrix lists AGP 9.1.1+ for API 37; the separate SDK setup page has a less specific minimum-AGP warning. Toolchain migration still needs a real build. Current project builds for `compileSdk=36`, `targetSdk=36`, AGP 8.13.0, Gradle 8.13 and Kotlin plugin 2.2.0. This is an **intentional non-migration** to avoid an uncompiled, untested breaking build toolchain change. An Android 17 device can run a correctly built API-36-targeted app, but such compatibility is **not verified** here.
- On-device `SpeechRecognizer` is an Android platform API, not an offline language model bundled with Character Companion. `isOnDeviceRecognitionAvailable` does not guarantee the selected RU/EN language model is installed; the new diagnostic distinguishes language-unavailable (error 13) from unsupported (error 12). System recognizer remains a separately disclosed network-capable option.
- Real Pixel 8a / LineageOS 24 Android 17, MindTheGapps and Morphe microG speech availability has not been tested. Google OAuth, streaming voice, performance and Play Protect behavior remain unverified.
- No APK compilation, native Android signing, device install, Windows EXE, or binary GitHub Release has been performed. No automated CI, background jobs, scheduled tasks, monitors or notifications were created.

References: https://developer.android.com/reference/android/speech/SpeechRecognizer ; https://developer.android.com/build/releases/about-agp ; https://developer.android.com/about/versions/17/setup-sdk
