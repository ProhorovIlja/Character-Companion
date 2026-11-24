# Mandy AI — EN/RU product scope and target-device engineering (2026-11-24)

User decision: English and Russian only are official supported languages for independent Mandy AI on Android and Windows. Applies to chat, dictation, voice conversations, TTS, character identity/memory, search, and future UI localization. Prioritize natural informal dialogue, expressive speech, Russian inflection/stress, punctuation, code switching, names, and relationship continuity. Retain arbitrary Unicode input, files, names, URLs and existing memories without filtering or destructive language migration. Models may intrinsically support more languages; no need to retrain or strip vocabularies merely to narrow support. Do not claim mixed-language ASR until tested. Two explicit language modes first, optional EN/RU-constrained detection only if backend can honor it. Existing browser compatibility features remain unchanged. Windows legacy whisper-cli already exposes Russian, English and unrestricted auto; independent Windows runtime must implement the new two-language scope, rather than treating that legacy option as validated bilingual detection.

Implementation: MandyLanguage.kt defines explicit ENGLISH en-US/en and RUSSIAN ru-RU/ru, no unknown-language fallback. MandyWorkspaceActivity uses only these options and always supplies an explicit locale to the on-device recognizer. Existing native dictation safety behavior and legacy VoiceRecognitionOptions are unchanged. 17 JVM assertions passed against exact enum and test source. Android adapter not SDK-compiled. Windows app and model support not changed by this commit.

## Device-specific requirements

Android target (user-provided software configuration): Google Pixel 8a / akita, Tensor G3, 8 GB shared system RAM, ARM64, Mali GPU, UFS 3.1, LineageOS Android 17, MindTheGapps and Morphe MicroG-RE. Google specs and LineageOS device docs confirm chipset/RAM/storage family, not installed versions or live device condition. Treat Morphe MicroG-RE as a fork intended for Morphe patched apps, not a guaranteed full system GMS replacement. Core text/ASR/TTS must run without GMS, Play Integrity, cloud SpeechRecognizer, or Google account. Android native on-device recognizer is an optional interim path only. Use standards-based external-browser OAuth/PKCE for explicit Drive authorization where supported; verify actual provider behavior on device. Never change ROM, GApps, root, system drivers, or security settings to force compatibility. Keep current target/min SDK and ABI until measured migration. Existing embedded llama.cpp CPU-only source must be built and validated before tuning.

Android candidates: llama.cpp ARM64 CPU (benchmark thread count and optional KleidiAI), Vulkan only after feature probe and CPU parity/thermal tests. Tensor TPU/NPU not assumed exposed by LineageOS; Snapdragon Hexagon/Adreno optimizations do not apply to Tensor G3. NNAPI deprecated since Android 15; evaluate supported LiteRT/other delegates only when operator and device support proven. Begin with Qwen3.5 2B/4B quantized candidates and modest 2K-4K contexts; no assumed fit. Measure available memory, KV/cache/workspace overhead, peak RSS/PSS, low-memory thresholds, thermal status, battery, sustained latency. One heavyweight engine resident initially; avoid simultaneous model loads unless measured safe. On memory pressure release inactive models at safe boundaries and surface a recoverable error. Preserve composer/history under cancellation and process death. Native AudioRecord/AudioTrack, AEC/noise handling and Bluetooth routes must be tested; no assumption of always-available system effects. Keep permissions minimal, use 16 KiB compatible ARM64 libraries, insets/accessibility and AMOLED UI. No background microphone or sync.

Windows target (user hardware): Windows 10 Enterprise x64, Ryzen 5 5600 (6 cores/12 threads), 32 GB DDR4-4000, RTX 4070 12 GB (Ada CUDA SM 8.9), 250 GB plus 1 TB NVMe SSDs, 1080p 240 Hz, ~890 Mbps wired. Native WPF workspace, user-selected app/model/cache location on 1 TB volume, no assumed drive letter. Validate exact Windows build, NVIDIA driver and compatible CUDA runtime; don't force a Windows upgrade. Start with llama.cpp CUDA offload and Qwen3.5 9B Q4/Q5 candidates, CPU AVX2 fallback, tune GPU layers/context/batch/KV quantization, then compare larger models only if VRAM/RAM/latency allow. Avoid VRAM oversubscription and expensive CPU/GPU transfers. Model load: GGUF mmap and normal OS file cache baseline, measured cold/warm loading, pinned staging only if beneficial. DirectStorage/RTX IO is a conditional I/O experiment, not inference acceleration; compressed GPU asset workflows differ from GGUF model loading, Windows 10 path can have extra CPU costs, and GPU decompression uses VRAM. No DirectStorage dependency unless a reproducible measured advantage over mmap and ordinary CUDA transfers justifies it. No global OS tweak, RAM overclock, driver change or unapproved system setting. Benchmark WASAPI capture/playback, default device changes, AEC and Bluetooth separately. Keep app responsive during model load; cancel foreground operations and bound cache/storage. Network: use HTTPS, explicit sync/search permissions, no untrusted binary auto-launch; bounded transfer, resume and hashes where applicable.

