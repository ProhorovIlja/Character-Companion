# Gradle Wrapper provenance

`gradle-wrapper.jar` is sourced from the official `gradle/gradle` GitHub repository at the stable `v8.13.0` tag (Apache-2.0). No project-specific code has been inserted into the jar.

The unchanged Gradle 8.13 **bootstrap wrapper JAR** is verified against the official Gradle reference:

- Wrapper JAR: `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`
- Current **Gradle 9.3.1** distribution ZIP: `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06` (the wrapper bootstraps this newer distribution)

Source: https://gradle.org/release-checksums/

The official jar is committed as a binary only in the canonical GitHub repository. The lightweight local source ZIP has no Gradle wrapper JAR; clone GitHub for the full wrapper or supply Gradle 9.3.1. There is no Android SDK or APK bundled with this repository.
