#!/usr/bin/env bash
# Run manually on a machine with Android SDK 36 and Java 17+; no CI and no timers.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f gradle/wrapper/gradle-wrapper.jar && -x ./gradlew ]]; then
  exec ./gradlew --no-daemon :app:assembleDebug
elif command -v gradle >/dev/null 2>&1; then
  exec gradle --no-daemon :app:assembleDebug
else
  echo 'Cannot build: clone the complete GitHub repository (Gradle Wrapper included), or install Gradle 8.13 and Android SDK 36.' >&2
  exit 1
fi
