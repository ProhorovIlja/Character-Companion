# Upstream research — 2026-11-24

Current scope: Character.AI Android client, Pixel 8a / LineageOS. No third-party source copied or dependency added in this revision.

| Reference | Relevant capability | Decision |
|---|---|---|
| [Mozilla Android Components](https://github.com/mozilla-firefox/firefox/tree/main/mobile/android/android-components) | Browser state, GeckoView integration | Reference for lifecycle/session architecture; don't import the whole browser stack. |
| [Candy Browser](https://github.com/sk2andy/candy-browser) | Dual engine, extension management, gesture-first UI | Study per-engine privacy controls and interaction design; extension integration needs actual APK validation and license review. |
| [EinkBro](https://github.com/plateaukao/einkbro) | Lightweight WebView browser | Reference for compact interaction and bounded browser state. |
| [Brave adblock-rust](https://github.com/brave/adblock-rust) | MPL-2.0 native network/cosmetic filter engine | Candidate for full filter grammar; defer JNI/NDK dependency until reproducible Android builds and benchmark baseline. |
| [Orbit](https://github.com/egeeuzun/orbit) | Native blocker with host fast path | Reference for preserving the current cheap host lookup before any heavier engine. |
| [Marmalade STT](https://github.com/maxwhipw/marmalade-stt-android) | Offline voice keyboard and Android RecognitionService | Candidate external provider; user installs/configures it separately, no bundling or claim of device validation. |
| [WhisperInput](https://github.com/alex-vt/WhisperInput) | Offline input panel and keyboard | Candidate external provider, same limitations. |
| [FUTO Voice Input](https://github.com/futo-org/voice-input) | Speech input through generic Android APIs | External compatibility candidate; license review needed before code reuse. |
| [Dictus](https://github.com/getdictus/dictus-android) | Local voice keyboard | Candidate external IME. |
| [LettuceAI](https://github.com/LettuceAI/app), [RPClient](https://github.com/KafuuNeko/RPClient) | Local-first roleplay UX | Reference for character library/editor, not compatible Character.AI service API replacements. |

Implemented in this revision: manually invoked Android API 33+ RecognitionSupport query for selected offline language, provider-reported installed/pending/available states, system voice settings access with fallback, lifecycle invalidation of stale probe callbacks, and pure language-tag classification tests. No microphone starts from diagnostics; no model downloads, scheduled tasks or provider changes. Android documents the APIs at https://developer.android.com/reference/android/speech/SpeechRecognizer and https://developer.android.com/reference/android/speech/RecognitionSupport. Provider support is advisory, not proof of offline operation. External provider compatibility via system speech/IME must be verified on device; system recognizer can use network and requires consent as before.

Do not add entire projects as dependencies without ABI, license, size, battery and session compatibility review. Native OAuth is still unresolved; no cookie extraction or private API assumption.
