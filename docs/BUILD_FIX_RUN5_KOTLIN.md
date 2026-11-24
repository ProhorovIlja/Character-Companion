# Build #5: Kotlin compiler/library mismatch (24 November 2026)

Run: https://github.com/ProhorovIlja/Character-Companion/actions/runs/45356783833

SDK installation, wrapper, tests, dependency resolution and AAR metadata validation succeeded. Kotlin compilation failed: Kotlin 2.2.0 can read metadata up to 2.3.0, but the resolved kotlin-stdlib 2.4.10 contains metadata 2.4.0. The 1,839 compiler error lines include extensive cascading diagnostics. No APK was uploaded. Do not bypass the Kotlin metadata validation or downgrade the resolved library blindly.

## New pinned build stack

- KGP/compiler, Kotlin BOM and stdlib 2.4.21; use AGP's documented buildscript classpath override for built-in Kotlin. Keep `org.jetbrains.kotlin.android` unapplied.
- AGP 9.3.1 and Gradle 9.7.0: JetBrains' highest fully supported AGP and Gradle versions for KGP 2.4.21. AGP 9.5.0 / Gradle 9.9.2 are newer separately but outside that documented matrix.
- Gradle 9.7.0 official wrapper JAR and distribution, both SHA-256 pinned. JDK 21 LTS on the GitHub runner; Java/Kotlin JVM target 17. Android platform 37.0, target SDK 36, build-tools 36.0.0 (AGP documented default).
- GeckoView ARM64 159.0.20261102144711, newest stable version found in the accessible artifact index; Mozilla beta/nightly are not selected. AndroidX Core and Core KTX 1.19.1, WebKit 1.18.0 and Browser 1.10.0.
- Actions checkout v7.0.1, setup-java v6.3.0, setup-android v4.0.4, upload-artifact v7.0.2, checked against GitHub's latest releases. Retain only workflow_dispatch.
- Independent sequential Gecko and legacy builds; each must pass its own debug signature, package/version, manifest/DEX and archive validation. A successful flavor is retained even if the other fails; the workflow still reports failure for incomplete builds. No production signing.
- Qualify the SeekBar progress receiver explicitly; make bookmark options addAll explicit to avoid ambiguous receiver/operators in reported errors.

## References

- https://kotlinlang.org/docs/releases.html
- https://kotlinlang.org/docs/gradle-configure-project.html
- https://developer.android.com/build/releases/agp-9-0-0-release-notes#runtime-dependency-on-kotlin-gradle-plugin
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://gradle.org/release-checksums/
- https://mvnrepository.com/artifact/org.mozilla.geckoview/geckoview-arm64-v8a
- https://developer.android.com/jetpack/androidx/releases/core

Only local source and mocked tool tests are possible without a real SDK/Gradle/network. The next user-started workflow must confirm compilation. A resulting debug APK remains untested on the Pixel 8a. No new automated triggers or schedules.
