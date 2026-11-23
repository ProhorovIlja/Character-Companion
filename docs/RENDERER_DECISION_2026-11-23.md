# Character Companion renderer decision — 2026-11-23

## User constraint and critical distinction

The user wants **no mandatory WebView2/Edge Runtime install** for the Windows build and asks us to compare native and web presentation for both Windows and Android. Edge and the WebView2 Runtime are separate components; on Windows 11 the latter is generally already supplied, but avoiding it is a legitimate preference. Replacing WebView2 does NOT make the Character.AI web UI into a native app. A web renderer (or a supported public API and a separately implemented native client) is needed to show a modern Character.AI site.

The Character.AI Help Center still describes the API as unavailable at https://support.character.ai/hc/en-us/articles/15063996838299-Is-there-an-API . No permission to reverse engineer an authentication bypass, steal cookies, clone proprietary APK code, or use unverified private endpoints is implied by an unofficial client project.

## Compared approaches

| Renderer/UI | Windows | Android | Runtime/install tradeoff | Decision |
|---|---|---|---|---|
| **WPF + CEF via CefSharp** | Native WPF shell, embedded Chromium with app | No single shared Android CEF target | Bundles substantial Chromium binaries, no WebView2 Runtime; frequent security updates required | **Selected for current Windows source prototype**: least rewrite of existing microphone/draft feature |
| **Kotlin/Android Views + Mozilla GeckoView** | GeckoView does not target Windows desktop | Ships Firefox/Gecko engine as an AAR, independent of Android system WebView | Increases APK size and requires manual rewrite of browser APIs, permissions, downloads, auth, tabs and crash handling | **Android experimental Gecko ARM64 flavor implemented in source**, NOT built or signed yet |
| Qt Quick/Widgets + Qt WebEngine | Powerful native C++/QML shell and bundled Chromium on Windows | Qt WebEngine is not a viable drop-in for the Android web renderer | Different Qt WebView paths invoke native system web engines, defeating this requirement | Useful desktop alternative, not a single identical renderer for both targets |
| Avalonia UI | Cross-platform native shell | Cross-platform native shell | Its `NativeWebView` implementation on Windows uses WebView2, so it does not solve this requirement by itself | Native UI framework only, not engine replacement |
| Tauri/Electron | Tauri delegates to OS webview (WebView2 on Windows); Electron bundles Chromium | Electron is not a native Android strategy | Tauri violates stated runtime preference; Electron heavier/limited cross-platform reuse here | Rejected for current desktop build |
| Browser extension or external-browser companion | Can reuse user's installed Edge/Firefox | Uses external installed browser | Lowest embedded-engine packaging cost; cannot promise in-app site, shared profile or programmatic send | Useful optional fallback for accessible voice dictation; no cookie export |
| Fully native Character.AI client | Native UI | Native UI | Requires stable authorized service API and full parity (login, character list, chats, media, calls) | Not currently supportable as full replacement |

## Current change

Windows `windows/CharacterCompanion.Windows` uses WPF + `CefSharp.Wpf.NETCore` 156.0.300 on x64; removed WebView2 NuGet/XAML controls, initialized CEF once per process, gave it an app-specific cache, and replaced navigation/JS request handling. No Microsoft Edge or separate WebView2 Runtime installation is required by this source design. **However** the CEF native dependencies, .NET Desktop Runtime (unless published self-contained) and suitable VC++ runtime need validation and packaging on a real Windows x64 machine. An executable or installer was not built in this environment.

The existing audio stack remains `NAudio` + user-selected local `whisper-cli.exe` and GGML model. A JSON envelope bridges a single trusted Character.AI composer; explicit sending still requires uniquely identifiable website controls. The app never claims message receipt from a DOM click. The conservative host blocker may need to be disabled if the website relies on those hosts.

**CEF security limitation:** CefSharp documentation states that it does not implement the Chromium sandbox. Treat this desktop source as pre-alpha. CEF security patch cadence, signed binaries, dependency scan, Windows security settings and a review of the request/popup policy are required before a real release. Do not assume CEF is automatically safer or faster than WebView2. No disable-web-security, certificate bypass, OAuth spoofing or stolen-cookie imports are permitted.

## Android GeckoView migration: concrete missing work

The `legacy` Android `MainActivity.kt` directly depends on `WebView`, `WebViewClient`, `WebChromeClient`, `CookieManager`, service workers, Android WebView file chooser, DownloadManager policy and `evaluateJavascript`. GeckoView is NOT compatible at source level. A responsible migration must separately implement and manually verify:

1. An engine interface and Gecko runtime lifecycle under Kotlin, with teardown on Android lifecycle events and renderer-crash handling.
2. Controlled `NavigationDelegate` and first-party policy for top-level visits, while allowing legitimate authentication/CDN subresources. Google OAuth may still refuse embedded browsers. Use a separate browser flow without leaking auth state.
3. Gecko `ContentBlocking` rules and custom site exceptions; preserve user's current manual host-blocking semantics rather than claiming ad removal.
4. Permission and media prompts (`PermissionDelegate`), browser downloads, file picker, full-screen video and history/back handling. Deny mic/camera by default until an explicit user decision.
5. Cookie, cache, data clearing, restore/session behavior, accessibility, font sizes, IME and keyboard insets, orientation and memory pressure on Pixel 8a (`akita`) running LineageOS 24 / Android 17. Do not copy old WebView cookies into Gecko sessions.
6. Exact GeckoView version, APK size, memory/CPU/energy metrics and site sign-in/call compatibility tests on physical Android 17 hardware. Keep existing Android implementation as a fallback until signoff.

The Windows CEF migration is in source only. **Update v0.23:** a separate `gecko` Android flavor now launches GeckoView (ARM64 Pixel 8a preview), while the `legacy` flavor retains the original MainActivity and all its mature WebView features. The new Gecko flavor does not yet match every feature; see `docs/GECKOVIEW_ANDROID_PREVIEW.md`. Neither engine has been compiled or run on a device here.

## Source references

- [CEF official project and embedding](https://github.com/chromiumembedded/cef)
- [CefSharp official .NET bindings](https://github.com/cefsharp/CefSharp)
- [CefSharp.Wpf.NETCore 156.0.300 on NuGet](https://www.nuget.org/packages/CefSharp.Wpf.NETCore/156.0.300)
- [Mozilla GeckoView standalone engine](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/index.html)
- [Qt platform and module limitations](https://doc.qt.io/qt-6/supported-platforms.html)
- [Avalonia native WebView implementation](https://docs.avaloniaui.net/docs/app-development/embedding-web-content)
- [Microsoft: Edge and WebView2 runtime deployment](https://learn.microsoft.com/en-us/microsoft-edge/webview2/concepts/distribution)
- [CefSharp sandbox limitation](https://cefsharp.github.io/api/118.6.x/html/T_CefSharp_CefSettingsBase.htm)
