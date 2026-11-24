#!/usr/bin/env bash
# Explicitly user-triggered, foreground-only SDK installation for GitHub Actions.
# API 37.0 has a minor-version package ID; "platforms;android-37" does NOT exist.
set -euo pipefail
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk_root" ]]; then
  echo 'ANDROID_HOME / ANDROID_SDK_ROOT is required to install the SDK.' >&2
  exit 1
fi
if ! command -v sdkmanager >/dev/null 2>&1; then
  echo 'Android sdkmanager command-line tools are unavailable.' >&2
  exit 1
fi
printf '%s\n' 'SDK_PLATFORM_PACKAGE=platforms;android-37.0' 'BUILD_TOOLS_PACKAGE=build-tools;36.0.0'
sdkmanager --install 'platforms;android-37.0' 'build-tools;36.0.0'
platform_jar="$sdk_root/platforms/android-37.0/android.jar"
if [[ ! -s "$platform_jar" ]]; then
  echo "Android SDK platform 37.0 missing at $platform_jar. Inspect sdkmanager packages, not android-37." >&2
  exit 1
fi
for tool in apksigner aapt; do
  if [[ ! -x "$sdk_root/build-tools/36.0.0/$tool" ]]; then
    echo "Android SDK build-tools/36.0.0/$tool is absent or not executable." >&2
    exit 1
  fi
done
printf 'ANDROID_SDK_PLATFORM=37.0_INSTALLED\nANDROID_SDK_PLATFORM_PATH=%s\n' "$platform_jar"
