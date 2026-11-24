# Android 0.38.0-rc1 — filter lifecycle and capability clarity

- Login compatibility is limited to visible embedded renderers. Backgrounding the app, opening the native workspace, or destroying an Activity clears the temporary manual exception. On return the actual visible auth route is reevaluated; the manual override is never restored automatically.
- Login routes must begin with a recognized auth path, optionally locale-prefixed. Manual route binding uses a SHA-256 fingerprint of the host and raw path, not a 32-bit hash or raw query. WebView ignores stale renderer callbacks; an old process-wide service-worker filter cannot retain a destroyed Activity's manual bypass. Renderer crashes clear temporary exceptions.
- Gecko uses its native tracking protection, not the WebView imported host rules. UI explains this distinction and browser-owned session limitations. No cosmetic filter, OAuth bypass, private API, or automatic updates introduced.
- Filter block counters and resets use the same lock, avoiding mismatched totals after a concurrent reset.

Validation: local Python structural/fixture tests and pure Kotlin/JVM policy tests. Actual Android build, behavior on Pixel 8a / LineageOS, sign-in and page visibility transitions remain to be tested. Test fresh start, login modal/manual toggle, Home/Back, activity background and return, native workspace, renderer crash/recovery, stale redirects, concurrent clear, Gecko and WebView. The previous signed debug artifacts are not this release. Existing workflow remains manual only.
