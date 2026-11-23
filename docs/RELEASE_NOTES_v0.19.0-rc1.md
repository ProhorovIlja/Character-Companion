# Character Companion Android v0.19.0-rc1 — source candidate

Date: 2026-11-23. **Not a signed APK or stable release.**

## Changes

- Read-only diagnostics now recognize Morphe MicroG-RE's current `app.revanced.android.gms` package (upstream `basePackageName=app.revanced` on 2026-08-13), the older `app.morphe.android.gms` package, and the independent system GMS package `com.google.android.gms`. Versions are sanitized; packages are not conflated, and no Google account or chat data is accessed.
- Manual ADB preflight checks these same exact package names; no installed-app scan or automatic process.
- Release packaging no longer contains stale hard-coded v0.17 metadata: it validates APK against the current `app/build.gradle.kts` versionCode/versionName and refuses debug/unsigned/no-device-signoff input.
- Extended Kotlin/JVM policy regression tests and 52 Python source/XAPK fixture checks. Builds remain Android-only.

## Compatibility and known limitations

- Pixel 8a `akita` / LineageOS 24 (Android 17, API 37): **runtime code designed for these APIs but not tested on the physical device**. The existing project still uses `compileSdk=36`, `targetSdk=36`, Android Gradle plugin 8.13, and Gradle 8.13. Updating to SDK 37 requires an independently verified AGP/Kotlin migration, not a version-number-only bump.
- Official Character.AI XAPK is proprietary. Previous static audit remains documented in `docs/OFFICIAL_APK_AUDIT_1.17.4.md`; native server API access, proprietary resources, Google OAuth callbacks, voice calls and login state remain unverified.
- A Custom Tab can use the external browser's logged-in state but it cannot silently transfer cookies into WebView. No bypass of Character.AI paid features, Google Play Protect, or identity services is included.
- No APK build was possible in this environment without Android SDK and Gradle; a GitHub Release requires a signed APK and completed on-device signoff.
- No CI, scheduled tasks, background monitoring, new permissions or telemetry were added.

## Primary sources

- https://github.com/MorpheApp/MicroG-RE/commit/29102769010c48e4027a4159760dbf1396285a57
- https://raw.githubusercontent.com/MorpheApp/MicroG-RE/main/build.gradle
- https://lineageos.github.io/lineage_wiki/gapps/
- https://developer.android.com/build/releases/agp-9-4-0-release-notes
