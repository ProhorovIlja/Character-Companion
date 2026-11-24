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
