# Character Companion continuation — Windows voice dictation added

Date: 2026-11-23. Repository: `ProhorovIlja/Character-Companion`.

The user previously requested Android-only, optimized for Pixel 8a (`akita`), LineageOS 24/Android 17 with MindTheGapps and Morphe microG. The latest subsequent request **changes scope**: keep Android and **also** develop a Windows desktop client.

The specific missing Windows feature is microphone dictation of messages to Character.AI, like ChatGPT: record speech, transcribe to editable text, optionally save/edit it and send manually, or click send while recording to transcribe then attempt to send automatically. Prefer local, private recognition, excellent performance, stability, rich settings and native appearance. The source and any tested builds should eventually be published to GitHub Releases with notes and archives and mirrored to Google Drive `gungrain@gmail.com` in the `Character Companion — Android` project folder until a broader folder is requested.

## Completed in this iteration

- New `windows/` WPF/.NET 10 WebView2 implementation separate from Android Gradle code.
- Explicit local `whisper.cpp` CLI integration, bounded WAV capture, native editable draft, opt-in send/copy/save.
- Origin-validated DOM insertion and conservative send-button detection, without private API usage or authentication bypass.
- Independent system-browser OAuth fallback; it is **not** equivalent to seamless login return.
- Source-only Windows documentation, offline structural tests and pre-alpha release notes.

## Remaining mandatory work

1. Restore and build with official .NET 10 SDK on a Windows 10/11 machine; fix compile errors and microphone device issues.
2. Install trusted `whisper.cpp` binaries and multilingual GGML model; test Russian/English detection, recording and cancellation, and file cleanup.
3. Test actual Character.AI DOM for unique composer, only sending on the user-requested action, and preserving text on failure.
4. Test Google login without browser-cookie transfer. Automatic return to the app is not guaranteed absent Character.AI protocol support.
5. Build signed Windows installer and signed Android APK separately; release only after real QA. Do not publish unsafe or untested executables.

The attached earlier Markdown conversation history was already saved to Google Drive as `character-companion-chat-history-2026-11-23.md` in the correct folder. This file records the new Windows scope and avoids losing it in future conversations.

No scheduled work, automations, GitHub Actions, CI triggers or background checks are requested or authorized.

## Follow-up v0.21.0-source (same day)

- Windows `0.2.0-prealpha`: fix auto-send with pre-existing or edited draft, serialize bridge operations, add Ctrl+Enter and Clear Draft.
- Whisper process timeout/error cleanup now waits for termination; first-party host check rejects URL userinfo.
- Eight executable JS bridge scenarios tested in Node sandbox plus Python structure checks; these are not live DOM integration tests.
- Both environments still lack .NET SDK/Windows compilation and Android Gradle SDK; no binary signoff.
- Keep Android 17/Pixel 8a/MindTheGapps/Morphe microG requirements in scope. Do not create background/scheduled tasks.

## Follow-up v0.22.0 source: user rejects Windows WebView2

User preference: no required separate installation of Edge or WebView2 Runtime, while retaining the Character.AI embedded site and native voice dictation. WPF/.NET 10 Windows project switched to bundled CEF through CefSharp.Wpf.NETCore 156.0.300, preserving NAudio and offline whisper.cpp. Direct CEF handlers enforce first-party top-level navigation, external-HTTPS popup confirmation and optional tracker domains; JavaScript bridge now uses CEF EvaluateScriptAsync with JSON envelope.

Remember: this is SOURCE ONLY and CefSharp explicitly lacks Chromium sandbox; on Windows there is no .NET SDK to compile or verify CEF packaging, login, app size or memory. For Android the user wants independent engine research as well; GeckoView is an attractive Android-only engine, but migration requires deliberate implementation of media, downloads, permissions, cookie/profile, WebRTC and renderer recovery; original Android WebView remains a fallback until real Pixel 8a / LineageOS 24 tests. See docs/RENDERER_DECISION_2026-11-23.md.
