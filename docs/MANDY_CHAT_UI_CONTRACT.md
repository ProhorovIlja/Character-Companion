# Mandy AI — cross-platform chat interaction contract
Date: 2026-11-25. Target design, not a compiled UI or a hardware benchmark.

## Product invariants
- Chat is the primary screen. Keep the character and conversation library together. Desktop: collapsible list/detail sidebar. Compact Android: drawer or list/detail navigation.
- Header: selected character, live call, settings, optional conversation menu. Composer: attachment, multiline draft, dictation, send or stop. Technical settings and diagnostics do not permanently occupy chat space.
- Use one visual language (AMOLED option, restrained accent, consistent lightweight vector icons, accessible labels, visible keyboard focus). Native Android views and WPF are the existing baseline; no framework migration solely for cosmetic reasons. Evaluate Material 3 Adaptive and Fluent controls with real build/size evidence before adoption.
- Contextual research is a capability used during a requested turn; no mandatory search button and no unattended activity.
- Preserve individual identity, memories, drafts, chats and relationship history across model replacement and device changes.

## Composer state contract
| State | Attach | Mic | Primary action | Cancel |
|---|---|---|---|---|
| Editing | If supported | If speech available | Send | Not applicable |
| Recording | Disabled | Active | Stop and transcribe/send if available | Discard audio, preserve original draft |
| Transcribing | Disabled | Disabled | Disabled | Cancel and preserve draft |
| Generating | Disabled | Disabled | Stop response | Preserve pending user message |
| Pending retry | Disabled until resolved | Disabled until resolved | Retry same ID | Explicitly discard retry state |
| Live call | Separate call UI | Conversation microphone | End call | Release microphone |

A visible disabled action must explain why it is unavailable; it must not request permissions or claim functionality. Do not auto-send a stale transcript to a different character. Keep incomplete text separate from durable completed responses. Select a trusted engine once per profile in the eventual UX, with explicit changes in settings; existing per-send confirmation is still experimental.

## Source state on 2026-11-25
- Android dad9d22: fixed composer, scrollable messages, character and settings header, compact dictation/send row, existing stop/cancel handlers retained. Call and Attach disabled. Existing on-device speech and optional Whisper paths are source-only here; Whisper stop-and-send is not yet supported. No SDK compile.
- Windows c1bddf7: character header, scrollable settings panel, composer actions, streaming preview, Send/Stop swap, Ctrl+Enter and Escape, accessibility labels. Attach, Dictate and Call disabled; Windows standalone workspace remains single-character. No .NET compile.
- UI sketches show the future sidebar, iconography, avatars and per-message actions, not a screenshot. No claim of complete feature parity.

## Storage and integration boundaries
Google Drive: use scoped OAuth (appDataFolder for app-private encrypted continuity, or drive.file for explicitly selected documents). Validate cross-client OAuth behavior and recovery-key portability before shipping. Never publish personal chats or keys to GitHub. GitHub: public upstream research, versioned source, releases and verified artifact metadata. Downloads require publisher and platform signing verification in addition to digest checks. No background polling or scheduled jobs are introduced by this work.
Attachments: explicit selection, bounded media sizes, model capability detection, visible attachment chips, no implicit upload, no pretending that text-only models understand raw media.

## Verification gates
Actual Android/Windows build; test small display, keyboard insets, large fonts, high contrast, keyboard focus, character switch and draft recovery, dictation cancel/keep/send, inference cancellation, long history, streaming latency, model RAM/VRAM and device thermals. Record frame time/jank, cold startup, memory, package size and first-token latency before declaring optimization. Establish release baselines on actual Pixel 8a and Windows hardware. Use Android Macrobenchmark/Baseline Profiles only after the native UI is buildable.

