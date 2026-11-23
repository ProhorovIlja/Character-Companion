# Character Companion for Windows — v0.2.0 pre-alpha source (2026-11-23)

## Safety and stability

- Fix transcript auto-send accidentally including earlier draft content. Explicit record-and-send only auto-sends when the draft was empty and unchanged for the entire recognition process; otherwise recognized text is appended and remains editable.
- Serialize WebView2 composer insertion and send attempts to prevent overlapping user-initiated operations.
- Wait briefly for the killed whisper-cli child process to exit after exceptions/timeouts, reducing temporary output-file cleanup races.
- Reject credential-bearing userinfo in first-party HTTPS URI classification.

## Controls and test coverage

- Add Ctrl+Enter to explicitly request sending the current draft and a Clear Draft button.
- Add eight executable JavaScript DOM scenarios exercising the actual ComposerBridge payload in Node.js, plus three structural Python regressions.
- Android source unchanged; previous Pixel 8a / LineageOS 24 / Android 17 policy diagnostics remain intact.

## Not claimed

- No compiled, signed or device-tested Windows EXE/MSIX, Android APK, or live Character.AI chat/send/OAuth verification.
- Native bridge interactions depend on site markup and never constitute proof of message delivery.
- Audio transcription requires separately acquired trusted whisper-cli.exe and GGML model. App neither installs runtimes nor fetches assets silently.
- No scheduled tasks, background monitoring, GitHub Actions, release or notifications created.

Manual QA: `windows/QA.md`; implementation: `windows/CharacterCompanion.Windows`.
