# Character Companion v0.26.0-source — safer Gecko external-browser handoffs

Date: 2026-11-23. **Source candidate only; no Android APK or Windows EXE has been built, signed or device-tested.**

## Android GeckoView (app 0.23.0-rc1, versionCode 23)

- Added pure Kotlin `ExternalBrowserPolicy`. An explicit request to open a website in the system browser can no longer transfer a Gecko-session Google/Apple/Auth0 sign-in URL, temporary OAuth `code`/`state`/access token, sensitive callback route or signed download URL. Instead the user sees a clear confirmation and can open the fixed Character.AI home page in their system browser. Non-HTTPS and malformed URLs remain blocked.
- Handles percent-encoded query keys, multiply-encoded sensitive paths, hash-fragment credentials and nested redirect parameters conservatively. Simple ordinary HTTPS links still open only after an explicit user tap.
- Fixed the incoming Android Share intent path so that it does not attempt to navigate a crashed Gecko session before manual recovery.
- Added RU/EN strings explaining why login state cannot be moved between embedded and external browser sessions.
- Android `gecko` and `legacy` Gradle flavors are retained; Gecko still uses the embedded ARM64 engine without Android System WebView as its active browser. All previous Gecko file transfers, bookmarks, native privacy settings and manual crash recovery remain present.

## Verification

- **78/78** Android Python regression tests pass. Existing tests were updated for the new app version and Kotlin source list. The new suite tests UI integration, manifest/translation structure, and preservation of the navigation safety gate.
- Standalone **Kotlin/JVM PolicySmoke PASS**, including executable positive/negative tests for auth origins, plain external links, encoded OAuth parameters, callback routes, URL fragments, untrusted schemes and Unicode bidi controls.
- **18/18** Windows Python/Node tests pass; Windows WPF/.NET 10 + bundled CEF and offline Whisper source **is unchanged**.
- Manual Bash syntax checks and archive checksum/integrity checks pass. None of these constitute Android or Windows compilation.

## Unresolved release gates

- No Android SDK/Gradle distribution, Windows/.NET SDK, ADB-connected Pixel 8a or real Character.AI login session was available. Thus there is no signed APK, EXE, device performance benchmark, Google OAuth callback support or stable GitHub Release.
- **Opening Character.AI in the system browser cannot transfer cookies into GeckoView.** It is not a real sign-in callback implementation; a supported OAuth integration is required for that.
- GeckoView still lacks parity for imported custom domain blocking, certain media/fullscreen workflows and potentially cross-origin CDN downloads. Windows CefSharp lacks Chromium's sandbox; security and deployment validation remain mandatory.
- Android 17 / API 37 on Pixel 8a (`akita`, LineageOS 24, MindTheGapps/Morphe microG) remains the acceptance-test target; `compileSdk`/`targetSdk` are 36. No code can guarantee Google Play Protect will display no warning.
- Character.AI's closed XAPK code, account credentials, private endpoints and authentication secrets were not copied or used.

## Build / workflow

Use the full GitHub checkout including the original official Gradle Wrapper JAR, a separately provisioned Android SDK, and `bash scripts/build-apk.sh gecko` or `bash scripts/build-apk.sh legacy` manually. All automated CI, scheduled work, monitoring, notification and background task creation remain prohibited by the user's explicit instruction. No installer or release asset is created from this source archive alone.
