#!/usr/bin/env bash
# Manual, foreground-only Android build prerequisite audit. No downloads or changes.
set -u
cd "$(dirname "$0")/.."
fail=0
case "${1:-legacy}" in
  legacy) sdk_version=37.0 ;;
  gecko) sdk_version=37.2 ;;
  *) echo 'Expected gecko or legacy' >&2; exit 2 ;;
esac
if command -v java >/dev/null 2>&1; then
  echo 'JAVA_FOUND=YES'
  java -version 2>&1 | head -1
else
  echo 'JAVA_FOUND=NO'; fail=1
fi
expected_jar='7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d'
expected_dist='84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae'
wrapper='gradle/wrapper/gradle-wrapper.jar'
props='gradle/wrapper/gradle-wrapper.properties'
if [[ -f "$wrapper" ]]; then
  echo 'GRADLE_WRAPPER_FOUND=YES'
  checksum=''
  if command -v sha256sum >/dev/null 2>&1; then
    checksum="$(sha256sum "$wrapper" | cut -d' ' -f1)"
  elif command -v shasum >/dev/null 2>&1; then
    checksum="$(shasum -a 256 "$wrapper" | cut -d' ' -f1)"
  fi
  if [[ "$checksum" == "$expected_jar" ]]; then
    echo 'GRADLE_WRAPPER_SHA256=PASS'
  else
    echo 'GRADLE_WRAPPER_SHA256=FAIL_OR_UNAVAILABLE'; fail=1
  fi
  if [[ -f "$props" ]] && \
      grep -Fxq 'distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.0-bin.zip' "$props" && \
      grep -Fxq "distributionSha256Sum=$expected_dist" "$props"; then
    echo 'GRADLE_DISTRIBUTION_PIN=PASS'
  else
    echo 'GRADLE_DISTRIBUTION_PIN=FAIL'; fail=1
  fi
elif command -v gradle >/dev/null 2>&1; then
  echo 'GRADLE_FOUND=YES'
  if raw_version="$(gradle --version 2>/dev/null)"; then
    :
  else
    raw_version=''
  fi
  gradle_version="$(printf '%s\n' "$raw_version" | sed -nE 's/^Gradle ([0-9]+(\.[0-9]+)+)$/\1/p' | head -1)"
  if [[ "$gradle_version" == '9.7.0' ]]; then
    echo 'GRADLE_VERSION=9.7.0_VERIFIED'
  else
    echo 'GRADLE_VERSION=UNSUPPORTED_EXPECT_9.7.0'; fail=1
  fi
else
  echo 'GRADLE_FOUND=NO'; fail=1
fi
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${HOME}/Android/Sdk}}"
if [[ -f "${sdk}/platforms/android-${sdk_version}/android.jar" ]]; then
  printf 'SDK_37_FOUND=YES\nSDK_PLATFORM=%s\n' "$sdk_version"
else
  printf 'SDK_37_FOUND=NO\nSDK_PLATFORM_MISSING=%s\n' "$sdk_version"; fail=1
fi
if command -v adb >/dev/null 2>&1; then
  echo 'ADB_FOUND=YES'
else
  echo 'ADB_FOUND=NO'
fi
if [[ "$fail" -eq 0 ]]; then
  echo 'BUILD_PREREQUISITES=PRESENT_UNVERIFIED'
else
  echo 'BUILD_PREREQUISITES=MISSING'
fi
exit "$fail"
