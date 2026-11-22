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

## Follow-up 22 November 2026

- [Character.AI official API page](https://support.character.ai/hc/en-us/articles/15063996838299-Is-there-an-API): no supported public developer API. Wrapping the official website avoids unstable unofficial account/session endpoints.
- [Character.AI Login](https://support.character.ai/hc/en-us/articles/23628361893659-Trouble-logging-in-We-ve-Updated-our-Login-Process): email link / Google / Apple identity methods; embedded OAuth may reject WebView regardless of client settings.
- [Character Calls & Voice FAQ](https://support.character.ai/hc/en-us/articles/23957274129691-Character-Calls-Voice-FAQ): mobile app only at time of documentation; do not promise feature parity through WebView.
- [AndroidX WebKit release notes](https://developer.android.com/jetpack/androidx/releases/webkit): version 1.18.0 is stable as of 2026-11-18; avoid unverified alpha-only features.
- [Kotlin compatibility](https://kotlinlang.org/docs/gradle-configure-project.html) and [Android AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes): retained tested-in-documentation dependency family pending actual compilation.
- GitHub survey covered `Edsuns/AdblockAndroid`, `brave/adblock-rust` and Android WebView ad-block browsers. A full third-party filter engine is deferred: license compliance, native binary size, rule-list updates and WebView/API breakage need proof of benefit in device tests. **No third-party implementation code was copied.**
