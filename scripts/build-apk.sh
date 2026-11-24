#!/usr/bin/env bash
# Manual foreground-only Android build. No CI, background tasks, signing or upload.
# Accepts an official Gradle wrapper OR a locally installed Gradle 9.7.0.
set -euo pipefail
cd "$(dirname "$0")/.."
FLAVOR="${1:-}"
case "$FLAVOR" in
  gecko) SDK_MINOR=2; TASK=':app:assembleGeckoDebug'; APK='app/build/outputs/apk/gecko/debug/app-gecko-debug.apk' ;;
  legacy) SDK_MINOR=0; TASK=':app:assembleLegacyDebug'; APK='app/build/outputs/apk/legacy/debug/app-legacy-debug.apk' ;;
  *) echo 'Usage: bash scripts/build-apk.sh gecko|legacy (manual foreground build)' >&2; exit 2 ;;
esac
bash scripts/doctor.sh "$FLAVOR"
if [[ ! -f gradle/wrapper/gradle-wrapper.jar ]] && ! command -v gradle >/dev/null 2>&1; then
  echo 'Gradle 9.7.0 is required for lightweight source archives.' >&2; exit 1
fi
# Refuse stale output; report success only for the current invocation.
rm -f -- "$APK"
if [[ -f gradle/wrapper/gradle-wrapper.jar ]]; then
  sh ./gradlew --no-daemon "$TASK" "-PcompanionSdkMinor=$SDK_MINOR"
else
  gradle --no-daemon "$TASK" "-PcompanionSdkMinor=$SDK_MINOR"
fi
if [[ ! -s "$APK" ]]; then
  echo 'Build did not produce the expected nonempty flavor-specific APK.' >&2; exit 1
fi
# A nonempty arbitrary file or stale .apk name is not an Android package.
if ! command -v unzip >/dev/null 2>&1 || ! unzip -tqq "$APK" >/dev/null; then
  echo 'APK failed ZIP integrity validation (unzip required).' >&2; exit 1
fi
entries="$(unzip -Z -1 "$APK")"
if ! grep -Fxq 'AndroidManifest.xml' <<< "$entries" || \
    ! grep -Fxq 'classes.dex' <<< "$entries"; then
  echo 'Build output lacks AndroidManifest.xml or classes.dex; not a valid APK.' >&2; exit 1
fi
printf 'APK_FLAVOR=%s\nAPK_READY=%s\nDEBUG_ONLY=YES_DEVICE_UNTESTED\n' "$FLAVOR" "$APK"
if command -v sha256sum >/dev/null 2>&1; then
  echo "APK_SHA256=$(sha256sum "$APK" | awk '{print $1}')"
fi
