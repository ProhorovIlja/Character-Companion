# Windows pre-alpha acceptance tests (manual; run only on user request)

Before publishing any EXE/MSIX, complete every item on an actual Windows 10/11 desktop:

1. Build with .NET 10 SDK; verify NuGet versions and bundled CEF native binaries, then launch an unpackaged build with standard-user privileges. No administrator rights or startup services.
2. Navigate Character.AI main page; test login with an explicitly supported method. Test Google login only via system browser; verify no session claims or cookie import are made.
3. Confirm First Party top-level navigation blocks `http:`, `file:`, `javascript:` and outside domains; test user confirmation before external link opened.
4. Test filter enabled/disabled without breaking official character chat/images; measure network requests before claiming savings.
5. Test three minute microphone cap; unplug microphone, deny access, choose invalid CLI/model, use non-ASCII paths and cancellation.
6. Test Russian, English and auto multilingual speech with whisper.cpp `tiny`, `base`, `small` models on CPU, compare elapsed time and memory objectively.
7. Stop/transcribe: editable native draft appears and remains until explicit clearing. Test copy/save and restart; saved drafts are created only on explicit user action.
8. Record/transcribe/send: only an explicit button triggers send attempt. Confirm chat composer is unique; if no send button, text remains and no click is made. Verify actual receipt separately from DOM click.
9. Test Character.AI page redesign, contenteditable vs textarea, chat navigation, missing internet, CEF renderer crashes and browser-process recovery.
10. Inspect `%TEMP%/CharacterCompanionAudio` and `cc-whisper-*.txt`; verify normal cleanup. Confirm that no audio or chat text is uploaded by the host app.
11. Review binary signatures and Windows SmartScreen behavior on a clean machine. Do not claim or attempt guaranteed suppression of warnings.
12. Only when all tests pass, sign release binaries and publish Windows source ZIP, optional installer, hashes and release notes. Publish Android binaries under separate platform assets after Android device signoff.

The preceding source-only tests do **not** qualify as completion of this list.

### v0.21 targeted checks

- With **nonempty old draft**, dictate and press "Расшифровать и отправить": app must append recognized text and **must not** click Send.
- During local recognition, manually edit the draft: auto-send must be cancelled and edited text retained.
- Double-press Send or Ctrl+Enter while insertion is underway: exactly one bridge operation may run at a time.
- With **empty unchanged draft**, an explicit "Расшифровать и отправить" action may click one clearly labelled site Send; check actual delivery separately.
- Force whisper-cli to hang past timeout: confirm child process termination and temporary transcript cleanup.
- Check that a URL with user-info and a lookalike host cannot be treated as first-party.
- Perform microphone format/samplerate tests on actual Windows hardware, especially 44.1/48-kHz-only devices (not covered by source tests).

### v0.22 CEF-only migration signoff

- On clean Windows x64 without manually installing WebView2, build/publish with .NET 10 and ensure the app launches with all bundled CefSharp/CEF resources. Do not claim it works before compiling and executing.
- Inspect installed CEF component versions/licenses and security patch state, VC++ dependencies, profile isolation, browser subprocess, memory use and GPU behavior.
- Verify request/popup handler callbacks run on CEF threads but WPF prompts run on the application dispatcher.
- Review the documented **missing Chromium sandbox in CefSharp** before shipping.
- Confirm Character.AI authentication and third-party redirects without stealing or transferring OAuth state, cookies or session identifiers.
- Confirm native microphone dictation does not require granting the embedded webpage microphone permissions.
