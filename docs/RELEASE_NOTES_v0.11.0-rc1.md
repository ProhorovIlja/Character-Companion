# Character Companion Android v0.11.0-rc1 — source candidate, NOT a published APK release

**No APK has been produced or signed.** Installation, Character.AI login, chat, calls and Android 17 performance remain untested. Source changes were not reverse engineered from the user's XAPK: its Drive transfer exceeded the connector limit.

## Added

- Read-only offline XAPK/APK archive inventory at `tools/inspect_xapk.py`, with nested split APK inspection, ABI/DEX counts, basic binary Android Manifest decoding, ZIP traversal/size checks and SHA-256 reporting. No code execution, asset extraction or upload.
- `tools/split_xapk.py` to break an original, unmodified XAPK into sequential raw 190-MiB chunks, each below the connected Drive transfer limit, with a SHA-256 manifest. Reassembly is SHA-256 checked.
- Android 17 / LineageOS read-only preflight `scripts/device-android17-preflight.sh` and Pixel 8a signoff plan. No ADB logs, installing software or automatic device actions.
- Synthetic XAPK, AXML, split and device diagnostic regression tests; no copyrighted app code or bytes included.

## Improved

- The Activity explicitly declares resizability for modern Android multiwindow layouts. `compileSdk` and `targetSdk` remain 36 because AGP 8.13 does not support compiling against API 37; an API 37 migration will require AGP/Gradle/SDK alignment and hardware testing.
- Release gate now requires the `aapt` binary that supports `dump badging`, rather than assuming every `aapt2` build supports that command. Verified signed APK and user-approved device test evidence remain mandatory for release preparation.
- Source-only Android 17 and sideloading/Play Protect limitations are explained without promising to suppress install warnings.

## Release status

- APK: **NOT BUILT**
- Source ZIP, source tar.gz: generated separately and verified when published
- GitHub Release: **NOT PUBLISHED** (no signed/tested APK)
- Google Drive backup: source candidate only
- Official Character.AI XAPK: **IDENTIFIED BY METADATA, CONTENT NOT FETCHED** (278,738,234 > 268,435,456 byte transfer limit)

The project is unofficial and not endorsed by Character.AI. No automated jobs, workflows, scheduled tasks or background monitoring were created.
