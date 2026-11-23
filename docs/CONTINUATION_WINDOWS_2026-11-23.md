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
