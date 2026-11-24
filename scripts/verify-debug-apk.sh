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
rm -f -- "$output" "dist/native-audit-$flavor.txt"
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
"$tools/aapt" dump xmltree "$apk" AndroidManifest.xml | python3 scripts/check_apk_renderer.py "$flavor"
if [[ "$flavor" == gecko ]]; then
  grep -Eq '^lib/arm64-v8a/[^/]+[.]so$' <<< "$entries"
fi
# Keep debug artifacts available for diagnosis even when native alignment fails.
# A failed audit must never be interpreted as 16 KiB device compatibility.
{
    if python3 scripts/check_apk_native.py "$apk" "$flavor"; then
        echo 'ELF_CHECK_EXIT=0'
    else
        echo 'ELF_CHECK_EXIT=NONZERO'
    fi
    if "$tools/zipalign" -c -P 16 4 "$apk"; then
        echo 'ZIP_ALIGNMENT_16K=PASSED'
    else
        echo 'ZIP_ALIGNMENT_16K=FAILED_OR_UNAVAILABLE'
    fi
    echo 'DEVICE_16K_RUNTIME=NOT_TESTED'
} > "dist/native-audit-$flavor.txt" 2>&1
cat "dist/native-audit-$flavor.txt"
cp -- "$apk" "$output"
printf 'VERIFIED_DEBUG_APK=%s\n' "$output"
