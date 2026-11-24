# Character Companion v0.41.0-source — 2026-11-24

**Android v0.32.0-rc1. Source-only update. Neither an Android APK nor a Windows EXE has been compiled or tested on the target devices.** Both `gecko` (ARM64) and `legacy` Android flavors remain supported in source. Windows WPF/CEF/Whisper is unchanged.

## Voice dictation editing and accessibility

- Recognized speech is inserted at the current text cursor, or replaces the currently selected text. Previously, new speech was always appended to the bottom, even if the user moved the caret. Plain text is still edited in a native Android `EditText`: the app does not run scripts in Character.AI, paste into the website, or send chat messages automatically.
- The new pure Kotlin `VoiceDraftPolicy.insert` returns the updated text and insertion caret; it rejects blank speech, invalid selection indices and overlong results (16,000 UTF-16 code units). The insertion is adjusted to avoid splitting UTF-16 surrogate pairs, e.g., emoji, when a cursor/selection endpoint falls inside one. At the end of the draft, the existing newline-between-utterances behavior is preserved.
- If the Android text view reports an absent cursor (`-1`) while unfocused, accepted speech goes to the end rather than disappearing. The chosen language, current selection and unsaved draft survive only an ordinary configuration recreation through the Activity's volatile non-configuration instance; **none** is copied into a saved-state Bundle or persistent preferences, and the draft is not recovered after process death.
- The voice screen now uses a native `ScrollView` and a bounded-height editor so users can reach start/stop, optional system recognition, save, copy, clear and copy/return actions under a landscape/compact screen or while the keyboard is open. The IME is requested not to enter fullscreen extraction mode. Recognition status is announced politely to accessibility services. These changes are **source-only**: actual visual layout, TalkBack and keyboard behavior must still be tested on device.
- Privacy behavior from previous releases is retained: local-only recognition is preferred; system recognizer fallback requires an explicit user warning and may transmit audio to third parties; copied text remains in the system clipboard; no audio or transcript is automatically uploaded or sent.

## Verification

- Manual offline `scripts/test-offline.sh`: **99 Python unittest cases passed** and the executable Kotlin/JVM `PolicySmoke` passed. Added targeted unit assertions for insertion, selection replacement, return caret, 16k limit, invalid ranges, emoji splitting and deterministic exhaustive selections on six sample strings. These Kotlin tests actually run the pure policy logic; Android View rendering and system SpeechRecognizer APIs were not compiled or executed.
- Existing checked Android dependencies were left pinned: AGP 8.13.0, Kotlin Gradle plugin 2.2.0, Gradle 8.13, compile/target API 36, GeckoView 156.0.20260921121718, AndroidX WebKit 1.18.0. An Android 17/API 37 runtime still requires hardware verification.
- Current isolated container still has no Android SDK, Gradle distribution or `adb`; external SDK/Gradle domains fail DNS. No APK build, actual Android SDK compilation, production signing, Pixel 8a/LineageOS 24 microphone or Google OAuth check, or Windows EXE build was performed. **Passing source tests is not APK release readiness.**
- No automatic tasks, GitHub Actions CI, scheduled jobs, monitoring, notifications or background work were created.
