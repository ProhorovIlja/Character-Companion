# Character Companion v0.31.0 — source release notes

Source checkpoint, 23 November 2026. Windows client 0.7.0 pre-alpha; Android GeckoView/legacy remains 0.25.0-rc1.

## Windows changes

The user-requested dictation-and-send bridge now fails closed if the target page or composer changes **during** synchronous website event callbacks (`focus`, `input` or `change`) or while querying send buttons. It verifies attachment of both the input editor and labeled send button just before clicking. It will not deliberately submit to a new chat if the page changes during an event handler. No read of messages, tokens or cookies is performed.

The desktop NAudio 3 capture class is updated from obsolete `WaveInEvent` to `WaveIn` (same event-driven recording API). Existing audio format negotiation, local streaming resampling and whisper.cpp recognition are preserved.

The JavaScript harness now exercises 16 deterministic DOM scenarios, including five new synchronous navigation/DOM replacement edge cases.

## Retained architecture and constraints

Android still uses GeckoView arm64 (`gecko`) with a separate optional WebView (`legacy`) fallback, targeting Pixel 8a `akita` with LineageOS 24/Android 17, MindTheGapps and Morphe microG. This Android version's compileSdk and targetSdk remain 36. Windows uses native WPF/.NET 10 with bundled CEF, not WebView2; self-selected local Whisper executable/model for dictation. Real Character.AI sign-in and Google OAuth remain unverified.

The standalone tests cannot establish a functional APK/EXE, actual microphone compatibility, OAuth callback, message delivery, stable browser sandboxing, or Google Play Protect behavior. No Android SDK/Gradle/ADB or .NET SDK is installed in the working environment. No signed APK or EXE, GitHub binaries, scheduled builds, CI or monitors were created. Source archives deliberately omit binary gradle-wrapper.jar; the GitHub repo contains it.
