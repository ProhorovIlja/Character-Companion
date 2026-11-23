# Windows Character Companion 0.10.0 pre-alpha — source only

- Compare **all** nested OAuth keys against the same case-insensitive sensitive-key set used for top-level query checks, not a partial hardcoded regex. Deny forwarding encoded `redirect_uri`, `callback`, `code_verifier`, `return_url` and `authorization` from CEF to an unrelated system-browser session.
- Extend the production-source-linked, manually invoked C# PolicySmoke with six cases, including harmless nested `statefulness` (must remain allowed).
- No WebView2 dependency: native WPF/.NET 10 + CefSharp/CEF, NAudio and local whisper.cpp. No .NET compilation, real website login or speech hardware verification was possible here.
- CEF's missing Chromium sandbox remains a release security concern. No executable published.
