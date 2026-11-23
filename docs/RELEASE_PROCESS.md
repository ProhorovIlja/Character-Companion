# Android release process — manual-only, no automations

This repository has no GitHub Actions, schedules, upload hooks, services or automated release jobs. A GitHub Release is **not** available until a real APK has been assembled, tested and signed.

## Local requirements

- Official Gradle 8.13 Wrapper binary (full GitHub checkout), JDK 17+, Android SDK Platform 36, build-tools with ``aapt` (for dump badging) and `apksigner`.
- Release signing key owned by the maintainer. Never commit `.jks`, keystore properties, passwords or service-account credentials.
- A configured build/signing method under maintainer control. `:app:assembleRelease` is not assumed to create a signed APK by default.
- Real Android devices or emulators on API 29, 33, 35, 36 and 37; a separate manual device signoff.

## Commands

```bash
bash scripts/test-offline.sh
bash scripts/doctor.sh
./gradlew --no-daemon --offline :app:assembleRelease  # after dependency caching and signing setup
# Verify on devices, then write a separate device-signoff.txt with DEVICE_TESTS_PASSED=YES.
bash scripts/prepare-release.sh /absolute/path/to/signed-release.apk /absolute/path/to/device-signoff.txt
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

`docs/ANDROID_17.md` describes a manual Pixel 8a/LineageOS API 37 device matrix. The current app targets API 36 intentionally; merely setting API 37 under AGP 8.13 is not a valid migration. `docs/XAPK_AUDIT.md` explains the 256-MiB Drive connector limit and safe, manual XAPK splitting and static inspection. Do not include third-party app APKs/Dex/assets in releases or claim successful reverse analysis before bytes are available.

## Offline native bookmark QA (0.15.0 source candidate)

- Launch with native home enabled while offline; create a bookmark to a public Character.AI link; confirm no WebView navigation occurs.
- Reject `https://evil.example/`, login/OAuth pages, oversized URLs and malformed schemes.
- Rename an existing bookmark; verify search refreshes and the old URL remains canonical.
- Toggle filters while the native home is open; verify no network reload until the web surface becomes visible. A direct navigation should load the new page once, never reload the old one first.
- Return from background and fullscreen keyboard interactions; verify browser chrome and media permissions remain safe.
