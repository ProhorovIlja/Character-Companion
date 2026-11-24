#!/usr/bin/env bash
# MANUAL, foreground-only release packaging. Refuses unsigned/debug/unverified APKs.
# Never creates a GitHub Release, starts CI, schedules tasks or stores signing keys.
set -euo pipefail
cd "$(dirname "$0")/.."
# Read the version from the same Gradle metadata used by the APK build.
# This prevents a stale hard-coded version from incorrectly labeling an APK.
VERSION="$(sed -nE 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' app/build.gradle.kts)"
VERSION_CODE="$(sed -nE 's/^[[:space:]]*versionCode[[:space:]]*=[[:space:]]*([0-9]+).*/\1/p' app/build.gradle.kts)"
if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-rc[0-9]+)?$ || ! "$VERSION_CODE" =~ ^[1-9][0-9]*$ ]]; then
  echo 'Gradle versionName/versionCode is missing or ambiguous; release refused.' >&2
  exit 2
fi
PKG='dev.caicompanion.app'
APK="${1:-}"
SIGNOFF="${2:-}"
FLAVOR="${3:-}"
case "$FLAVOR" in gecko) ;; *) echo "Explicit renderer flavor gecko required for the single combined release APK." >&2; exit 2;; esac
if [[ -z "$APK" || -z "$SIGNOFF" || ! -s "$APK" ]]; then
  echo 'Usage: bash scripts/prepare-release.sh /path/to/app-gecko-release-signed.apk /path/to/device-signoff.txt gecko' >&2
  echo 'No APK or device signoff: release preparation intentionally refused.' >&2
  exit 2
fi
if [[ "$(basename "$APK")" != *"$FLAVOR"* ]]; then
  echo 'APK filename must identify its tested renderer flavor.' >&2; exit 2
fi
if ! grep -Fxq "RENDERER_FLAVOR=$FLAVOR" "$SIGNOFF"; then
  echo 'Device signoff does not match selected renderer flavor.' >&2; exit 2
fi
if ! grep -Fxq 'DEVICE_TESTS_PASSED=YES' "$SIGNOFF"; then
  echo 'Device test gate NOT passed. Refusing to package a release.' >&2
  exit 2
fi
if [[ "$(basename "$APK")" == *debug* ]]; then
  echo 'Debug APK must never be published as a production release.' >&2
  exit 2
fi
if ! command -v git >/dev/null || ! git diff --quiet || ! git diff --cached --quiet; then
  echo 'Requires a clean, committed Git checkout.' >&2
  exit 2
fi
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-${HOME}/Android/Sdk}}"
APKSIGNER="$(find "$SDK/build-tools" -maxdepth 2 -name apksigner -type f 2>/dev/null | sort -V | tail -1 || true)"
AAPT="$(find "$SDK/build-tools" -maxdepth 2 -name aapt -type f 2>/dev/null | sort -V | tail -1 || true)"
if [[ -z "$APKSIGNER" || ! -x "$APKSIGNER" || -z "$AAPT" || ! -x "$AAPT" ]]; then
  echo 'Android build-tools apksigner and aapt (dump badging) required.' >&2
  exit 2
fi
SIGNATURE="$($APKSIGNER verify --verbose --print-certs "$APK" 2>&1)" || {
  echo 'APK signature verification failed.' >&2; exit 2;
}
if grep -Eiq 'Android Debug|CN=Android Debug' <<< "$SIGNATURE"; then
  echo 'APK is signed with an Android debug certificate.' >&2; exit 2
fi
BADGING="$($AAPT dump badging "$APK" 2>&1)" || {
  echo 'APK manifest inspection failed.' >&2; exit 2;
}
if ! grep -Eq "^package: name='$PKG' versionCode='$VERSION_CODE' versionName='$VERSION'" <<< "$BADGING"; then
  echo 'Package ID/version mismatch. Refusing to label APK as this version.' >&2; exit 2
fi
if grep -Fxq 'application-debuggable' <<< "$BADGING"; then
  echo 'Release APK is debuggable. Refusing to package.' >&2; exit 2
fi
if ! "$AAPT" dump xmltree "$APK" AndroidManifest.xml | python3 scripts/check_apk_renderer.py "$FLAVOR"; then
  echo 'Renderer manifest identity does not match release flavor.' >&2; exit 2
fi
if ! command -v unzip >/dev/null || ! unzip -tq "$APK" >/dev/null; then
  echo 'APK ZIP integrity check failed.' >&2; exit 2
fi
# Native segment alignment and archive alignment are independent requirements.
ZIPALIGN="$(dirname "$APKSIGNER")/zipalign"
if [[ ! -x "$ZIPALIGN" ]] || ! "$ZIPALIGN" -c -P 16 4 "$APK"; then
  echo 'APK 16 KiB ZIP alignment verification failed or unavailable.' >&2; exit 2
fi
if ! python3 scripts/check_apk_native.py "$APK" "$FLAVOR"; then
  echo 'APK native ELF alignment verification failed.' >&2; exit 2
fi
if [[ ! -f docs/RELEASE_TEMPLATE.md ]]; then
  echo 'Missing checked-in release notes template.' >&2; exit 2
fi
OUTPUT="${RELEASE_OUTPUT_DIR:-$(pwd)/out/release-$VERSION-$FLAVOR}"
mkdir -p "$OUTPUT"
cp -- "$APK" "$OUTPUT/CharacterCompanion-Android-$VERSION-$FLAVOR.apk"
git archive --format=zip --output="$OUTPUT/CharacterCompanion-Android-$VERSION-$FLAVOR-source.zip" HEAD
git archive --format=tar HEAD | gzip -n -9 > "$OUTPUT/CharacterCompanion-Android-$VERSION-$FLAVOR-source.tar.gz"
cp -- docs/RELEASE_TEMPLATE.md "$OUTPUT/RELEASE_NOTES.md"
(cd "$OUTPUT" && sha256sum ./*.apk ./*.zip ./*.tar.gz > SHA256SUMS.txt)
cat > "$OUTPUT/BUILD_PROVENANCE.txt" <<INFO
version=$VERSION
renderer_flavor=$FLAVOR
commit=$(git rev-parse HEAD)
package_id=$PKG
apk_signature=verified_by_apksigner
manifest=verified_by_aapt
renderer_manifest=verified_by_aapt_xmltree
native_elf_audit=passed_or_no_native_libraries
zip_alignment_16k=verified_by_zipalign
qa=provided_manual_device_signoff
release_upload=NOT_PERFORMED
INFO
printf 'RELEASE_KIT_READY=%s\n' "$OUTPUT"
printf '%s\n' 'This does not publish a GitHub Release. Review artifacts and notes before manual upload.'
