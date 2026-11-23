# Character Companion v0.24.0-source — Android Gecko transfers and web dialogs

Date: 2026-11-23. **Source candidate only; NOT a tested Android APK or Windows EXE.**

Android flavor `gecko` (Android app version `0.21.0-rc1`, versionCode `21`) now implements:

- HTML file uploads via the Android Storage Access Framework (`ACTION_OPEN_DOCUMENT`) for up to 8 explicitly selected `content://` documents. The active first-party origin must be unchanged during selection. No ambient storage permission, arbitrary file-path access or cookie import.
- User-confirmed downloads via GeckoView `ContentDelegate.onExternalResponse`, saving the **original Gecko response stream** through `ACTION_CREATE_DOCUMENT`. Only HTTPS first-party 2xx responses are accepted. Byte-for-byte streaming cap: **256 MiB**, with a bounded buffer; any partial output is removed on failure where the document provider permits.
- Basic native JavaScript alert, confirm, prompt and single/multiple choice dialogs to preserve usability after installing a custom Gecko prompt delegate.
- File-prompt/stream cancellation and lifecycle cleanup, sanitization of untrusted MIME types and suggested names (including Unicode bidi controls), separate RU/EN strings and extra offline regressions.
- Android incremental version increment from `0.20.0-rc1` to `0.21.0-rc1`. The overall *source bundle* version is `v0.24.0-source`.

The original Android `legacy` flavor (system WebView) and Windows WPF/CEF with local Whisper dictation remain present. No WebView2 dependency in Windows source. Gecko's Firefox engine is separate from MindTheGapps and Morphe microG; there is no promise that Google OAuth inside the embedded browser succeeds or that cookies transfer from the system browser.

## Security and limitations

- A file picker opens only for a live first-party Character.AI page and user-selected Android content URIs; folder upload is explicitly unavailable. OS picker grants, MIME filters and site behavior require device verification.
- Download saving covers first-party streaming responses. Non-first-party CDN responses are ignored rather than silently forwarded to another Android app with sensitive signed URLs. Large or long-running downloads, media fullscreen, injected custom filters, and cross-engine session parity remain unfinished.
- Generic web dialogs are bounded and user-controlled; unsupported prompt types may still be dismissed. Confirm dialog and upload behavior in the real site before calling them production-ready.
- File processing uses only a one-off worker thread after a user chooses Save. There are no scheduled jobs, recurring background tasks, notifications, monitoring services, GitHub workflows or CI triggers.
- No Android SDK / Gradle distribution or .NET SDK is available in the execution environment. Offline source tests and pure Kotlin checks do **not** compile the Android Activity/CEF native binaries or validate real-world use.
- Android 17 / LineageOS 24 on Pixel 8a `akita` is a manual compatibility target. `compileSdk`/`targetSdk` remain API 36, not API 37. Do not promise Play Protect warning suppression.

## Manual verification before a signed APK

1. Use the complete GitHub repository (the source ZIP omits Gradle's binary wrapper JAR), official Android SDK, JDK and Gradle 8.13.
2. Run `bash scripts/test-offline.sh`, then **manually** `bash scripts/build-apk.sh gecko`; inspect build diagnostics and only install a correctly signed test package on Pixel 8a.
3. Test Character.AI login, chats, file uploads (single/multiple/cancel/rotation/background), downloads (cancel/malformed MIME/size limit/network drop), dialog prompts, cookies, screenshots, microphone/camera, 60/120 Hz, process death and startup on actual Android 17.
4. Compare all essential features against `legacy`, run a .NET/CEF Windows compile, and test the local Whisper transcription pipeline independently.
5. Only after device and Windows acceptance tests pass, create signed installable releases with complete release notes and SHA-256 checks.

## Sources

- GeckoView `PromptDelegate.FilePrompt`: https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSession.PromptDelegate.FilePrompt.html
- GeckoView `ContentDelegate.onExternalResponse`: https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSession.ContentDelegate.html
- GeckoView response stream ownership: https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/WebResponse.html
