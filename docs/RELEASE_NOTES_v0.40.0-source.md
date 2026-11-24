# Character Companion v0.40.0-source — 2026-11-24

**Android v0.31.0-rc1. Source-only release, no device-tested APK or Windows EXE.** Native GeckoView ARM64 and legacy Android flavors retained; Windows WPF/CEF and Whisper source unchanged.

## Android dictation handoff

- Added an explicit **Copy and return** action to the voice-draft screen: copies the already reviewed text to Android clipboard and closes the draft screen back to the calling app. It does not inject JavaScript, paste text, access Character.AI credentials, or automatically submit the message; the user pastes and sends the text in the chat.
- Empty drafts and ClipboardManager failures keep the draft open and display localized RU/EN feedback.
- Android API 33+ sets `ClipDescription.EXTRA_IS_SENSITIVE` on user-copied text to **obscure the system clipboard preview**, following official Android documentation. **This does not make the clipboard private or prevent other eligible apps from reading it.** Source: https://developer.android.com/develop/ui/views/touch-and-input/copy-paste

## Manual APK build repairs

- Windows PowerShell manual builder now accepts **either** the canonical SHA-256-checked Gradle 8.13 wrapper **or** an independently installed Gradle reporting exactly **8.13**. Therefore a lightweight source ZIP (without the wrapper JAR) can be built on a properly configured Windows machine. Versions 8.12, 8.13.1, 9.x and unknown versions are rejected.
- Full-wrapper path now verifies the `distributionUrl` and `distributionSha256Sum` pins in addition to the JAR checksum. Linux doctor performs the same checks or verifies installed Gradle 8.13. No component is downloaded or installed by the preflight helpers.
- Linux and Windows manual debug build scripts delete stale APK output before invoking Gradle, reject an empty build result, and inspect the output for ZIP structure, `AndroidManifest.xml`, and `classes.dex` before reporting its path. This checks only minimal build artifact structure; **it is not a device, signature, or Play Protect validation**. Release packaging still refuses debug/unsigned binaries.
- Added executable foreground-only build-helper tests with fake Gradle 8.13, wrong versions, non-ZIP output, missing DEX, and stale prior APK. These are mock tests and do not compile Android.

## Validation and limitations

- Offline Python tests plus standalone executable Kotlin/JVM PolicySmoke passed; see included `-tests.txt` for the exact count and output.
- No Android SDK platform 36/37, Gradle, adb, PowerShell or Android emulator is installed in the current isolated environment. Download attempts fail DNS. **No APK or EXE was compiled, signed, installed, or tested on Pixel 8a, LineageOS 24/Android 17, MindTheGapps or Morphe microG.** App currently uses compile/target API 36; Android API 37/AGP 9.x migration requires a real build on an equipped machine. Official AGP/API support: https://developer.android.com/build/releases/about-agp
- No automatic tasks, CI workflows, background checks, monitoring or notifications were created.
