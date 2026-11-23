# Character Companion v0.25.0-source — Gecko native controls and manual recovery

Date: 2026-11-23. **Source candidate only. No compiled, signed or device-tested APK/EXE.**

## Android GeckoView improvements (Android version 0.22.0-rc1)

- Native Gecko browser toolbar now includes an explicit save-current-link action, searchable local bookmark library, opening saved pages and confirmation before removing bookmarks. The existing `SettingsStore` and `NativeLibrarySearch` are reused; the website's DOM, cookies and chats are not scraped for this feature.
- Native Gecko-specific settings dialog now exposes only controls implemented by Gecko: tracking protection, microphone/camera permission prompts, screenshot/screen-recording protection, sanitized last-page restore and compact toolbar. Edits persist using the existing local preferences. Turning off last-page restore removes the stored URL. The fallback/legacy tools remain reachable through settings.
- Added explicit, user-initiated content-process crash recovery for Gecko `onCrash` and `onKill`; the user is warned that unsent form state may be lost. Automatic retries, timers, recurring monitoring and background tasks are not introduced. Manual restart attempts are rate-limited to two in 120 seconds with the existing monotonic recovery budget. Outstanding site permissions, file prompt and download operations are invalidated on renderer failure. Recovery reloads only a sanitized, trusted URL.
- Retain Gecko Activity on orientation/screen-size changes to reduce involuntary loss of the live browser session. Android can still restart the process for other reasons; no guarantee of preserving unsent text after real process death.
- Android permission return now verifies the activity is still in the foreground, the user still allows media and the active origin remains trusted before granting a pending request.
- New standalone regression tests check the native bookmark/settings actions, crash recovery gate, manifest and localized strings. Existing Gecko file transfer protections and Windows CEF/Whisper prototype are unchanged.

## Scope and limitations

- New controls are in the **gecko ARM64 flavor only**. The `legacy` variant remains available for feature parity checks. The Gecko app uses Mozilla's embedded engine, not Android System WebView, but the repository still contains a legacy Android WebView implementation as a fallback. The Windows native WPF/CEF client is unchanged and does not require WebView2 Runtime.
- Gecko custom blocking/imported domain lists, offline settings backup, equivalent Google OAuth handoff, fullscreen media and some web dialog types are still outstanding. Some Character.AI site functions may not work in an embedded browser.
- Real session recovery, Chromium/Gecko native library loading, microphone/camera functionality, Google sign-in, upload/download, 120 Hz and power use all require real Android 17 / Pixel 8a and Windows acceptance tests. Offline tests do not confirm an Android or Windows build.
- Android `compileSdk` and `targetSdk` are still 36; Android 17 is the target environment, not an independently certified compatibility claim.
- XAPK static research for Character.AI v1.17.4 is in `docs/OFFICIAL_APK_AUDIT_1.17.4.md`; none of its closed-source code is copied. No nonpublic Character.AI API or OAuth interception is implemented.
- No CI, scheduled checks, automations, background monitoring or persistent tasks.

## Build and release

Use the GitHub checkout containing `gradle-wrapper.jar`, Android SDK and compatible Gradle/AGP. Run `bash scripts/test-offline.sh` and the **manual** flavor build `bash scripts/build-apk.sh gecko`, then conduct real device acceptance tests. No APK/EXE or GitHub Release is generated just by creating this source candidate. A signed release requires an explicit device QA signoff and verified package signatures. Play Protect warnings cannot be suppressed or guaranteed absent.

References:
- GeckoView ContentDelegate (crash and kill callbacks): https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSession.ContentDelegate.html
- GeckoView runtime/session lifecycle: https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoSession.html
