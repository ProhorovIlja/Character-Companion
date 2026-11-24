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
