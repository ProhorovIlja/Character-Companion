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

## November 2026 comparison (v0.4)

- AndroidX WebKit stable 1.18.0 (2026-11-18): https://developer.android.com/jetpack/androidx/releases/webkit
- WebChromeClient fullscreen / permission APIs: https://developer.android.com/reference/android/webkit/WebChromeClient
- WebViewClient navigation callbacks: https://developer.android.com/reference/android/webkit/WebViewClient
- Edsuns/AdblockAndroid (LGPL-2.1): https://github.com/Edsuns/AdblockAndroid — EasyList/AdGuard syntax but adds a native C++ integration and distribution obligations. Evaluated; not copied.
- AmniX/AdBlockedWebView-Android (MIT): https://github.com/AmniX/AdBlockedWebView-Android — legacy wrapper; not copied because current core APIs are preferable.
- brave/adblock-rust (MPL-2.0): https://github.com/brave/adblock-rust — much more capable engine, but integrating Rust/JNI, maintaining binary ABIs and filters is a separate validated milestone. Not included.

The site itself, not this wrapper, owns account authentication and available chat/call functions. Do not equate complex filter engines with higher reliability: excessive blocking breaks sign-in and chat dependencies. No external source code was merged in this version.

## v0.7 source and supply chain review

- [Gradle 8.13 checksums](https://gradle.org/release-checksums/): the project's wrapper JAR was verified against `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`; Gradle ZIP pinned to `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`.
- The wrapper JAR is from `gradle/gradle` release tag `v8.13.0`, Apache-2.0 licensed. No event-driven GitHub workflow was added.
- [Edsuns/AdblockAndroid](https://github.com/Edsuns/AdblockAndroid), [AmniX/AdBlockedWebView-Android](https://github.com/AmniX/AdBlockedWebView-Android), and [brave/adblock-rust](https://github.com/brave/adblock-rust) remain design references only. The new parser is original, intentionally limited, and does not import upstream licensed filter-engine code.

## v0.8 Android 2026 research

- Android security checklist: https://developer.android.com/privacy-and-security/security-tips (WebView must not expose unnecessary JavaScript bridges or local files).
- Android unsafe WebView file inclusion: https://developer.android.com/privacy-and-security/risks/webview-unsafe-file-inclusion (2026 guidance on file-access restrictions).
- Android WebViewClient reference: https://developer.android.com/reference/android/webkit/WebViewClient (interception limitations; errors must not be conflated with site functionality).
- Character.AI login documentation: https://support.character.ai/hc/en-us/articles/23628361893659-Trouble-logging-in-We-ve-Updated-our-Login-Process (Google/Apple/email sign-in, not a public client API).
- Considered reusing alternative Android WebView adblock projects, but copying unreviewed third-party code is less safe than a bounded auditable domain filter. No third-party source merged.

## v0.9 browser-session fallback — November 2026

- [AndroidX Browser 1.10.0](https://developer.android.com/jetpack/androidx/releases/browser): stable browser support for Android Custom Tabs.
- [Custom Tabs overview](https://developer.android.com/develop/ui/views/layout/webapps/overview-of-android-custom-tabs): browser-backed tabs share browser cookies/permissions and security features, not the WebView cookie jar.
- [Google sign-in best practices](https://developers.google.com/identity/siwg/best-practices): embedded Android WebViews are blocked for Google OAuth.
- A Custom Tab is **not** a credential-transfer bridge, a replacement API for Character.AI, or an app-controlled adblock surface. The fallback must start a clean first-party login journey in the browser.
