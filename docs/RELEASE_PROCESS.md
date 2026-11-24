## Source-only v0.28 manual Windows-hosted Android build

For a local, foreground-only Android debug build from a complete GitHub checkout, use `.\scripts\build-apk-windows.ps1 gecko` or `legacy` in interactive PowerShell. The helper verifies the wrapper SHA-256 before invoking Gradle and fails closed when SDK 37 or Java are missing; only the separately user-approved manual workflow; no scheduled/automatic triggers. See [Windows manual build checklist](ANDROID17_MANUAL_BUILD_WINDOWS.md). Neither debug APKs nor production signed artifacts were generated in this source-only checkpoint.

# Android release process — manual-only, no automations

This repository has one explicitly approved manual GitHub Actions build, with no automatic triggers, schedules, upload hooks, services or automated release jobs. A GitHub Release is **not** available until a real APK has been assembled, tested and signed.

## Local requirements

- Official Gradle 9.7.0 bootstrap Wrapper binary (full GitHub checkout) fetching checksum-pinned Gradle 9.7.0, JDK 17+, Android SDK Platform 37, build-tools with ``aapt` (for dump badging) and `apksigner`.
- Release signing key owned by the maintainer. Never commit `.jks`, keystore properties, passwords or service-account credentials.
- A configured build/signing method under maintainer control. `:app:assembleRelease` is not assumed to create a signed APK by default.
- Real Android devices or emulators on API 29, 33, 35, 36 and 37; a separate manual device signoff.

## Commands

```bash
bash scripts/test-offline.sh
bash scripts/doctor.sh
./gradlew --no-daemon --offline :app:assembleGeckoRelease  # or :app:assembleLegacyRelease; signed only after real QA
# Verify the exact signed APK on devices, then complete the signoff fields below.
bash scripts/prepare-release.sh /absolute/path/to/app-gecko-release-signed.apk /absolute/path/to/device-signoff.txt gecko
```

Never set `DEVICE_TESTS_PASSED=YES` before the tests. The packaging script rejects missing signoff, debug APKs, unsigned APKs, wrong package IDs/version labels and corrupt ZIPs. It packages versioned source ZIP/tar.gz and SHA-256 checksums from committed Git HEAD. Review `out/release-*/RELEASE_NOTES.md` and `BUILD_PROVENANCE.txt`.

## Publication

Use the repository's **GitHub Releases** page only after verifying the source archives and APK checksums. Add a version tag and upload the exact release-kit contents. The existing connector exposes source-file writes but does not provide a GitHub Release creation or binary-asset upload action; the preparation script therefore does not claim to publish a release. Use a manual authenticated GitHub release interface or a supported release publishing tool when available. Do not enable CI, watchers, schedules or periodic triggers.

## Manual device checklist

- Install and launch APK; validate package and signing key, upgrade from prior APK with same key.
- Character.AI login via email and browser-backed Google/Apple; document limitations of WebView cookie isolation.
- Browse characters, open chat, send and receive messages, search and saved chat history.
- Check media, keyboard resizing, back gestures, file picker, downloads, fullscreen playback, safe-sharing links.
- Voice calls and camera/microphone permissions only as exposed by the website; verify cancellation on stop.
- Validate offline/network-loss/TLS handling and renderer crash recovery on 3+ failure attempts.
- Test imported filters, exceptions, huge malformed imports, export TXT, settings JSON roundtrip.
- Measure cold start/heap/battery on representative Android 10–17 phones, including Pixel 8a / LineageOS; check TalkBack and small screens.
- Confirm source license notices, release notes, hashes and no signing secrets are shipped.


## Android 17 and official-XAPK analysis

`docs/ANDROID_17.md` describes a manual Pixel 8a/LineageOS API 37 device matrix. The current app targets API 36 intentionally; the former SDK36/AGP8.13 build failed; the current compileSdk37/AGP9.3.1 build still needs real validation. `docs/XAPK_AUDIT.md` explains the 256-MiB Drive connector limit and safe, manual XAPK splitting and static inspection. Do not include third-party app APKs/Dex/assets in releases or claim successful reverse analysis before bytes are available.

## Offline native bookmark QA (0.15.0 source candidate)

- Launch with native home enabled while offline; create a bookmark to a public Character.AI link; confirm no WebView navigation occurs.
- Reject `https://evil.example/`, login/OAuth pages, oversized URLs and malformed schemes.
- Rename an existing bookmark; verify search refreshes and the old URL remains canonical.
- Toggle filters while the native home is open; verify no network reload until the web surface becomes visible. A direct navigation should load the new page once, never reload the old one first.
- Return from background and fullscreen keyboard interactions; verify browser chrome and media permissions remain safe.


