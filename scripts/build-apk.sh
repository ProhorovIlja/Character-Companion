#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if command -v gradle >/dev/null 2>&1; then
  gradle --no-daemon :app:assembleDebug
else
  echo 'Gradle is not installed. Open this directory in Android Studio with Gradle 8.13 and Android SDK 36.' >&2
  exit 1
fi
