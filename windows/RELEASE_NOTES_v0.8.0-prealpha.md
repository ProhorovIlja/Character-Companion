# Windows v0.8.0 pre-alpha — source only

- Fix unbounded memory retention while consuming whisper-cli.exe stdout/stderr. Fixed-size buffers stream and discard CLI chatter while the separate text-output file provides the actual transcript.
- Add the explicit `windows/build-windows.ps1` Windows x64 publisher. It requires .NET 10, runs source-linked PolicySmoke, builds the unpackaged CEF/WPF folder without trimming or single-file mode, and checks EXE, libcef.dll and locale resources. It does not sign or publish.
- Source-linked .NET PolicySmoke now includes the real WhisperCliTranscriber as an additional compilation gate and validates missing executables fail before process launch.
- Runtime integration with Character.AI, Microsoft Windows, CEF and physical microphones is **not verified**. No EXE has been built.
