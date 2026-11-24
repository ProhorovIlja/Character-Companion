# GitHub manual Android build #4 — Maven Central HTTP 429

2026-11-24. The user-initiated run https://github.com/ProhorovIlja/Character-Companion/actions/runs/45354942394 compiled neither flavor. Android SDK 37.0 setup, Java 17, pinned Gradle wrapper verification and all Python regression tests succeeded. The `Build GeckoView and WebView debug APKs` step failed during root project buildscript classpath dependency resolution, **before** application source compilation.

The real runner received HTTP **429 Too Many Requests** from `https://repo.maven.apache.org/maven2/` while resolving dependencies including `org.apache.httpcomponents:httpmime:4.5.6`, `commons-io:2.16.1` and `org.ow2.asm:asm:9.9`. Gradle reported 47 other failures of the same kind. No APKs/artifacts were created. Do not mislabel this as a Kotlin compile error or missing Android SDK.

## Narrow fix

`settings.gradle.kts` now prefers Google's public Maven Central mirror (`https://maven-central.storage-download.googleapis.com/maven2/`) **before** `mavenCentral()` both in `pluginManagement.repositories` and `dependencyResolutionManagement.repositories`. The Google Android Maven repository remains first for Android/AGP artifacts. Maven Central remains a fallback and Mozilla's GeckoView repository remains enabled. No dependency versions, signing keys, credentials, app source, workflow triggers or device requirements are modified.

Why **both** scopes: buildscript/plugin classpath artifacts were precisely what failed. Fixing application dependencies alone cannot address those downloads. The mirror is documented by Google Cloud and recommended by JetBrains for Maven Central 429 failures:

- https://docs.cloud.google.com/docs/buildpacks/java#hosted_maven_central_mirror
- https://support.jetbrains.com/hc/en-us/articles/40043409905170-Builds-fail-with-429-Too-Many-Requests-when-resolving-dependencies-via-Maven-Central
- https://docs.gradle.org/current/userguide/declaring_repositories_basics.html

Three targeted Python tests guard ordering, retained repositories, and the user-approved manual-only event. This only validates configuration; the mirror's network reachability and a complete build must be tested in a **new** user-initiated run from `main`. Do not re-run run #4, which uses its original commit. No automatic CI, schedule or background monitoring is introduced. Debug APKs, if later produced, are not production-signed or validated on Pixel 8a/LineageOS 24.
