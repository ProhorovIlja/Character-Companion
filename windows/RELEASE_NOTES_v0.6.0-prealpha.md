# Windows v0.6.0 pre-alpha — conversation-bound dictation

**Source only, not an executable.** Still uses .NET 10 WPF, bundled CefSharp/CEF and local whisper.cpp; WebView2 is not required.

- Dictation auto-send records the active Character.AI page **at microphone start**, not only when recognition ends. Changing the page invalidates auto-send; the transcript remains editable.
- The CEF JavaScript DOM bridge independently requires the exact page URL. This also blocks wrong-chat insertion when CEF navigates after the WPF-side check.
- Editing and undoing a draft during dictation still blocks auto-send via a per-edit revision counter. An already nonempty draft always blocks auto-send.
- Cancel Recording no longer waits for needless PCM resampling; it stops and discards the native recording. Capture format attempts use separate completion sources.
- Verified with 11 Node DOM scenarios, 2 CEF JavaScript-envelope scenarios and the offline Windows Python suite. **No .NET/WPF compilation or real microphone/site test** was possible here.

Manual QA: open chat A, start recording, navigate to chat B, then use Transcribe & Send; verify **no click**. Repeat with A→B→A, reload, edit/undo, and no page loaded at recording start. With unchanged A and empty draft, a user-requested auto-send may only click if the composer/send button are unique; confirm actual delivery on-site.
