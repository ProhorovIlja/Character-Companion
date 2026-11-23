# Character Companion for Windows — v0.1.0 pre-alpha source

**Date:** 2026-11-23. No compiled EXE, MSIX or tested installer.

## Added

- Separate native Windows 10/11 WPF desktop project targeting .NET 10 and WebView2, with an independent browser profile and explicit system-browser handoff.
- Local speech-to-text via NAudio microphone recording and user-selected whisper.cpp `whisper-cli.exe`/GGML model. Russian, English or automatic language recognition.
- Three-minute voice memo limit; local temporary WAV and text cleanup after normal transcription; no cloud speech backend or telemetry.
- Editable native transcript area with manual insert, send, copy and TXT save. Two recording-stop actions: transcribe to draft, or transcribe and attempt sending on explicit click.
- Fail-closed contenteditable/textarea bridge, unique visible composer requirement, unique labeled send-button requirement, origin checks and JSON-safe text injection.
- Conservative tracker filtering is enabled by default and can be disabled manually; it is host-only, not a full adblocking engine.
- No automatic OAuth cookie migration or impersonated identity provider. OAuth external-link handoff asks for consent and opens Character.AI home in system browser, never an in-progress token URL.

## Verified

- WPF XAML parsed as XML; handlers, dependencies, trust boundaries and microphone path checked by offline Python tests.
- All pre-existing Android source and app config preserved in this candidate; Android remains v0.19.0-rc1.

## Not yet verified

- Windows SDK compilation, WebView2 Runtime initialization, microphone formats, whisper.cpp invocation and actual Character.AI interaction. The site can change its DOM at any time.
- Google OAuth and voice calls inside Character.AI are not provided by an official public API, and are not claimed to work here.
- No signed EXE, MSIX or release build; no automatic tasks, workflows or scheduled jobs.

## Build

See `windows/README.md` for manual .NET 10 commands and explicit whisper.cpp model setup. A stable GitHub Release should wait for signed Windows artifacts and actual Windows/Character.AI tests.
