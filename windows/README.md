## Mandy AI native Windows preview (25 November 2026)

`windows/Mandy.NativePreview` is a separate, native WPF desktop app. A self-contained
Windows x64 folder was compiled and launched on Windows with .NET 10. The app saved
text exchanges in its local SQLite history, including replies from a local
Qwen3-0.6B-Q8_0 model served by llama.cpp. This starter model is small and its
answers are not representative of the intended full assistant quality.

To build the app on Windows, install .NET 10 SDK and run
`python scripts/build_mandy_windows.py --build --output <new-empty-output-path>`
from the repository root. The script runs the inference, history, memory and
attachment smoke projects before publishing. The model is not downloaded by the
build. To use the optional bundled starter model, place the Windows x64 CPU
llama.cpp `llama-server.exe` and its release DLLs, license files, and
`Qwen3-0.6B-Q8_0.gguf` in a `LocalModel` directory beside
`Mandy.NativePreview.exe`. Keep the whole publish folder together. When no server
key is entered, the first Send starts this model on a random loopback port and
the app stops it when the window closes. A manually configured trusted local
server remains supported. The bundled model is CPU-only; GPU acceleration,
dictation and live calls need separate setup or further validation.

The older CharacterCompanion.Windows CEF prototype documented below is a
different application and its source-only status does not describe this preview.

### v0.11.0 source-only CEF self-contained process host

An explicit WPF `Program.Main` handles CefSharp renderer/GPU/utility subprocesses with `SelfHost.Main(args)` before launching the main window. This is required by the manual `--self-contained true` folder publish; the existing `windows/build-windows.ps1` checks BrowserSubprocess.Core, Chromium data files, libcef and locales. Launch the **published CharacterCompanion.Windows.exe**, not `dotnet.exe CharacterCompanion.Windows.dll`. The code has not been compiled or runtime-tested on Windows. See [release notes](RELEASE_NOTES_v0.11.0-prealpha.md).

### v0.9.0 source-only shutdown and external-popup security

Closing Windows now cancels active whisper.cpp processing and prevents later automatic-send dispatch. Non-user-initiated third-party CEF popup/navigation attempts no longer create an external-browser dialog. Windows authentication and process cancellation remain unverified on-device; [release notes](RELEASE_NOTES_v0.9.0-prealpha.md) and [QA](QA.md) describe required manual checks.

**Windows 0.8.0 source:** `whisper-cli.exe` stdout/stderr are drained with constant memory. Added a foreground-only manual Windows PowerShell build/publish gate (`.\windows\build-windows.ps1` from repository root) that first compiles/runs linked-production .NET PolicySmoke, then publishes a full CEF/WPF portable folder. **The helper is untested in PowerShell; no EXE or signed installer exists.** [Release notes](RELEASE_NOTES_v0.8.0-prealpha.md).

**Windows 0.7.0 source:** ComposerBridge now rechecks exact page and DOM attachment after synchronous website focus/input callbacks and immediately before Send. NAudio 3 recorder now uses `WaveIn` rather than obsolete `WaveInEvent`. [Release notes](RELEASE_NOTES_v0.7.0-prealpha.md). **Not built/tested on Windows.**

**v0.6.0 source:** Auto-send is tied to the first-party conversation page observed when the microphone starts. Page navigation, draft editing (including undo) or a changed DOM page cancels auto-send; recognized text stays editable. The `ComposerBridge` verifies the exact URL inside its JavaScript task. Cancel Recording skips resampling and transcription. [Detailed release notes](RELEASE_NOTES_v0.6.0-prealpha.md). **No compiled Windows executable yet.**

# Character Companion · Windows 0.5.0 pre-alpha — bundled CEF, NOT WebView2

**v0.5.0 source:** NAudio capture now tries PCM16 48/44.1/16-kHz mono and 48/44.1-kHz stereo formats, then a bounded streaming resampler produces 16-kHz mono WAV for offline whisper.cpp. This code has not been run on Windows or compiled in this environment. [Speech change notes](RELEASE_NOTES_v0.5.0-prealpha.md).

**v0.4.0 source:** hardened handoff to the system browser. Native `SitePolicy` now validates HTTPS origin, rejects userinfo and alternate ports, and redirects identity links, callback paths, encoded OAuth keys, nested redirect credentials, and hash-token fragments to a fixed Character.AI homepage only after user confirmation. Credentials or cookies are never transferred between CEF and the system browser.

