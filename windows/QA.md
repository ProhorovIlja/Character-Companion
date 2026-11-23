### v0.9.0 manual shutdown and external navigation checks

On real Windows, during a long Whisper inference close the window: check Task Manager for absence of the orphaned `whisper-cli.exe`, absence of unintended message insertion/send, and cleanup of the temporary audio/text files. Repeat by closing immediately after pressing Transcribe & Send and after recognition finishes but before CEF dispatch. Verify normal user-clicked external links prompt for confirmation, while automatic popups/redirects do not. Test sign-in because OAuth redirects may not retain a CEF user gesture. Source tests cannot certify these scenarios.

### v0.8.0 — manual build and CLI pipe-drain verification

From the full checkout on a Windows 10/11 x64 desktop with official .NET 10 SDK and network/package cache ready:

```powershell
.\windows\build-windows.ps1 -Configuration Release
```

The build must **first run actual source-linked C# PolicySmoke**, compile the production Whisper subprocess runner, and then publish untrimmed CefSharp native assets. It must reject missing SDKs, failed PolicySmoke, missing `CharacterCompanion.Windows.exe`, `libcef.dll` and `locales`. Validate the reported SHA-256. This is an unsigned, unverified portable folder, not a release installer.

On a real Windows test machine, run a trusted local `whisper-cli.exe` against a legitimate model and confirm multi-minute stdout/stderr streams do not grow the client process memory unboundedly; cancel and timeout must terminate the child and remove temporary text files. No synthetic test substitutes for this. No startup services or scheduled tasks.

### v0.4.0 manual security test gate

Before trying a Windows installer, manually run `dotnet run --project windows/PolicySmoke/CharacterCompanion.PolicySmoke.csproj` with .NET 10 installed; require `PASS 24 external-handoff policy cases`. On Windows test browser handoffs with ordinary HTTPS links, Google/Apple/Auth0 sign-in, nested `%2563ode` keys, `#access_token`, encoded `/auth/callback` paths, and invalid schemes. Confirm that the browser never receives a source CEF OAuth callback or cookies. No real browser/API acceptance has been performed in this source-only environment.

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

## v0.5.0 speech capture acceptance

On Windows, test a 48-kHz default input, a 44.1-kHz input and a device that lacks native 16-kHz capture. Stop and cancel recordings, confirm no truncated WAV or stale native-capture temp file, and check generated 16-kHz mono RIFF metadata before using whisper.cpp. Repeat with a stereo-only capture driver if available. Run `dotnet run --project windows/PolicySmoke/CharacterCompanion.PolicySmoke.csproj` to execute the source-linked URL and PCM regression checks. Document perceived speech quality and latency; none of these real-device checks were performed in the source-only environment.


### v0.6.0 conversation-bound dictation (manual Windows signoff)

1. With Character.AI conversation **A** open, record, then navigate to **B** and select Transcribe & Send. The transcript must remain in the native draft, with **no send attempt in B**.
2. Repeat with A→B→A. The navigation revision must still cancel the original automatic send.
3. Repeat while recording and again while Whisper is transcribing. Also try a page reload and a tab/hash navigation; the JS bridge must reject any different current URL.
4. Edit the draft and undo it before recognition ends. The edit revision must cancel auto-send even when the text is identical to its previous snapshot.
5. Start recording before any first-party page is ready. Recognition may still work; automatic send is suppressed.
6. From a stable first-party chat with an initially empty, unmodified draft, a user-initiated Transcribe & Send may attempt **one** clearly labeled send button. Verify actual receipt on the real service separately.
7. Cancel a 1–3-minute recording: it must not transcribe or convert to a second WAV, and temporary files should be removed. Test microphone format fallback and driver failure.

These are real Windows integration tests pending compilation and hardware access; Node synthetic DOM checks alone are not a signoff.
