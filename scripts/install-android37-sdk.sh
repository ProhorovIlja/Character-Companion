#!/usr/bin/env bash
# Explicitly user-triggered, foreground-only SDK installation for GitHub Actions.
# API 37.x has a minor-version package ID; install only the selected flavor.
set -euo pipefail
case "${1:-legacy}" in
  legacy) sdk_version=37.0 ;;
  gecko) sdk_version=37.2 ;;
  *) echo 'Expected gecko or legacy' >&2; exit 2 ;;
esac
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_root" ]]; then
  echo 'ANDROID_HOME / ANDROID_SDK_ROOT is required to install the SDK.' >&2
  exit 1
fi
if ! command -v sdkmanager >/dev/null 2>&1; then
  echo 'Android sdkmanager command-line tools are unavailable.' >&2
  exit 1
fi
printf '%s\n' "SDK_PLATFORM_PACKAGE=platforms;android-$sdk_version" 'BUILD_TOOLS_PACKAGE=build-tools;36.0.0'
sdkmanager --install "platforms;android-$sdk_version" 'build-tools;36.0.0'
platform_jar="$sdk_root/platforms/android-$sdk_version/android.jar"
if [[ ! -s "$platform_jar" ]]; then
  echo "Android SDK platform $sdk_version missing at $platform_jar. Inspect sdkmanager packages, not android-37." >&2
  exit 1
fi
for tool in apksigner aapt; do
  if [[ ! -x "$sdk_root/build-tools/36.0.0/$tool" ]]; then
    echo "Android SDK build-tools/36.0.0/$tool is absent or not executable." >&2
    exit 1
  fi
done
printf 'ANDROID_SDK_PLATFORM=%s_INSTALLED\nANDROID_SDK_PLATFORM_PATH=%s\n' "$sdk_version" "$platform_jar"