## Upstream research candidates
- Android canonical list-detail: https://developer.android.com/develop/ui/views/layout/canonical-layouts
- Material 3 Adaptive: https://developer.android.com/jetpack/androidx/releases/compose-material3-adaptive
- Windows UI: https://learn.microsoft.com/en-us/windows/apps/develop/user-interface
- Keyboard navigation: https://learn.microsoft.com/en-us/windows/apps/develop/input/focus-navigation
- Lucide vector icons (license/attribution review before copying): https://lucide.dev/
- Android benchmarks: https://github.com/android/performance-samples
- Character-oriented UI: https://github.com/LettuceAI/app
- Local inference/mobile controls: https://github.com/a-ghorbani/pocketpal-ai ; https://github.com/Vali-98/ChatterUI
- Voice/image workflows: https://github.com/SolvAI/PocketCompanion
- Capability-based attachment routing: https://github.com/0xZaphod/lmchat
- Drive scopes: https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- Drive app data: https://developers.google.com/workspace/drive/api/guides/appdata
- GitHub releases: https://docs.github.com/en/rest/releases/releases

These are references and candidates, not imported code or verified hardware performance. Assess licenses, security, dependencies, and measurable value before reuse.

## Signature refinement — 2026-11-25
Historical baseline before the later user-selected ChatGPT-inspired direction: compact character header, fixed composer, separate call and dictation, collapsible library, AMOLED with a restrained accent. Avoid repeated redesigns and preserve touch/keyboard accessibility.

- Android 65b4564: explicit chat-model approval scoped to the exact conversation selection ticket; no permanent trust grant. Reuse only while the approved digest remains in the available model inventory. Settings can forget approval. Both on-device and offline Whisper dictation can request stop-and-send, while Stop keeps an editable transcript. If no chat model is approved, the first send still requires model selection/consent. Existing validation, pending-ID retry and cancellation remain. Streaming follows new text only when at the bottom, and history scrolling no longer moves keyboard focus on snapshot render.
- Windows 59d40e3: original native vector icons for settings, call, attach, microphone, send/retry and stop; accessibility names, disabled-action tooltips, no icon fonts or additional UI packages. Empty sends are disabled; pending retry remains available. Attach, microphone and call still require functional integration in this standalone preview.
- Both revisions: source readback verified; Android APK and Windows EXE not built or device-tested in this pass. No performance claims without measurements.

### External references reviewed
- ChatGPT editable dictation: https://help.openai.com/en/articles/12168547-voice-dictation-faq
- Claude dictation and voice distinction: https://support.claude.com/en/articles/10065434-use-dictation-on-claude-mobile
- Android adaptive quality: https://developer.android.com/docs/quality-guidelines/adaptive-app-quality/tier-2
- WPF Fluent source: https://github.com/dotnet/wpf/blob/main/Documentation/docs/using-fluent.md
- Android Macrobenchmark: https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- Upstream Whisper streaming: https://github.com/ggml-org/whisper.cpp/tree/master/examples/stream
- Offline ASR implementation candidate: https://github.com/voiceping-ai/android-offline-transcribe

Next acceptance gates: verify Android real recording, stop, cancel, review/send, no cross-character stale callbacks; compare English/Russian transcription quality and latency before introducing streaming ASR or VAD. On Windows, verify native package startup, keyboard and accessibility, and connect the existing local speech stack to the standalone workspace. Add attachment capabilities only when a real provider can consume the selected media. Measure startup, frame time, memory, power and inference latency on real hardware before claiming improvements. No scheduled/background tasks or new workflow triggers.

