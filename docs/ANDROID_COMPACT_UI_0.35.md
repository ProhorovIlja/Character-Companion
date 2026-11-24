# Android 0.35.0-rc1 — compact chat-first shell

Both renderers now share a neutral graphite palette, one 52 dp header, and a 56 dp bottom bar (Home, Saved, Voice, More). Gecko previously used 150 dp of navigation above its viewport; WebView used 183 dp total. New non-compact shell uses 108 dp. Compact mode hides the header; bottom controls remain accessible when the keyboard is closed. No new rendering frameworks, JavaScript injection, or remote asset dependencies.

Secondary navigation, privacy controls, sign-in and settings are reachable in a native overflow menu. Settings open by category rather than a long mixed list. Fresh installs open the website directly; the offline workspace is still explicitly selectable, and existing preference values are respected. Browser session screen has shorter primary copy and expandable details. This is browser-owned session continuation, not native Google OAuth or cookie transfer.

Voice dictation now distinguishes finish (request final provider transcript) from cancel. Unsolicited or late callbacks remain guarded; leaving the Activity cancels active listening. No auto-send. A rejected bookmark no longer silently saves the homepage. WebView clear-site/cache options refuse execution before a WebView exists.

QA required: both APK builds, install and sign-in on real Pixel/LineageOS, portrait/landscape, 320–480 dp widths, enlarged font, keyboard and predictive back, bookmarks/menu/settings, dictation finish/cancel, accessibility labels, renderer recovery. Structural regression checks do not substitute for rendered screenshots or hardware QA. Website content and account UI remain owned by Character.AI.
