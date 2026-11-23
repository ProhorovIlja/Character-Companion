# Character Companion v0.30.0 — source-only candidate (2026-11-23)

## Windows v0.6.0-prealpha: bind dictation to original conversation

- `ComposerBridge.BuildScript(text, send, expectedPage)` now embeds a JSON-serialized expected page URL and refuses to touch a composer when `location.href` differs, even if both pages belong to `character.ai`. This is checked *in the page script* immediately before DOM interaction, guarding against navigation between validation and the CEF JavaScript execution.
- Recording start captures the first-party browser URL and an in-memory navigation revision. The "transcribe and send" action is suppressed if the user navigated while recording/transcribing, if the browser was not on a trusted first-party page at record start, or if there was a pre-existing/edited draft.
- A separate edit revision counter also detects draft text that the user edited and later reverted to its original value while Whisper was running. The recognized transcript is kept in the editable draft for manual review instead of being silently sent elsewhere.
- "Cancel recording" calls `MicrophoneRecorder.CancelAsync()`, which stops and cleans up without first resampling a WAV file or performing transcription. Failed microphone capture format attempts each use a new completion source; the `IsRecording` state is set before StartRecording to avoid a callback/start race.
- `windows/tests/bridge_runtime.js` now runs the actual generated DOM payload on 11 deterministic mocks, including same-domain wrong-chat and differing fragment cases. CEF envelope remains tested on 2 Node scenarios. All Python static/source checks rerun.

## Android

No Android product source changes in this revision. Android `gecko` and `legacy` flavors, Pixel 8a (`akita`), LineageOS 24/Android 17 support target, Morphe microG/MindTheGapps compatibility requirements and the existing Android application version `0.25.0-rc1` remain as in v0.29.0. The 82 existing Android Python tests and pure-Kotlin smoke suite are rerun; no runtime/device compatibility assertion is made.

## Verification and release blockers

- Completed: Python Android source tests, Kotlin/JVM smoke suite, Python Windows source tests, Node bridge and CEF-envelope tests; ZIP/TAR roundtrip integrity and SHA-256 manifest.
- Not completed: C# compilation, WPF/CEF startup, NAudio hardware capture or real Whisper model, Android SDK/Gradle build, account authentication, real chat delivery or device testing.
- No compiled or signed APK, EXE, installer, or GitHub binary Release is included. No automations, CI, recurring checks or background monitoring.

Because the Character.AI website may update its DOM, a successful synthetic button click is **not** proof the server accepted a message. The user should review the current conversation before manual sending. An embedded CEF profile cannot silently import Google/Firefox/Edge browser cookies.