## User-selected direction — ChatGPT-inspired native interface (2026-11-25)
This section supersedes the previous Signature visual baseline. Preserve original Mandy branding and character-specific capabilities; use ChatGPT's interaction hierarchy as the reference, not its proprietary assets or implementation. Default style: restrained neutral surfaces and minimal borders, optional AMOLED, light and custom accents. Desktop conversation and composer centered at a readable width, mobile conversation full width; character library replaces generic chat library. Keep the call and settings actions at the top right; attach, dictation, send/retry/stop in one rounded composer. Keep technical model and synchronization controls away from the main conversation. Google Drive and GitHub are integrations accessed contextually or through settings, not permanent chat toolbar clutter.
- Android db52853: rounded theme-aware composer and user message surfaces, primary action treatment; replace the normal composer action row with existing recording or generation controls rather than adding a second toolbar; compact expandable diagnostic status. No changes to model approval, transcript persistence, or native inference.
- Windows 88ce6b1: centered rounded composer (max 800 DIP), neutral user message bubble, compact status; existing native vector actions, virtualization and cancellation remain. Respect current high-contrast or stock-theme fallback.
- Archived project material reviewed: saved full development history and Windows voice/Android continuity report of 2026-11-23. The older Windows WebView-based dictation implementation is historical, not evidence that speech is connected in the new standalone native workspace.
- Official references: https://help.openai.com/en/articles/12168547-voice-dictation-faq ; https://help.openai.com/en/articles/20001274-chatgpt-voice ; https://help.openai.com/en/articles/6825453-chatgpt-release-notes ; https://developer.android.com/guide/topics/ui/accessibility/apps ; https://learn.microsoft.com/dotnet/desktop/wpf/advanced/optimizing-performance-controls .
- Further upstream candidates, not imported: https://github.com/hushai-app/hushai-android (JNI inference), https://github.com/shippopotamuslabs/enclave (local multimodal/voice and ARM optimization), https://github.com/Vali-98/ChatterUI (local model UX). Verify license, API compatibility, hardware support and benchmarks before reuse. Do not assume Adreno or Qualcomm NPU acceleration applies to Tensor G3.
- Source readback verified for both changes. No native builds or hardware benchmarks. Live call and attachment controls remain unavailable, and standalone Windows dictation remains unconnected. No scheduling, workflow triggers or background jobs created.

## Material AMOLED refinement — 2026-11-25
User confirmed ChatGPT's conversation design adapted to Mandy with Material AMOLED. This is a Material-inspired native implementation, not a claim of using the full Material 3 Compose library.
- Android 4eb78d4 / 8c0dee1: independent AMOLED (default) and graphite themes, local explicit appearance choice, save current draft before recreating workspace. Legacy browser-shell appearance remains unchanged. Generation preview uses a single outstanding queued text snapshot, drops intermediate previews when UI is busy, retains complete durable reply.
- Windows b73b282: equivalent black/graphite canvas and composer palette; in-session checkbox, no persistence yet. Conversation viewport capped at 800 DIP. Native WPF controls and high-contrast/stock-theme fallback retained.
- All changes verified by GitHub readback, not compiled or hardware tested.

### Archive reconciliation
Reopened all seven available long-form text histories/reports plus v0.41 release notes, v0.10–0.19 release notes and the archived manual release procedure in the project Drive folder (19 text documents). Older reports are evidence of historical source and test status, not proof of functionality in the independent Mandy application. Historical browser shell and standalone native tracks must not be confused; do not carry WebView dependencies into a native client solely because they appear in earlier builds. Source ZIP/XAPK binaries were inventoried but not re-extracted this pass.
Retain: Pixel 8a akita / Tensor G3 / 8 GB, LineageOS 24 Android 17 target with MindTheGapps/Morphe microG considerations; Windows 10/11 Ryzen 5 5600 / RTX 4070 12 GB / 32 GB RAM. No development SDK install on user devices as a delivery prerequisite. Explicit dictation stop/keep or send, live calls, multimodal input, separate persistent character identity/chat/relationship/memory, context-driven web research, scoped encrypted Drive continuity, GitHub source and verified release downloads. No unattended operation or new workflow triggers.

