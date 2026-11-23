# Character Companion v0.13.0-rc1 (source candidate)

**Android only | SOURCE CANDIDATE | NOT A VERIFIED APK**

## New — fully native offline workspace

- Native, locally rendered launch workspace separate from the remote Character.AI web page. Enabled on first start; can be disabled in Settings.
- Native full-text search across user-saved bookmarks (no network calls or remote chat access); 80-character bounded queries, first-party URL sanitization, up to 100 links.
- Explicit actions to enter the Character.AI WebView, open Character.AI in the system browser / Custom Tab, save an existing page and view privacy statistics.
- Native screen supports Android Back and cleans up keyboard focus when dismissed; long-press Home reopens it.
- Native workspace is included in bilingual English/Russian localization and settings export/import.
- Importing a settings backup **does not re-enable camera/microphone requests** without renewed user consent.

## Build/QA status

- Pure Kotlin policy tests and Python static tests: see verified local test output. Android compilation, OAuth, live chats, voice, Play Protect and Android 17 on Pixel 8a/LineageOS are **not tested**.
- The repository uses AGP 8.13.0 / compile+target SDK 36. AGP 9.1.1+ is needed for direct API 37 compilation. Migrating without a complete toolchain and an actual build is deferred.
- GitHub is source hosting; it can build Android via Actions runners when properly configured, but **no workflows or CI automations are added or enabled**.
- No proprietary Character.AI code/assets or XAPK included.

## Known limitations

- No public Character.AI API: live chat and voice remain service-controlled; the WebView and browser have separate cookie stores.
- No verified release APK / GitHub Release because a complete Android build + device QA is required.
- Third-party ad blocking cannot guarantee complete removal of first-party advertisements.
- Google Play Protect warnings for sideloaded APKs cannot be suppressed by app code.
