# Mandy AI — unified private companion architecture (2026-11-24)

See [current Mandy AI product decision](MANDY_AI_PRODUCT.md) for chat-first navigation, model-directed search, source permissions and branding; it supersedes earlier UI and search proposals.

Primary product: independent private conversational AI, not a coding assistant. Keep existing Android and Windows Character.AI browser shells as optional compatibility modes, with no destructive migration. Native Android UI (ARM64, Pixel 8a Akita, LineageOS/Android 17 target device) and native Windows WPF UI (Windows 10 Enterprise x64, Ryzen 5 5600, 32 GB RAM DDR4-4000, RTX 4070 12 GB, 1080p/240 Hz). Install Windows application and model/cache library on user-selected 1 TB NVMe volume, no assumed drive letter. Feature parity, platform-specific interaction. No screen sharing. No publication of private characters or conversations.

## Shared contracts and persistence

Reference text message protocol implemented in shared/continuity.py. Version 1 JSON UTF-8 envelope {version,events}; immutable message IDs, device IDs, character IDs and chat IDs use canonical lowercase UUID strings. Clock is a positive logical counter <= 2^53-1; order by (clock,id), not device wall clock. Clients must advance their logical clock beyond locally known received messages before emitting new events. User/character roles; text/dictation/call modes; text only, max 16,000 UTF-16 units per message. Batch max 2,048 messages and 4 MiB encoded; split into bounded chunks in future storage layer, not total history limits. Union is deterministic and idempotent within the cap, rejects same ID with different contents or chat linked to different characters. No silent overwrite. This reference is executable Python for cross-platform conformance, not a mobile/desktop runtime dependency or installed feature. Eight unittest methods passed locally on exact sources. Missing: persistent database, pagination/chunk indexing, deletion/tombstones, branching and edits, per-account binding, authenticated encryption, native adapters, real transport. Do not upload plaintext protocol data.

Planned local SQLite per device, immutable message events and revisioned character records. Store transcript for both parties, including actual spoken portion on interrupted responses; no audio recordings by default. Attachments default transient; optionally retain local selected files, bounded thumbnails, hashes, metadata and extracted descriptions/transcripts or source URLs. No unrestricted file retention, no model binaries in sync. Character identity, memory evidence, relationships, dynamic lore, voice settings, conversation preferences and research sources share stable character IDs. Character revisions retain concurrent alternatives for user resolution; do not use wall-clock last-writer-wins for personality or history. Deletion must propagate via authenticated tombstones before live sync. Existing local Android data preserved; migration preview required.

Google Drive is preferred private encrypted sync transport, not a model inference server. Evaluate appDataFolder under same OAuth application or dedicated user-visible folder with drive.file scope; verify both platform OAuth client IDs see the same data before adopting. External browser OAuth/PKCE as supported, never copy browser credentials. Use per-device immutable encrypted chunks with authenticated manifest/version, vetted AEAD and passphrase/key recovery design; secret material held in Android Keystore and Windows protected storage, never upload plaintext keys. No crypto implementation in this change. Explicit Sync now only; no timed, automatic, background or event-triggered sync until separately approved. GitHub for public source/release metadata and signed/versioned installers, model checksums and configuration catalog, not private chat data. Update checks/downloads explicitly initiated; no unattended execution.

## Runtime and media

Native adapters over common versioned engine interface; candidates llama.cpp (CUDA Windows, Android NDK CPU/Vulkan after benchmark), Qwen3.5-9B quantized Windows / 4B or smaller Android, not locked before actual roleplay and memory benchmarks. Model weight files are separate optional downloads with license, hash, compatibility, free disk/VRAM checks and rollback. Android low-memory fallback, measured thermal/battery limits; no promise of Tensor accelerator support. Windows can additionally run higher-quality CPU offloaded candidates, optional authenticated private PC inference for phone while PC reachable (not required, never expose to public internet). Voice/STT/TTS memory budgets must be benchmarked concurrently. No runtime integrated yet.

Dictation state: idle -> recording -> stopping/transcribing -> editable draft. Stop leaves text in composer, subsequent dictation inserts at caret, preserves draft. Send while recording requests finalize then send once, only on success and same chat/draft revision; stale/cancel/fail leaves recoverable draft. Live voice: user-initiated session, streaming VAD + ASR + turn completion (pause is not end), streaming LLM, expressive TTS and barge-in with cancellation of pending generation/playback, echo cancellation, explicit end call, no always-on microphone. Preserve transcript and delivered output, not unspoken generated text as conversation. Candidates sherpa-onnx, whisper.cpp, Silero VAD, Pipecat Smart Turn; TTS evaluate authorized voice references, language support, emotional/prosodic control and model licensing (Pocket TTS current language set does not include Russian in documented CLI). No voice cloning without appropriate authorization. Avoid forcing emotions or verbal fillers.

Research: configured SearXNG JSON endpoint for general search, existing Wikipedia as fallback; public URL retrieval isolated from private tokens, untrusted content labeled, sources retained. Images -> vision-capable local model; audio -> STT; video -> bounded keyframes plus audio transcript. Links or files explicit, no external app required for standard in-app viewing, no screen sharing. Models must have declared input capability; graceful fallback and user-facing limits, no invented perception. Search may be initiated by the model within a user-initiated turn after source permission is granted, not monitoring. No automatic private data access or public query containing private context. Text and voice chat use same context, identity and memory. Local character development uses evidence-backed proposed updates and user-controllable approval/revert; no automatic change of model weights, no invented shared history.

## Delivery gates

1. Shared data contract and tests (initial text reference implemented here).
2. Real native local chat/SQLite and migration, one local model baseline per platform, text generation, draft and character sidebar.
3. Voice dictation, full-duplex pipeline, interruption/echo tests, expressive TTS benchmarks.
4. In-app research and media ingestion; memory retrieval, evidence-based character evolution, revision history.
5. Encrypted Drive transfer with conflict and deletion tests on both devices; manual updates with manifests and rollback.
6. Signed Windows installer and Android APK, real device tests, quality/latency benchmarks and parity matrix. Existing browser app changes remain source-only since last successful older CI build.

Verified candidate sources (not imported code):
https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
https://github.com/ggml-org/llama.cpp/blob/master/docs/multimodal.md
https://huggingface.co/Qwen/Qwen3.5-9B
https://huggingface.co/Qwen/Qwen3.5-4B
https://github.com/k2-fsa/sherpa-onnx
https://github.com/ggml-org/whisper.cpp
https://github.com/pipecat-ai/smart-turn
https://github.com/kyutai-labs/pocket-tts
https://github.com/kyutai-labs/pocket-tts/blob/main/docs/CLI%20Commands/export_voice.md
https://github.com/searxng/searxng/blob/master/docs/dev/search_api.rst
https://developers.google.com/workspace/drive/api/guides/appdata
https://developers.google.com/workspace/drive/api/guides/manage-changes
https://developers.google.com/workspace/drive/api/guides/api-specific-auth
