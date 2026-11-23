# Character Companion Windows 0.7.0 pre-alpha — synchronous DOM mutation guard

**Source only. Not built or run on Windows.**

- ComposerBridge now revalidates the original conversation URL and the live editor after focus, after native input/change dispatch and immediately before attempting Send. It refuses to click detached send controls or a button that no longer belongs to the active composer container.
- Added five Node VM regressions for navigation on focus, navigation on input, composer replacement on input, navigation while enumerating send controls and a detached send button; 16 DOM simulations in total. This verifies the extracted real C#-embedded JS, not the actual Character.AI DOM.
- Updated microphone capture from the NAudio 3 obsolete `WaveInEvent` alias to the recommended NAudio 3 `WaveIn` event-callback class, without changing the user-started offline recording behavior. See NAudio's official migration guide.
- Retained the 0.6.0 protections: original chat and draft revision bound to user-requested auto-send, cancellation without PCM normalization, strict origin controls, no WebView2.

## Unverified release gates

.NET 10 SDK / Windows, live CEF/Character.AI, real microphone devices and signing remain untested. Android is unchanged at source version 0.25.0-rc1 and Android 17 compatibility is not established by the standalone checks. **No APK/EXE or GitHub binary release exists.**