### Hardware research and candidate selection
- Existing Android presets remain provisional: 2/4/6 CPU threads, 2048/4096/6144 context; do not blindly increase. Compare ARM CPU and supported Mali Vulkan, not Snapdragon-specific kernels; published Pixel 8a data includes workloads where Vulkan is slower. https://llamadart.leehack.com/docs/platforms/known-platform-issues ; https://github.com/john-rocky/edge-llm-bench ; https://github.com/ggml-org/llama.cpp/wiki/Feature-matrix
- Windows: CUDA first candidate, AVX2 CPU fallback, compare equivalent model/quant/context via llama-bench https://github.com/ggml-org/llama.cpp/blob/master/tools/llama-bench/README.md . Measure VRAM including KV cache and voice components.
- Model candidates (not verified compatibility or rankings): Qwen3.5-4B and 9B official cards https://huggingface.co/Qwen/Qwen3.5-4B ; https://huggingface.co/Qwen/Qwen3.5-9B ; use suitable trusted GGUF conversions and compare against smaller and newer alternatives. Russian/English naturalness, character consistency, memory, instruction adherence, hallucination and latency tests must determine choice. Avoid treating coding benchmarks as companionship scores.
- Speech: multilingual whisper.cpp baseline, compare sherpa-onnx Russian streaming T-one / GigaAM and multilingual alternatives https://github.com/k2-fsa/sherpa-onnx ; separate TTS evaluations for EN/RU prosody, interruption latency and memory.
- UI: Android dark theme https://developer.android.com/develop/ui/views/theming/darktheme ; Macrobenchmark and Baseline Profiles https://developer.android.com/topic/performance/baselineprofiles/measure-baselineprofile ; WPF Fluent https://github.com/dotnet/wpf/blob/main/Documentation/docs/using-fluent.md . Actual build and physical-device measurement are gates. No automatic jobs created.

## Custom appearance — 2026-11-25
User requires individually configurable colors in addition to default Material AMOLED and graphite. Keep the established ChatGPT-inspired layout. Eight common roles: canvas, composer, bubble, text, muted, accent, outline, icons. Values are opaque #RRGGBB. Validate text against canvas/composer/bubble (4.5:1), muted text against canvas/composer (4.5:1), icons against canvas/composer (3:1), accent against composer (3:1). Derive on-accent black/white automatically. These are checks for the specified pairs, not whole-app accessibility certification. 
- Android 0c4e24b: explicit hex editor, reset fields, persistent custom preference, draft-safe recreation, custom chat surfaces/text/icons and preset native dialogs. 
- Windows 4b194cb / 9a6a40b: hex editor and dynamic brush resources; versioned per-user registry value stores only theme colors and base preset; invalid stored settings fall back to AMOLED. System high-contrast/stock fallback retained at startup. No restart on theme changes. 
- The scope is main chat surfaces and actions, not all dialogs, controls, or system bars. Color picker, named theme collection, theme file import/export and encrypted cross-device theme synchronization remain future work. No additional dependencies. Source readback verified, native compilation/device tests pending. Independent reference math checks of the two presets: minimum text contrast 8.98:1 / 6.44:1, minimum icon contrast 11.47:1 / 8.22:1; same-color example fails at 1:1. Not executed Android/WPF tests. Reopened saved development history for continuity; existing model/voice/Drive/performance requirements remain. Research: https://developer.android.com/codelabs/m3-design-theming ; https://github.com/material-foundation/material-color-utilities ; https://learn.microsoft.com/en-us/windows/apps/design/accessibility/high-contrast-themes . No unattended operations.

## Portable appearance and visual asset selection — 2026-11-25
Android 650d79e and Windows cced496: explicit Copy theme JSON / Import theme JSON inside custom color editor. Import fills fields only; Apply is a separate action, retains existing persistence and contrast checks. Up to 2048 characters, schema 1, name 1–80 characters (display metadata, currently exported as Custom), exactly eight opaque RGB color roles. No automatic clipboard reads, cloud transfers, credentials or chat data. Windows b9cdb7b fixes the action panel return type for resource traversal. Native compilation and real cross-device round-trip remain unverified; Android JSONObject parser and Windows JsonDocument differ in duplicate-member handling, so only unique-member canonical JSON is supported for interoperability. Not yet a named-theme library or encrypted Drive integration.

Canonical sample:
```json
{
  "schema": 1,
  "name": "Material AMOLED",
  "colors": {
    "canvas": "#000000",
    "composer": "#141414",
    "bubble": "#232323",
    "text": "#F2F2F2",
    "muted": "#B5B5B5",
    "accent": "#F2F2F2",
    "outline": "#3D3D3D",
    "icons": "#CCCCCC"
  }
}
```