**Manual unit compilation without WPF/CEF:** `dotnet run --project windows/PolicySmoke/CharacterCompanion.PolicySmoke.csproj` from the repository root. It compiles the **actual production `SitePolicy.cs` and `Speech/Pcm16Resampler.cs`** with .NET 10, checks 24 URL cases and 5 PCM resampling cases, without additional NuGet packages or a browser. It has NOT been run in this environment because the .NET SDK is absent. [See test README](PolicySmoke/README.md).

Source-only WPF/.NET 10 desktop prototype. **The former WebView2 dependency has been removed.** The web renderer is app-distributed `CefSharp.Wpf.NETCore` 156.0.300 (Chromium Embedded Framework), not Edge WebView2. No standalone Edge/WebView2 Runtime installer is required. This does **not** make Character.AI's website fully native or remove the cost of bundling a Chromium engine.

## Features preserved

- A first-party `https://character.ai/` browsing surface in a native WPF window and a private, dedicated CEF profile at `%LOCALAPPDATA%\CharacterCompanion\CefProfile`.
- Top-level URL restrictions, popup blocking or user-confirmed external HTTPS browser handoff, conservative toggleable tracker-domain filter.
- A user-triggered NAudio microphone recording with multiple PCM16 capture-format fallbacks (max three minutes), local `whisper-cli.exe` with selected multilingual GGML model, editable draft and explicit copy/save/clear/insert/send operations. No automatic background listening and no cloud speech requests.
- **Record → transcribe → attempt send** only when explicitly requested and no earlier/changed draft exists. Ctrl+Enter sends only when explicitly pressed. The app does not claim message receipt when it merely clicked a web button.
- The CEF bridge uses the exact checked-in `ComposerBridge` script, a JSON-encoded transcript, original-page origin check and a unique composer/send control requirement.

## Requirements & manual Windows x64 build

- Windows 10/11 x64 and official **.NET 10 SDK**. Source selects x64 because supported CEF binaries need a matching runtime architecture.
- NuGet package restore for `CefSharp.Wpf.NETCore` **156.0.300** and `NAudio` **3.3.0**. CEF native runtime binaries must be included in output; do **not** publish a single-file build that drops them.
- Trusted `whisper.cpp` Windows `whisper-cli.exe` and a separate multilingual GGML `.bin` model selected manually. They are not bundled or downloaded in the background.

```powershell
cd windows/CharacterCompanion.Windows
dotnet restore
dotnet build -c Release -r win-x64
dotnet run -c Release -r win-x64
```

To create an **unsigned, unverified** portable source build, after testing prerequisites:

```powershell
dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=false -o ..\publish\win-x64
```

Keep the **entire** publish folder: `libcef.dll`, resources/locales, CefSharp assemblies, browser subprocess and supporting native DLLs are required. Windows Visual C++ runtime requirements must be tested on a clean machine. The executable has **not** been built, signed or tested here.

## Privacy and security limitations

- CefSharp documentation states that it **does not implement Chromium's sandbox**. Do not infer security superiority relative to WebView2. Patch CEF frequently and conduct a dedicated review before release.
- Google OAuth can reject embedded browsers. An explicitly requested system-browser handoff starts at the fixed Character.AI homepage for identity flows. Separate browser cookies cannot automatically authenticate this CEF profile; no silent session transfer, token harvesting or spoofing is implemented.
- The desktop app is an unofficial client; no public Character.AI API is assumed. Full native parity for calls/media/login has not been demonstrated.
- Tracker rules may break legitimate pages; filtering is toggleable and narrow. It is not a bypass for subscription requirements, and the app does not access account credentials.
- WAV and transcript temporary files are removed on normal completion; abrupt process crashes can leave leftovers that users may delete manually.

## Testing status

```bash
python -m unittest discover -s windows/tests -p 'test_*.py'
node windows/tests/bridge_runtime.js
node windows/tests/test_cef_script_runtime.js
bash scripts/test-offline.sh
```

These scripts test source invariants and JavaScript payloads using a synthetic DOM. They **do not** compile WPF/C#, execute Chromium on Windows or prove login/message delivery. See [windows/QA.md](QA.md) for manual Windows acceptance checks and [renderer decision](../docs/RENDERER_DECISION_2026-11-23.md) for Windows CEF vs Android GeckoView tradeoffs.
