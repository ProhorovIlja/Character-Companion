#!/usr/bin/env bash
# One manual build step. Verify a fresh debug APK before copying it for download.
set -euo pipefail
cd "$(dirname "$0")/.."
flavor="${1:-}"
case "$flavor" in gecko|legacy) ;; *) echo 'Expected gecko or legacy' >&2; exit 2 ;; esac
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
tools="$sdk/build-tools/36.0.0"
apk="app/build/outputs/apk/$flavor/debug/app-$flavor-debug.apk"
output="dist/CharacterCompanion-$flavor-debug.apk"
mkdir -p dist
rm -f -- "$output"
test -n "$sdk"
test -s "$apk"
unzip -tqq "$apk"
entries="$(unzip -Z -1 "$apk")"
grep -Fxq 'AndroidManifest.xml' <<< "$entries"
grep -Fxq 'classes.dex' <<< "$entries"
certs="$("$tools/apksigner" verify --verbose --print-certs "$apk")"
printf '%s\n' "$certs"
grep -Fq 'CN=Android Debug' <<< "$certs"
badging="$("$tools/aapt" dump badging "$apk")"
version="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' app/build.gradle.kts)"
code="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' app/build.gradle.kts)"
test -n "$version"
test -n "$code"
grep -Fq "package: name='dev.caicompanion.app' versionCode='$code' versionName='$version'" <<< "$badging"
if [[ "$flavor" == gecko ]]; then
  grep -Eq '^lib/arm64-v8a/[^/]+[.]so$' <<< "$entries"
fi
cp -- "$apk" "$output"
printf 'VERIFIED_DEBUG_APK=%s\n' "$output"
