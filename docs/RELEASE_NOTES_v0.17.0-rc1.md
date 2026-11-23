# Character Companion v0.17.0-rc1 — source preview (2026-11-23)

**Platform:** Android only. **Release status:** source preview, not a signed APK or completed GitHub Release. No build/device sign-off yet.

## Added

- New **Device and ROM compatibility** screen in the native offline hub and Settings, designed for Pixel 8a (`akita`), LineageOS 24.0 / Android 17, and other Android devices.
- A local, opt-in report of device class, Android API/release, reported ABIs, current refresh rate, actual kernel page size, installed WebView provider/version and visibility of a GMS-compatible package.
- Explicitly distinguishes a **GMS-compatible package name** (also used by microG) from verified Google login. Does not assume LineageOS or that a browser works merely because it is installed.
- Diagnostic fields sanitized to prevent newline/URL/control-character injection. Only the user can copy them; no tracking or automatic telemetry.
- Narrow Android package visibility declaration for `com.google.android.gms` — no QUERY_ALL_PACKAGES, accounts or new runtime permissions.
- Expanded **manual ADB preflight** reporting `akita`, ARM64 and GMS-compatible package visibility without serial numbers or ROM version strings.
- Verified Pixel 8a / LineageOS 24.0 technical dossier, exact repositories and a device acceptance checklist. Corrected common tree reference to `zuma`.
- Regression tests for Pixel 8a detection, sanitization, Android 17 API, arm64, GMS uncertainty, refresh-rate bounds and 4/16-KiB kernel page sizes.

## Unchanged constraints

- Current Gradle setup stays at compile/target API 36 until Android SDK/Gradle are available for an **actual** build. Android 17 support is not yet device-certified.
- WebView's session is separate from Chrome/Custom Tabs; sign-in redirect callback and Character.AI calls remain unverified.
- Proprietary Character.AI resources and APK are not included. No Rust/C++ modules were added speculatively.
- No background tasks, CI, watchers, auto-updates, schedule hooks or forced Play Protect changes.

## Validation and release gate

Python structural tests and pure Kotlin/JVM policy checks are run manually; APK build, on-device behavior, 16-KiB native-library checks and Play Protect remain untested. `scripts/prepare-release.sh` refuses to package a release without a separately signed APK and device signoff.

See `docs/PIXEL8A_LINEAGEOS_24.md`, `docs/ANDROID_17.md` and `docs/QA.md`.
