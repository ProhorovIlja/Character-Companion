# Character Companion v0.10.0-rc1 — source candidate (NOT an APK release)

Status at preparation time: **No APK has been produced** in the current environment. No installer or GitHub Release should be advertised as ready. Android SDK/Gradle/ADB and on-device sign-in tests are unavailable.

## Changes since v0.9.0

- Added bounded renderer recovery: at most three automatic WebView renderer restarts within a two-minute rolling window. The app shows a retry screen instead of looping indefinitely on repeated renderer crashes.
- Added a manual Storage Access Framework export of user-imported domain block/allow lists as normalized TXT. The export is explicitly initiated by the user and is performed off the UI thread. Chats, tokens and cookies are not exported.
- External-domain downloads are now opened through the browser rather than delegated to Android DownloadManager. Only clean HTTPS links on Character.AI-owned domains are eligible for the system downloader. Signed/query-bearing URLs remain browser-only.
- Pending camera/microphone permissions are denied when the Activity stops (not during Android permission dialogs). Permission requests received after destruction are denied.
- Extended offline unit tests, manifest/locale checks and manual release safeguards.

## Known limitations

- An embedded WebView cannot assume Google OAuth works. Browser-backed Custom Tabs have a separate browser session and do not receive WebView adblock rules.
- User-imported filters cover only supported domain entries, not full EasyList cosmetic or URL-path rules, first-party advertising or all WebSocket traffic.
- Website layout, messaging and calls have **not** passed real-device tests.
- **No signed release APK exists or has been verified in this iteration.**

## Release gate

A production GitHub Release must include a signed, verified APK, two Git source archives (ZIP and tar.gz), SHA256SUMS.txt, release notes, and the commit ID. Verify Android 10/13/15/16 installations, OAuth/browser mode, chats, file picker, downloads, voice permissions, filter compatibility, performance and accessibility before publishing. No automatic publishing or CI/schedules are permitted.
