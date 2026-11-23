# Character Companion v0.16.0-rc1 — Android release notes

**Unofficial Character.AI browser companion. Not affiliated with Character.AI.**

## Added
- Recovery guard for repeated WebView renderer crashes with a retry screen.
- Manual export of imported adblock domain lists as TXT.
- Stricter download handoff: third-party URLs use the external browser.

## Improved
- Cancel pending microphone and camera requests when the app is fully backgrounded.
- More comprehensive offline security and regression tests.
- Reproducible signed-APK verification and source archive packaging.

## Limitations
- Website functionality is controlled by Character.AI and may change.
- Google/Apple identity providers may block OAuth inside WebView; Custom Tabs use separate browser cookies and do not inherit WebView adblocking.
- Only domain-based request filtering is supported, not all page-integrated ads.

## Distribution
- APK (signed, signature/manifest checked before upload)
- Source ZIP and source tar.gz
- SHA256SUMS.txt and BUILD_PROVENANCE.txt

Install only after manually reviewing the device signoff, certificate identity and these notes.
