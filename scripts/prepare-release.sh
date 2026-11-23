#!/usr/bin/env bash
# MANUAL, foreground-only release packaging. Refuses unsigned/debug/unverified APKs.
# Never creates a GitHub Release, starts CI, schedules tasks or stores signing keys.
set -euo pipefail
cd "$(dirname "$0")/.."
VERSION='0.15.0-rc1'
PKG='dev.caicompanion.app'
APK="${1:-}"
SIGNOFF="${2:-}"
if [[ -z "$APK" || -z "$SIGNOFF" || ! -s "$APK" ]]; then
  echo 'Usage: bash scripts/prepare-release.sh /absolute/path/to/signed-release.apk /path/to/device-signoff.txt' >&2
  echo 'No APK or device signoff: release preparation intentionally refused.' >&2
  exit 2
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
if ! grep -Eq "^package: name='$PKG' versionCode='15' versionName='$VERSION'" <<< "$BADGING"; then
  echo 'Package ID/version mismatch. Refusing to label APK as this version.' >&2; exit 2
fi
if ! command -v unzip >/dev/null || ! unzip -tq "$APK" >/dev/null; then
  echo 'APK ZIP integrity check failed.' >&2; exit 2
fi
if [[ ! -f docs/RELEASE_TEMPLATE.md ]]; then
  echo 'Missing checked-in release notes template.' >&2; exit 2
fi
OUTPUT="${RELEASE_OUTPUT_DIR:-$(pwd)/out/release-$VERSION}"
mkdir -p "$OUTPUT"
cp -- "$APK" "$OUTPUT/CharacterCompanion-Android-$VERSION.apk"
git archive --format=zip --output="$OUTPUT/CharacterCompanion-Android-$VERSION-source.zip" HEAD
git archive --format=tar HEAD | gzip -n -9 > "$OUTPUT/CharacterCompanion-Android-$VERSION-source.tar.gz"
cp -- docs/RELEASE_TEMPLATE.md "$OUTPUT/RELEASE_NOTES.md"
(cd "$OUTPUT" && sha256sum ./*.apk ./*.zip ./*.tar.gz > SHA256SUMS.txt)
cat > "$OUTPUT/BUILD_PROVENANCE.txt" <<INFO
version=$VERSION
commit=$(git rev-parse HEAD)
package_id=$PKG
apk_signature=verified_by_apksigner
manifest=verified_by_aapt
qa=provided_manual_device_signoff
release_upload=NOT_PERFORMED
INFO
printf 'RELEASE_KIT_READY=%s\n' "$OUTPUT"
printf '%s\n' 'This does not publish a GitHub Release. Review artifacts and notes before manual upload.'
