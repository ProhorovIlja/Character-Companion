# Character Companion Windows v0.4.0 pre-alpha — source only

2026-11-23 · WPF/.NET 10 + bundled CefSharp/CEF 156.0.300, **not WebView2**.

## Changed

- Auth-bearing external browser links no longer pass through intact: Google, Apple, Auth0, callbacks, tokens, signed query keys, nested/percent-encoded OAuth parameters and hash-fragment credentials resolve to `https://character.ai/` after a user approval prompt.
- HTTPS origin validation refuses unsupported schemes, embedded usernames/passwords, alternate ports, overly long addresses, Unicode bidi controls and control characters. Ordinary external HTTPS links are still offered after explicit confirmation.
- A secondary URL check runs immediately before `Process.Start`, including direct UI actions. CEF cookies are not copied, and an external browser session does not automatically log the user back into CEF.
- Added `windows/PolicySmoke` with a .NET 10 console project that links the **production SitePolicy.cs** (24 cases, no CEF/NuGet dependency), plus static regression checks. This project is intended for **manual** compilation and execution.
- Whisper.cpp offline dictation, native WPF draft editor, separate send/copy/save buttons, strict DOM origin checks, and CEF browser deployment unchanged.

## What was tested

- Existing Android Python tests (78/78) and Kotlin/JVM policy smoke passed; Android sources unchanged.
- Windows source, XML and JS/Node tests passed (21/21). Static checks are not .NET compiler tests.
- `dotnet` is absent here; the new **24-case C# PolicySmoke is not executed**. The desktop client has also not been compiled or tested with CEF on Windows.
- No APK/EXE, signed installer, GitHub Release, CI, scheduled tasks or background work was created.

## Critical limitations

- CEF is a heavyweight embedded Chromium and CefSharp does not provide Chromium's sandbox. Update CEF regularly and review the application before running untrusted web content.
- Character.AI may reject embedded OAuth; the system-browser handoff intentionally does not transfer login state to CEF. A verified provider-supported callback is required for a seamless login.
- Full CEF packaging, .NET build, live site composer/send, audio hardware, remote authentication and clean-machine Windows testing remain mandatory before any installer is labeled stable.
