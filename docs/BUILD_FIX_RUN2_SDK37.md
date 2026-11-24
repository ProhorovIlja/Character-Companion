# Manual Android build #2: AAR metadata / SDK 37 fix (2026-11-24)

Run: https://github.com/ProhorovIlja/Character-Companion/actions/runs/45350814303

The Python tests passed, then `:app:checkGeckoDebugAarMetadata` failed (5 issues): GeckoView 156 requires `compileSdk >= 37`; transitive AndroidX Core/Core-KTX 1.19 also requires `compileSdk >= 37` and AGP >= 9.1. The old AGP 8.13.0 + SDK 36 configuration is incompatible with these dependencies. This is not an Android SDK download failure.

Toolchain updated based on official Android/Gradle compatibility documents:

- Android Gradle Plugin **9.1.1**, compatible with Android API 37.0 (https://developer.android.com/build/releases/agp-9-1-0-release-notes).
- Gradle distribution **9.3.1**, pinned `gradle-9.3.1-bin.zip` SHA-256 `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06` from https://gradle.org/release-checksums/. The *unchanged*, separately checksum-verified **8.13 Gradle Wrapper bootstrap JAR** can fetch the new Gradle distribution. This is intentional; it is not falsely labeled a Gradle 9.3.1 JAR.
- Kotlin uses **AGP 9 built-in Kotlin** instead of `org.jetbrains.kotlin.android`; deprecated `kotlinOptions` block removed per https://developer.android.com/build/migrate-to-built-in-kotlin. Java bytecode remains 17.
- `compileSdk = 37`; `targetSdk = 36` intentionally preserved until physical Android 17 testing.
- The same explicitly user-authorized **manual-only** `workflow_dispatch` GitHub Actions workflow now installs SDK platform 37. No automatic triggers added. Build-tools 36.0.0 remain compatible with AGP 9.1.1.
- Both Linux/Windows doctor and manual builders plus their offline tests updated for this toolchain; the expected build tasks remain `:app:assembleGeckoDebug` and `:app:assembleLegacyDebug`.

**Verification limitations:** The first two GitHub Actions attempts were failures; this repair has not been compiled using real Android tooling in the current sandbox. A *new manual invocation from `main`* is required; do not re-run a failed old commit. Only a passing cloud compile plus signature/package checks will create actual APK artifacts.
