# Character Companion · Windows prototype (v0.20.0-source)

Native Windows 10/11 WPF client with WebView2, privacy-focused request filtering, and **offline voice dictation**. This is an **uncompiled source prototype**, not a tested executable.

## Scope

- Loads the first-party `https://character.ai/` website in WebView2 with a separate on-disk browser profile.
- Conservative user-toggleable tracker-domain filter enabled by default (not full EasyList; no subscription bypass).
- System-browser handoff for other HTTPS sites and OAuth; **external cookies do not return into WebView2**.
- Records mic via NAudio at 16-kHz mono PCM, at most three minutes; runs a user-provided `whisper-cli.exe` and GGML `.bin` model locally. No cloud transcription service or background capture.
- Whisper output appears in editable native draft. Buttons: insert without sending; click verified site-send control; record→transcribe→attempt send; copy; save TXT.
- Site injection restricted to `*.character.ai`, only one visible composer, and *only* one accessible send control near it. If either is not found, leave the editable draft and require manual paste/send. Clicking send is **not proof of server receipt**.
- WAV and temporary transcript are removed after use. The app does not export cookies or account data; it saves only chosen binary/model paths, language, and filter preference.

## Requirements and manual build

Install official Microsoft .NET 10 SDK, WebView2 Runtime, and a trusted `whisper.cpp` Windows build with `whisper-cli.exe`. Download a multilingual GGML model for Russian/English. Neither executable nor model is bundled.

```powershell
cd windows/CharacterCompanion.Windows
dotnet restore
dotnet build -c Release
dotnet run -c Release
```

For an unpackaged Windows x64 release candidate (after testing on Windows):

```powershell
dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=false -o ..\publish\win-x64
```

The output is **unsigned** unless signed separately by a trusted certificate. Never publish it as tested before on-device verification.

## Known limitations

- No build was performed in the present Linux container; this source has not been compiled with a Windows SDK or executed on Windows hardware.
- Google OAuth in embedded WebViews can be blocked by Google. System browser mode is a **separate** session; a seamless OAuth return requires registration and support from the actual Character.AI service, not cookie scraping.
- Character.AI controls page markup, editor and send controls. DOM insertion can fail after site updates; transcribed text always remains in the native editor.
- The host app has no Character.AI private API, cannot guarantee access to Character Calls, and does not bypass paid features.
- Some microphones may not accept the requested 16-kHz WaveIn format; device selection and resampling are future enhancements.
- The Whisper CLI and models are outside the app and must be supplied separately; do not download unknown executables automatically.
- Transcriptions are deleted on normal completion; an abrupt process crash can leave a temporary WAV in the user's temp folder. Manual deletion is possible.

Primary references: [Microsoft WPF WebView2](https://learn.microsoft.com/en-us/microsoft-edge/webview2/get-started/wpf), [whisper.cpp CLI](https://github.com/ggml-org/whisper.cpp/tree/master/examples/cli), [Google OAuth in embedded browsers](https://developers.google.com/identity/siwg/best-practices).