## Speech candidates for focused bilingual testing

- Baseline shared ASR: multilingual whisper.cpp (not English-only .en weights); Android quantized base/small, Windows CUDA small/medium/large-v3-turbo candidates. Compare rather than claim speed or quality.
- sherpa-onnx/ONNX Runtime: Russian Zipformer and GigaAM-v3 where supported, English streaming models, VAD/endpointing and offline TTS. GigaAM multilingual reports stronger regional languages and moderate English; evaluate against Whisper; no assumed mixed-language quality. Avoid loading two large ASR engines concurrently on phone. Use the provider selected by the user, not silent cloud fallback.
- TTS: sherpa-onnx compatible RU/EN local VITS/Piper-style voices as lightweight candidate, Chatterbox Multilingual (RU/EN and voice conditioning) on Windows as quality candidate; evaluate actual model/license and mobile feasibility. English-only Pocket TTS/Kokoro candidates cannot alone satisfy Russian. Voice sample only with authorization; preserve consistent character identity across languages; test stress, intonation, interruption and voice reference quality.
- Dialogue LLM candidate families remain provisional: evaluate existing Qwen models and alternatives for informal bilingual text and character stability; no weight merge, training or model download in this iteration.

## Proposed release validation (manual, not scheduled)

Record exact device/OS build, model source/revision/license/hash, backend/compiler/driver, quantization, threads, context, acceleration, battery/thermal state and model cache location. Fixed licensed or synthetic EN/RU and mixed-language corpus, quiet/noisy/long pauses, names, informal Russian, punctuation, no speech. Compare WER/CER by language, code switch errors, ASR RTF and end-to-text latency; TTS first audio latency, RTF, pronunciation, speaker consistency and blind bilingual ratings; dialogue first-token latency, tokens/s, peak RAM/VRAM, sustained 15-minute foreground session, interruptions and character memory. Compare cold/warm model load, battery/thermal, disk footprint and recovery under low resources, no internet and absent GMS. Device-specific presets Balanced / Quality / Low memory only after results; expose advanced per-engine controls, capability labels and restore defaults. Benchmark results must be reported separately for each device, not extrapolated from other GPUs/phones. No background benchmarks, monitoring, automated updates or sync. Full build and actual user-device acceptance are still blockers.

Research sources reviewed (candidate references, not copied code):
https://support.google.com/pixelphone/answer/7158570
https://lineageos.github.io/lineage_wiki/devices/akita/
https://lineageos.github.io/lineage_wiki/gapps/
https://github.com/MorpheApp/MicroG-RE
https://github.com/wazeerc/MicroG-RE
https://github.com/microg/GmsCore/wiki/
https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md
https://github.com/crc-org/llama.cpp/blob/main/docs/build.md
https://developer.android.com/ndk/guides/neuralnetworks/migration-guide
https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo
https://developer.nvidia.com/cuda/gpus
https://github.com/microsoft/DirectStorage/blob/main/Docs/DeveloperGuidance.md
https://developer.nvidia.com/rtx-io
https://github.com/ggml-org/whisper.cpp
https://github.com/k2-fsa/sherpa-onnx
https://github.com/Passw/k2-fsa-sherpa-onnx
https://github.com/salute-developers/GigaAM
https://github.com/salute-developers/GigaAM/blob/main/evaluation.md
https://github.com/mxl/whisper-benchmark
https://github.com/resemble-ai/chatterbox
https://github.com/jamiepine/voicebox
https://huggingface.co/Qwen/Qwen3.5-4B
https://huggingface.co/Qwen/Qwen3.5-9B

No external code imported, no new dependencies or new build/workflow, no actual model/hardware benchmarks, no Android or Windows executable produced.
