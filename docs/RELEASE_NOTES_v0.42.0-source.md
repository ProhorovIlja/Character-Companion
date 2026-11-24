# Character Companion v0.42.0-source — 2026-11-24

**Android 0.33.0-rc1, source-only.** Addresses user-reported Character.AI sign-in dialogs being hidden when ad blocking is enabled.

## Authentication compatibility

- For `https://character.ai`, `https://*.character.ai` and `https://c.ai` pages with path segments such as `login`, `signin`, `signup`, `auth`, `authorize`, `oauth`, `verify` or `callback`, the Android app temporarily suspends browser-level tracking/ad filtering.
- Once navigation leaves an auth URL for a normal chat route, protection resumes automatically. Only first-party HTTPS routes qualify; hostname lookalikes, HTTP, nonstandard ports, URL queries and fragments never toggle automatic compatibility.
- Some Character.AI sign-in dialogs are SPA modals that do not change the address. In that case, **long-press the privacy/shield button** (or choose **Login compatibility (temporary)** in Settings), complete sign-in and switch it off manually. A navigation away from the manually exempted path also restores the shield automatically.
- The legacy WebView filters both regular subresources and ServiceWorker requests. GeckoView has an independent tracking-protection mechanism; both receive the compatibility state. The shield displays `LOGIN`/`◇` while temporarily suspended.
- The bypass exists only in memory. It does not disable Safe Browsing, weaken TLS validation, grant camera/microphone/storage access, copy cookies, persist OAuth URLs or automatically create a global allowlist. WebView third-party cookies remain controlled by the existing user setting.
- **Limitations:** Genuine Google/Apple SSO may reject embedded browser environments; the existing browser-backed tab is an explicit fallback and does **not** share the in-app login state. This change is designed to improve login-page rendering, not guarantee that authentication will succeed. If the login modal has no navigation event, explicit restoration is required.

## Checks and packaging

- Pure Kotlin/JVM policy tests plus Python source/structure tests, XML parsing and shell syntax.
- No Android Gradle compilation, APK signing, Pixel 8a / LineageOS 24 / Android 17 hardware test, or server-side authentication testing in this environment. Gradle and Android SDK are absent and external download DNS fails.
- Source snapshots and test evidence are not installable APKs. Windows prototype unchanged. No CI, scheduled tasks, automated monitors or background jobs.
