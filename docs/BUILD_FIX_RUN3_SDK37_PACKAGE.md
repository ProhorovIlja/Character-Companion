# GitHub Actions run #3: Android API 37 package identifier

Run: https://github.com/ProhorovIlja/Character-Companion/actions/runs/45353443323

**Confirmed failure:** the SDK install step failed with `Warning: Failed to find package
'platforms;android-37'`. Java 17 and command-line SDK tools were available;
Gradle did not execute. No APKs were produced.

**Root cause:** Google's Android 17 platform is published as the *minor* API
package `platforms;android-37.0` (path `platforms/android-37.0/android.jar`).
It is not published as the unsuffixed `platforms;android-37` package.
The project's AGP 9.1.1 compiler supports API 37.0. This patch pins
`compileSdk { version = release(37) { minorApiLevel = 0 } }`, installs 37.0
in the one manually triggered GitHub workflow, validates the exact JAR on
Linux/Windows, and adds executable mock-SDK regression tests.

Source references:
- https://developer.android.com/reference/tools/gradle-api/9.1/com/android/build/api/dsl/CompileSdkSpec
- https://github.com/flutter/flutter/blob/master/.agents/skills/updating-android-sdk/SKILL.md
- https://github.com/google-ai-edge/gallery/blob/main/.github/workflows/build_android.yaml

**Verification limit:** the SDK install was tested against a local stub that
simulates accepted/declined platform packages; it is not proof of a real
Gradle compilation. Only a *new*, manually dispatched Actions run from
`main` can verify the SDK platform download and full APK build. Do not
re-run the old run #3 because it retains the old commit.

The existing single workflow continues to allow ONLY `workflow_dispatch`.
There are no push, PR, schedule, background, or monitoring triggers.
