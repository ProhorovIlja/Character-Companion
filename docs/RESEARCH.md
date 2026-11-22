# Source evaluation · November 2026

Source links are references for evaluation, **not code incorporated into this repository**.

- [AndroidX WebKit stable releases](https://developer.android.com/jetpack/androidx/releases/webkit): use 1.18.0 for service-worker compatibility and evolving WebView APIs. WebView behavior depends on the installed browser implementation.
- [Android WebViewClient reference](https://developer.android.com/reference/android/webkit/WebViewClient): `shouldInterceptRequest` limitations for blob/javascript/redirects; correct renderer death handling.
- [Android WebView termination](https://developer.android.com/develop/ui/views/layout/webapps/handle-termination): renderer process recovery.
- [Google embedded OAuth](https://developers.googleblog.com/upcoming-security-changes-to-googles-oauth-20-authorization-endpoint-in-embedded-webviews/): third-party Google login may reject WebView.
- [Character.AI official API FAQ](https://support.character.ai/hc/en-us/articles/15063996838299-Is-there-an-API): no documented public API.
- [Edsuns/AdblockAndroid](https://github.com/Edsuns/AdblockAndroid): Kotlin/C++ filter engine with element hiding; LGPL-2.1; not incorporated because of licensing/dependency/maintenance and compatibility review needs.
- [brave/ad-block](https://github.com/brave/adblock-rust): mature filtering engine reference; would add native bindings, large rule sets and potential maintenance complexity.
- [Maticcm/Open-WebUI-Client-for-Android](https://github.com/Maticcm/Open-WebUI-Client-for-Android): open Android wrapper patterns (file chooser, web permissions), not copied.
- [Husky110/lettuce_app](https://github.com/Husky110/lettuce_app): independent character chat with user-provided models, not a Character.AI client and not mergeable as a service replacement.

Reasons not to merge arbitrary forks: license obligations, outdated endpoint assumptions, account risk and the absence of an official Character.AI API. The implementable and auditable approach is a small native shell using the official web service. No external SDKs or proprietary code were copied.
