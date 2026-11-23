# Character Companion v0.29.0-source — Android transfer lifetime and Windows microphone normalization

2026-11-23. Source-only; not a built, signed, installable APK/EXE, nor a finished GitHub Release.

## Android / GeckoView (app version 0.25.0-rc1, versionCode 25)

- On a top-level navigation, including reloads to the same URL, GeckoView now invalidates pending Android document picker prompts and unconfirmed HTTP-response downloads. An abandoned picker cannot commit its previous URIs into a new page.
- Changes in the visible top-level URL invalidate a pending attachment if the exact initiating page no longer matches. Download consent is tied to the original first-party page snapshot until the user confirms a save. Return to the same URL later does not reauthorize an already-revoked operation.
- The document picker and download stream still require explicit user approval; 256-MiB streamed-copy cap and cleanup rules are retained. Files already being saved by an explicit ACTION_CREATE_DOCUMENT selection are a one-time foreground-initiated action; this change does not forcibly stop that copy when the page navigates.
- Added a small platform-independent `GeckoTransferOriginPolicy.kt` and executable Kotlin/JVM regression assertions for same-page, cross-subdomain, cross-site and null URL scenarios. All Gecko APIs are still **uncompiled** here; a real ARM64 Pixel 8a / Android 17 test is required.
- GeckoView is the primary ARM64 preview. The `legacy` Android WebView flavor is retained as a fallback. No login-cookie copying, proprietary Android XAPK code, or unofficial auth API was added.

## Windows / bundled CEF + local whisper.cpp (source version 0.5.0 pre-alpha)

- `WaveInEvent` now tries 48-kHz mono, 44.1-kHz mono, 16-kHz mono, then 48-kHz or 44.1-kHz stereo PCM16 capture, rather than requiring all microphones to expose 16-kHz input.
- After Stop, a bounded, constant-memory `Pcm16Resampler` averages native PCM16 frames into **16-kHz mono PCM16 WAV** for offline whisper.cpp. It handles stereo averaging, frame-aligned truncation and rejects invalid formats or more than three minutes. The original native-format temporary WAV is removed after conversion; completed recordings are still deleted after transcription/cancellation.
- Added the production converter source to the standalone .NET 10 PolicySmoke project, with five deterministic PCM input-output checks in addition to the previous 24 URL-policy assertions. This console project has **not been compiled or run** here: no .NET SDK is installed.
- Output remains a native WPF desktop window with bundled CEF (no WebView2 dependency), private browser profile, editable transcribed draft, safe explicit Insert/Send/Copy/Save, and user-confirmed speech-then-send. CEF still lacks Chromium sandbox in CefSharp; shipping requires separate security review and actual Windows tests.
- Capture fallback / normalization is a speech-oriented box-filter resampler rather than a production-proven studio DSP. Audio quality, latency, output WAV integrity and microphone driver support remain unmeasured on Windows.

## Tests and limitations

- Android Python suites, Kotlin/JVM PolicySmoke and Windows Python/Node suites were run locally. Results and checksums are recorded in `character-companion-v0.29.0-verification.json` and SHA-256 manifest distributed beside the source archives.
- This environment includes Java 21 and Kotlin CLI, but **no Android SDK, Gradle distribution, ADB, .NET SDK or Windows runtime**, and cannot resolve external package hosts. Therefore neither Android APK nor Windows EXE was compiled, signed, installed or tested. No assertion that Google/Character.AI OAuth, site chat, real voice, file transfers or Play Protect warnings are solved.
- The existing manual Gradle and .NET commands remain; no GitHub Actions, scheduler, CI or background monitoring is created.
