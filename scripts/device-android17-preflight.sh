#!/usr/bin/env bash
# Manual, read-only diagnostics for Android 17 / Pixel 8a / LineageOS.
# Does not launch apps, collect logcat, inspect private data or install anything.
set -euo pipefail
if ! command -v adb >/dev/null 2>&1; then
    echo 'ADB_FOUND=NO' >&2
    exit 2
fi
# To select among multiple connected devices use ADB_SERIAL in the shell.
args=()
if [[ -n "${ADB_SERIAL:-}" ]]; then args=(-s "$ADB_SERIAL"); fi
api="$(adb "${args[@]}" shell getprop ro.build.version.sdk | tr -d '\r\n')"
case "$api" in ''|*[!0-9]*) echo 'ANDROID_API=UNREADABLE' >&2; exit 2;; esac
printf 'ANDROID_API=%s\n' "$api"
if (( api == 37 )); then
    echo 'ANDROID_17_RUNTIME=DETECTED'
else
    echo 'ANDROID_17_RUNTIME=NO'
fi
os="$(adb "${args[@]}" shell getprop ro.lineage.version | tr -d '\r\n' | cut -c 1-90)"
if [[ -n "$os" ]]; then
    printf 'LINEAGE_OS_PRESENT=YES\n'
else
    printf 'LINEAGE_OS_PRESENT=NO_OR_UNAVAILABLE\n'
fi
# Do not print serial numbers or device identifiers.
if adb "${args[@]}" shell pm path dev.caicompanion.app | grep -q '^package:'; then
    echo 'COMPANION_INSTALLED=YES'
else
    echo 'COMPANION_INSTALLED=NO'
fi
# A successful preflight does NOT demonstrate any actual app functionality.
echo 'LOGIN_CHAT_VOICE_PERFORMANCE=NOT_TESTED'
echo 'PLAY_PROTECT_WARNING_STATUS=NOT_DETERMINABLE_FROM_ADB'
