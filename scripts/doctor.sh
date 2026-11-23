#!/usr/bin/env bash
# Manual preflight only: never installs packages, downloads files, schedules tasks or changes settings.
set -u
cd "$(dirname "$0")/.."
fail=0
if command -v java >/dev/null 2>&1; then
  echo 'JAVA_FOUND=YES'
  java -version 2>&1 | head -1
else
  echo 'JAVA_FOUND=NO'; fail=1
fi
if [[ -f gradle/wrapper/gradle-wrapper.jar && -x ./gradlew ]]; then
  echo 'GRADLE_WRAPPER_FOUND=YES'
  expected='81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f'
  actual=''
  if command -v sha256sum >/dev/null 2>&1; then
    actual="$(sha256sum gradle/wrapper/gradle-wrapper.jar | cut -d' ' -f1)"
  elif command -v shasum >/dev/null 2>&1; then
    actual="$(shasum -a 256 gradle/wrapper/gradle-wrapper.jar | cut -d' ' -f1)"
  fi
  if [[ -n "$actual" && "$actual" == "$expected" ]]; then
    echo 'GRADLE_WRAPPER_SHA256=PASS'
  else
    echo 'GRADLE_WRAPPER_SHA256=FAIL_OR_UNAVAILABLE'; fail=1
  fi
elif command -v gradle >/dev/null 2>&1; then
  echo 'GRADLE_FOUND=YES'
  gradle --version | sed -n '/^Gradle /p' | head -1
else
  echo 'GRADLE_FOUND=NO'; fail=1
fi
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${HOME}/Android/Sdk}}"
if [[ -f "${sdk}/platforms/android-36/android.jar" ]]; then
  echo 'SDK_36_FOUND=YES'
else
  echo 'SDK_36_FOUND=NO'; fail=1
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
