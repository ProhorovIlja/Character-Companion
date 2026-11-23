# Character Companion v0.33.0-source — 2026-11-23

Source-only security and lifecycle update. Android GeckoView/legacy remains 0.25.0-rc1, unchanged. Windows WPF + bundled CEF / offline Whisper becomes 0.9.0-prealpha, **without WebView2**.

## Windows changes

- A WPF window-lifetime cancellation token now cancels active Whisper inference at application close. The model runner checks cancellation before process creation, and the existing process-tree termination / temporary file cleanup remains.
- Shutdown prevents future automatic composer submissions and suppresses late WPF UI updates. An already-dispatched CEF JS action cannot be unclicked; integration QA must verify exact behavior.
- External browser offers from CEF non-user-initiated popups and top-level redirects are suppressed. This reduces unsolicited prompts but may affect provider sign-in flows and needs a real site test.
- Added source-only regressions for closure, subprocess cancellation, event ordering and CEF user-gesture gating.

## Verification and limitations

- Run `python3 -m unittest discover -s windows/tests -p 'test_*.py' -q` and `bash scripts/test-offline.sh` plus Node checks.
- No Android SDK, .NET SDK, Gradle or Windows runtime available here, so **no APK, EXE or installer compiled, signed or device-tested**. CEF's lack of Chromium sandbox and real Character.AI OAuth remain release blockers.
- Project sources are available in GitHub and source archives. No background jobs, GitHub Actions, scheduling or automation were created.
