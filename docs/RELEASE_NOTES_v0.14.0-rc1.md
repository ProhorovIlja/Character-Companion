# Character Companion v0.14.0-rc1 — Android source candidate

**Status: source candidate, not a verified installable release.**

## Changes

- Correct first-run native workspace behavior: dismissing the offline workspace initializes a safe first-party page instead of leaving an empty WebView.
- Prevent an obscured WebView from resuming media/timers when returning to an Android Activity that still shows the native workspace.
- Route Android Share Sheet links into the visible WebView, closing the workspace before navigation.
- Long-press a saved bookmark to open in-app, open in the system browser, or delete the local link after confirmation. English/Russian accessibility hints included.
- Fix bookmark search deduplication so later titles matching a search term are discoverable even if an earlier duplicate URL did not match.
- Move explicitly requested settings import/export SAF file operations off the main UI thread; keep 200,000-character input limit.
- Reject microphone/camera web permission requests while the Activity is paused or the native workspace is covering WebView; never grant a pending request after backgrounding.

## Quality gates

- 48 Python structure/unit tests and 224 pure-Kotlin policy assertions pass locally.
- Android Gradle Plugin 8.13.0, compile/target SDK 36, min SDK 29 remain unchanged. Android 17 API 37 compilation requires a verified build-toolchain migration.
- Real APK build, signing, WebView/OAuth/site integration, calls, memory benchmarks and Pixel 8a Android 17 testing remain **unverified**.
- No scheduled tasks, CI triggers, network filtering services or third-party code/assets are bundled.

## Planned GitHub Release artifacts (not yet produced)

After manual build and device QA: developer-signed APK, source ZIP/TAR.GZ, SHA256SUMS, certificate fingerprint, build provenance, and these reviewed release notes. Do not distribute a source candidate as a functioning APK.
