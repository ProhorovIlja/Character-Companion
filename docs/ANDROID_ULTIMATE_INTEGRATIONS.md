# Ultimate integrations — 2026-11-24

User requested Discord RPC, screen-off/background character voice calls, release updates, optimization, stability and QoL. This revision implements a manual release center in both renderer menus and browser-owned session. No releases currently published; debug Actions artifacts are not release updates.

## Discord Rich Presence
Official Discord Social SDK mobile GA 1.10 supports Android Rich Presence (including unauthenticated presence support introduced August 2026). See https://discord.com/blog/mobile-platform-support-arrives-in-discord-social-sdk and https://discord.com/developers/docs/social-sdk/release_notes.html . Integrate only after obtaining the authorized application ID, SDK distribution and applicable terms/ABI review. No user token, selfbot, private gateway or impersonation. Default off; generic activity, elapsed time, text chat/voice mode; optional character name and link only after explicit consent. Clear on disable/end; no transcript, private URL, account details or unconsented telemetry. Native SDK not yet integrated. Browser-owned session cannot reliably observe live chat state.

## Voice continuity
Separate user-initiated voice call from short voice dictation. Android microphone and mediaPlayback foreground service types, runtime permission and visible notification with mute/end; start from visible activity only. See https://developer.android.com/develop/background-work/services/fgs/service-types and https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start . Audio focus, Bluetooth/headset routing, audio interruption, screen lock and network changes need physical-device QA. A service alone does not keep a webpage WebRTC session alive; verify the Character.AI supported call transport before implementing. Never keep dictation recording after leaving editor. Stop on call end/permission revoke, no boot restart or unattended call. Do not claim working background voice until verified.

## Updates
Manual release center currently checks exact GitHub releases/latest endpoint once on user action, displays tag only, opens fixed official releases URL; no background polling, silent installs, credential forwarding or automatic APK downloads. Next stage: signed release assets with explicit engine identity, numeric versionCode, SHA-256 and signing certificate identity; prevent downgrade/cross-flavor replacement; user-confirmed Android PackageInstaller install; optional user-controlled foreground update checks only after consent. Obtainium (https://github.com/ImranR98/Obtainium) reference for release selection, not bundled. Both flavors currently share package ID; do not assume same-flavor upgrades until metadata and signing validated.

## Performance/QoL acceptance
- Compact accessible shell, keyboard and landscape ergonomics, quick library and settings, undo and draft continuity.
- Cold startup/PSS, frame timing, CPU and battery measured on Pixel 8a/LineageOS, compare engines under identical workload.
- No speculative persistent wakelocks, no unnecessary network refresh or duplicate renderer. Optimize only measured bottlenecks.
- Stable signer, install-over-old test, crash recovery, offline and network interruption QA.
- Prioritize actual APK build and device verification before calling features complete.

No Windows change, no automation/scheduled checks, no third-party binary imported.

### Release metadata validation
The update center now compares numeric version names (including installed RC builds) and checks the stable release asset filename, renderer, exact official download URL, upload state, bounded size and published SHA-256 digest format. A matching asset is a candidate only, not a verified APK. A future installer must inspect APK versionCode, application ID, signer and actual downloaded digest. No downloads or unattended checks are triggered by this revision.

### Verified download stage
User-selected stable candidate downloads into private cache; size and SHA-256 are streamed and checked, redirects limited to exact GitHub asset hosts, archive identity/newer versionCode/renderer manifest marker/non-debug flags/current signer set checked. A restricted FileProvider grants read-only access to the system installer after an explicit tap. No silent install, no scheduled checks. Android build and signed release/device tests are required before declaring operational. Key rotation deliberately fails closed; installed debug APKs cannot be silently upgraded to release-signed builds.

### Foreground updater lifecycle and storage
Explicit update dialogs now have Activity-scoped ownership and are dismissed on stop/destroy in all three session modes. A cancelled transfer stops at its next cancellation/read-timeout boundary; cooperative transfer deadline is five minutes. Verified APKs are parsed on the worker, not again on the UI thread; Android independently enforces install policy. Unhanded staged APKs are removed when dismissed, while handed-off files are not deleted by an immediate subsequent download. Old cache entries are reclaimed only on explicit new download; staging limit 1 GiB. References: https://developer.android.com/guide/components/activities/activity-lifecycle and https://github.com/ImranR98/Obtainium . Android lifecycle and installer tests remain necessary.

### Speech input usability
14 language choices (device default plus 13 explicit locales), optional provider partial results shown in a bounded transient preview, first valid candidate among at most five alternatives, no draft modification until final result, cancellation clears preview and re-enables language choice. Language changes invalidate an active model-support query. Upstream references: Android RecognizerIntent https://developer.android.com/reference/android/speech/RecognizerIntent ; https://github.com/maxwhipw/marmalade-stt-android ; https://github.com/futo-org/voice-input ; https://github.com/Adam-s-tech/voice-input-1 . Provider support varies; not a continuous voice-call engine. Discord RPC remains blocked on actual SDK integration and registered application ID (https://github.com/discord/discord-api-docs/blob/main/developers/discord-social-sdk/development-guides/setting-rich-presence.mdx).
