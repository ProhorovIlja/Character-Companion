#!/usr/bin/env bash
# Manual foreground-only Android build. No CI, background tasks, signing or upload.
# Run from the full GitHub checkout with Gradle wrapper, JDK 17 and Android SDK 36.
set -euo pipefail
cd "$(dirname "$0")/.."
FLAVOR="${1:-}"
case "$FLAVOR" in
  gecko) TASK=':app:assembleGeckoDebug'; APK='app/build/outputs/apk/gecko/debug/app-gecko-debug.apk' ;;
  legacy) TASK=':app:assembleLegacyDebug'; APK='app/build/outputs/apk/legacy/debug/app-legacy-debug.apk' ;;
  *) echo 'Usage: bash scripts/build-apk.sh gecko|legacy (manual foreground build)' >&2; exit 2 ;;
esac
if [[ -f gradle/wrapper/gradle-wrapper.jar && -x ./gradlew ]]; then
  bash scripts/doctor.sh
  ./gradlew --no-daemon "$TASK"
elif command -v gradle >/dev/null 2>&1; then
  bash scripts/doctor.sh
  gradle --no-daemon "$TASK"
else
  echo 'Cannot build APK: full Gradle Wrapper or installed Gradle 8.13 and Android SDK 36 required.' >&2
  exit 1
fi
if [[ ! -s "$APK" ]]; then
  echo 'Build did not produce the expected nonempty flavor-specific APK.' >&2; exit 1
fi
printf 'APK_FLAVOR=%s\nAPK_READY=%s\n' "$FLAVOR" "$APK"
if command -v sha256sum >/dev/null 2>&1; then
  echo "APK_SHA256=$(sha256sum "$APK" | awk '{print $1}')"
fi
