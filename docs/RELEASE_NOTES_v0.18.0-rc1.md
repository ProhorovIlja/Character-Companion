# Character Companion v0.18.0-rc1 — Android source candidate

Date: 2026-11-23. **Source candidate; NOT a production APK.**

## Changes

- Pixel 8a / LineageOS manual diagnostics now inspect both `com.google.android.gms` and Morphe's distinct `app.morphe.android.gms`, instead of treating all microG variants as system GMS.
- Show package availability and sanitized version labels, with explicit warnings that neither installed component guarantees Character.AI Google OAuth, voice calls, or Play Integrity.
- Package visibility is limited to those exact two package names in AndroidManifest; no installed-app enumeration, account access or background probe.
- Expanded Kotlin regression assertions and source-level structural tests, including malicious package-version label sanitization.
- Updated the Pixel 8a / LineageOS 24 dossier for Morphe MicroG-RE and MindTheGapps coexistence.

## Status / limitations

- Android 17 runtime remains untested on the physical Pixel 8a. `compileSdk` and `targetSdk` remain 36 pending a validated AGP/SDK migration.
- The source-only tests are not a substitute for assembling the Android APK with Gradle, testing login, and profiling the device.
- The external Google OAuth callback still cannot transfer browser cookies into WebView automatically.
- No proprietary Character.AI code has been copied into the project.
- Do not bypass, disable, or claim to suppress Play Protect prompts.
- No scheduled tasks, workflows or automatic monitors.

## Research

- https://github.com/MorpheApp/MicroG-RE
- https://morpheapp.net/microg
- https://lineageos.github.io/lineage_wiki/gapps/
