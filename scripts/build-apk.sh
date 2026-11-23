#!/usr/bin/env bash
# Run manually only; no GitHub workflow, schedule, background job or upload.
# Requirements: full GitHub checkout with verified Gradle wrapper, JDK 17+, Android SDK 36.
set -euo pipefail
cd "$(dirname "$0")/.."
if [[ -f gradle/wrapper/gradle-wrapper.jar && -x ./gradlew ]]; then
  bash scripts/doctor.sh
  ./gradlew --no-daemon :app:assembleDebug
elif command -v gradle >/dev/null 2>&1; then
  bash scripts/doctor.sh
  gradle --no-daemon :app:assembleDebug
else
  echo 'Cannot build APK: complete Gradle Wrapper or installed Gradle 8.13 and Android SDK 36 required.' >&2
  exit 1
fi
apk='app/build/outputs/apk/debug/app-debug.apk'
if [[ ! -s "$apk" ]]; then
  echo 'Build did not produce a nonempty APK. Check Gradle output.' >&2
  exit 1
fi
echo "APK_READY=$apk"
if command -v sha256sum >/dev/null 2>&1; then
  echo "APK_SHA256=$(sha256sum "$apk" | awk '{print $1}')"
fi