## Lazy native startup verification — v0.16 source candidate (pending hardware)

- With native workspace enabled, force-stop and cold-start Character Companion while offline. Confirm no WebView provider process is created merely by opening/filtering/renaming bookmarks. Capture cold-start duration and PSS only via on-device profiling with user approval.
- From the workspace, open a safe Character.AI link: WebView must initialize exactly once, load the intended page and leave no native overlay behind. Close/reopen the hub, background and rotate.
- Receive a safe Character.AI link via Android Share Sheet both from a cold start and with the native library visible. Navigation must bypass the hub. Repeat with a rejected OAuth link and verify no navigation.
- Force an absent/outdated WebView provider on a test device: no uninitialized WebView dereference or crash; show browser fallback and retry. Do not modify the owner's system WebView without consent.
- On Android 17/LineageOS Pixel 8a, measure memory and startup with this build against v0.15. No performance percentage is asserted from off-device tests.

## GeckoView ARM64 preview variant (v0.20.0-rc1)

`bash scripts/build-apk.sh gecko` builds the GeckoView ARM64 debug flavor from a complete GitHub checkout; `bash scripts/build-apk.sh legacy` builds the earlier Android WebView flavor. A flavor-specific signed release requires `RENDERER_FLAVOR=gecko`/`legacy` in the device signoff. Gecko support is experimental: file chooser, downloads, OAuth and media require physical-device validation; see [GeckoView test plan](GECKOVIEW_ANDROID_PREVIEW.md). Neither variant can be called a final release until tested.

## Renderer identity gate (2026-11-24)
Both manual debug APK verification and signed release packaging now validate the application-level `dev.caicompanion.app.RENDERER` marker in the compiled manifest with the SDK's `aapt dump xmltree`. A filename alone is not a renderer identity. Missing, mismatched, ambiguous or resource-valued markers fail closed. Release packaging also rejects `application-debuggable` even if a non-debug certificate was used. Native ELF and ZIP alignment checks remain separate. The in-app updater independently checks the manifest marker and signer on the downloaded APK. See https://developer.android.com/tools/aapt2 and https://github.com/flutter/flutter/issues/39408 for compiled XML inspection context. Debug builds require no production signoff and remain diagnostic only. No automatic release or update operation is triggered by this change.

## Single ARM64 distribution
The normal manual build publishes one combined debug APK, `CharacterCompanion-Android-debug.apk`. The signed release packaging helper now accepts only the combined Gecko build (`gecko` internal build identity). Existing `CharacterCompanion-Android-$VERSION-gecko.apk` naming remains the stable release/updater protocol; no separate legacy release artifact is permitted by the helper. WebView is an in-app fallback and an internal troubleshooting build only. Engine switching preserves a sanitized content link, not login state or page drafts. A real signed release, full Android build, and physical device tests are still required.

## Combined-release migration
The only production asset remains `CharacterCompanion-Android-$VERSION-gecko.apk` for compatibility with previously shipped updater metadata. The updated internal WebView-only build can select that same combined asset on an ARM64 device; legacy-only assets are no longer selected. Migration requires strictly newer stable versionName and versionCode, the same package and current signer set, a non-debug installed build and candidate, and the signed combined renderer marker. Android's installer remains the final authority. Debug builds show an explicit production signing warning rather than downloading an incompatible update. No silent install, key rotation, or unattended release checks. References: https://developer.android.com/reference/android/content/pm/PackageInfo and https://github.com/ImranR98/Obtainium/releases . Android compile and on-device signed migration test required.

## Exact APK and device QA signoff
The manual packager now accepts only a UTF-8 signoff with these seven unique fields, filled in after testing the exact signed combined APK (not a debug APK or another commit):

```text
DEVICE_TESTS_PASSED=YES
RENDERER_FLAVOR=gecko
COMMIT=<40 lowercase hex characters from git rev-parse HEAD>
VERSION_NAME=<exact Gradle versionName>
VERSION_CODE=<exact Gradle versionCode>
APK_SHA256=<64 lowercase hex characters from sha256sum of tested APK>
SIGNER_SHA256=<64 lowercase hex characters from apksigner verify --print-certs>
```

Do not copy placeholders into an actual signoff. A signed release with a single signer is required. The script validates the exact APK bytes and signer, captures the commit used for the source archives, verifies the copied APK checksum and records both digests in provenance. This is a manual QA declaration, not independent cryptographic proof that a device test occurred. No device identifiers or signing secrets are requested. References: https://developer.android.com/tools/apksigner and https://docs.github.com/en/actions/concepts/security/artifact-attestations . The script does not create a release or launch a build.
