# Windows v0.9.0 pre-alpha — lifetime cancellation and popup restraint

- Closing the WPF window now cancels a running local whisper.cpp inference through the existing cancellation token. Cancellation should kill the spawned `whisper-cli.exe` process tree and clean its temporary transcript. No auto-send is initiated after shutdown.
- A token check occurs before `process.Start()` so a window already closed before model launch cannot spawn `whisper-cli.exe`.
- CEF popup attempts and top-level third-party navigation only propose an external browser when `userGesture` is true. Non-user-initiated attempts are silently blocked; the normal explicit external-browser button remains usable.
- Added `windows/tests/test_shutdown_and_popup_source.py` to guard this wiring. Existing Python and JS tests remain.

**Not a Windows build.** These checks do not exercise NAudio drivers, CEF callbacks, shutdown races, real OAuth or native process termination. Run the manual .NET PolicySmoke and Windows integration plan before distributing an installer. The CEF browser has no Chromium sandbox; do not claim production security.

**Note:** a direct click that launches an asynchronous OAuth redirect may not retain CEF `userGesture` across redirects; the real site login flow must be tested. Sessions cannot be transferred between CEF and system browser.

No scheduled tasks, CI, watchers or background services were created.