Design asset candidates reviewed:
- Native system typography remains default (Android sans-serif and Windows system font) to avoid bundled font cost. Evaluate Roboto Flex for optional consistent Latin/Cyrillic variable typography; OFL 1.1, metadata confirms Cyrillic subsets: https://github.com/google/fonts/blob/main/ofl/robotoflex/METADATA.pb and https://github.com/googlefonts/roboto-flex . Do not ship font until readability, fallback, footprint and licensing are checked.
- Existing original vector icons remain; candidates Lucide (ISC and Feather-derived MIT portions; retain applicable notices) https://github.com/lucide-icons/lucide/blob/main/LICENSE and Material Symbols (Apache-2.0) https://github.com/google/material-design-icons . Include only used vectors, not whole icon libraries. Character images: user-selected, bounded local thumbnails, no mandatory image downloads or animated backgrounds.
- Material color utilities HCT and semantic color roles https://github.com/material-foundation/material-color-utilities ; https://github.com/material-foundation/material-color-utilities/blob/main/concepts/dynamic_color_scheme.md . Evaluate small palette-generation subset for optional seed colors; no library imported here.
- Additional native inference comparison candidate: https://github.com/tensai-app/llama.kt includes Mali CPU/Vulkan comparison on another device; does not establish Pixel 8a results or superiority to existing JNI. Keep previous hardware/model/voice gates, assess measured benefit before replacing runtime.
- Reopened saved development history and continuity report plus existing unified architecture and UI contract; all prior requirements remain applicable. Scope of this pass: appearance portability and source correction; no new inference, media or live voice integration, no automation or scheduled tasks.

## Windows native dictation integration — 2026-11-25
The previously archived Windows voice/Android continuity report was reopened, and the existing MicrophoneRecorder, Pcm16Resampler and WhisperCliTranscriber source inspected rather than replacing the speech stack. Commit 9940cb8 links the three speech components and the already-used NAudio 3.3.0 dependency into the browser-free Mandy.NativePreview project; it does not link the old CEF host. Commits d92d208 / 3f04b75 add native microphone control, explicit trusted executable/model file selection, auto/en/ru language selection, Stop/keep, Stop/send, Cancel and Escape. Selection and draft are captured before recording; editing, configuration and sending are disabled while busy. Successful transcription inserts at the selected range and can send via the existing local inference path using dictation mode. Failure/cancellation preserves original draft; closing requests cancellation and waits for the current foreground operation. Temporary audio and text deletion is best-effort, not secure erasure. Shared recorder commit 018dcca moves PCM normalization off the UI thread while awaiting its completion. This is an explicit user-initiated operation, not an unattended job. Current preview requires a user-selected trusted whisper-cli.exe and multilingual GGML .bin; no automatic binary/model download, no bundled runtime yet, and no guaranteed cloud-free behavior of arbitrary third-party executables. Existing recording cap 180 seconds, subprocess timeout four minutes. Source readback verified; no .NET restore/build, Windows microphone test, CUDA/CPU performance test, or transcription-quality validation performed. Full-duplex live calls and attachments remain unavailable. Do not conflate native source with working release.

Upstream speech references reviewed this pass: https://github.com/ggml-org/whisper.cpp/releases ; https://github.com/naudio/NAudio/blob/main/Docs/RecordWavFileWinFormsWaveIn.md (WASAPI recommended candidate for future capture evaluation); https://github.com/k2-fsa/sherpa-onnx (streaming ASR, VAD, TTS across Android/Windows; candidate only). Compare with existing Whisper using identical EN/RU clips, real-time factor, peak memory/VRAM, interruption behavior, and capture stability on both target devices. Keep one verified baseline rather than bundle all engines. No changes to previously selected ChatGPT-inspired Material AMOLED design, portable theme roles, model/voice/media/memory/Drive architecture or manual-only delivery policy. No scheduled or unattended work created.
