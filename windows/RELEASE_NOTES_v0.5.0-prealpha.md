# Windows v0.5.0 pre-alpha — microphone compatibility

This is **source code only**, not a built or signed Windows application. WPF + bundled CefSharp/CEF replaces WebView2; offline whisper.cpp remains responsible for transcription.

Changes:
- Native microphone capture tries 48-kHz or 44.1-kHz PCM16 first, with 16-kHz mono fallback and stereo alternatives. A driver may still reject every format; this is a best-effort fallback, not a universal hardware guarantee.
- Local constant-memory box-weighted normalization produces 16-kHz mono PCM16 WAV for whisper.cpp. The raw capture WAV is removed when conversion finishes or fails. A three-minute recording limit remains.
- `windows/PolicySmoke` now compiles **production** `SitePolicy.cs` and `Speech/Pcm16Resampler.cs` without NAudio/WPF/CEF, and contains deterministic sample assertions. To run manually after installing official .NET 10:
  `dotnet run --project windows/PolicySmoke/CharacterCompanion.PolicySmoke.csproj`
- Manually test USB, integrated and Bluetooth input devices, check Russian and English transcription, cancelling and stop-at-three-minutes, CPU/memory usage, and whether a microphone actually supports the attempted formats.

No .NET SDK, Windows execution or microphone test was available to verify the implementation. Windows packaging still requires shipping all CEF native assets and a separate security review because CefSharp lacks Chromium sandbox.
