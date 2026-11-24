# Mandy AI — model strategy and acceptance gates (2026-11-24)

Decision: own the companion architecture, use replaceable local neural models and native inference adapters, then evaluate optional custom adaptations. Do not train a foundation model from scratch or combine unrelated weights on the assumption that quality will add up. Preserve identity, conversation, memory, relationship revisions and voice configuration outside weights. One conversational model owns the visible answer in a turn; specialists provide bounded evidence or derived state, not competing personalities. No coding agent or autonomous tasks. All inference and research are scoped to user-initiated interactions. No scheduled training, background jobs, sync or update checks.

## Candidate profiles (not benchmark winners)
Windows Ryzen 5 5600 / 32 GB RAM / RTX 4070 12 GB: llama.cpp CUDA baseline, Qwen3.5-9B GGUF Q4_K_M or Q5 as candidates; test actual free VRAM with context, speech and vision loaded. Test higher-quality roleplay-tuned compatible alternatives and larger CPU-offloaded candidates only if interactive latency is acceptable. Install models and cache on user-selected 1 TB NVMe location. Android Pixel 8a / Tensor G3 / 8 GB shared RAM: ARM64 llama.cpp NDK baseline, 2B–4B quantized candidates including Qwen3.5-4B, smaller fallback, CPU vs supported Vulkan measured on device; do not assume NPU access. Lower memory usage and sustained thermal behavior matter more than peak throughput. Both support same character schema; model choice and runtime parameters are device-local. No remote model fallback without explicit selection.

Separate specialists: offline ASR (sherpa-onnx or whisper.cpp), VAD and semantic end-of-turn (Silero and Smart Turn candidates), expressive multilingual TTS with authorized voice samples, optional local image/audio/video adapters, embedding/retrieval, existing contextual search. Lazy-load resource-heavy modules, reserve memory for voice and OS, release idle model resources within active application lifecycle; no concurrent multi-LLM loading required. Media claims must match actual capability (lyrics/transcript vs audio vs video frames). Unknown capability means unavailable, not pretend perception. No unverified model execution or arbitrary remote code.

## Adaptation and experiments
First baseline: unmodified upstream model, exact revision, tokenizer/chat template, quantization, engine build and license recorded. Compare conversational finetunes using the same held-out fixtures. Optional custom Mandy AI dialogue adapter through LoRA/QLoRA (PEFT/Unsloth); only after baseline is runnable, with licensed/synthetic or explicitly authorized training data, held-out tests, no private chat uploads, and rollback. Adapter is for general dialogue style, not storing a character's private history. Weight merging with MergeKit (e.g. task arithmetic/TIES/SLERP) only when architecture, tensor shapes, tokenizer and base lineage are compatible and licenses permit. Merge in suitable source precision then quantize; compare every candidate with originals. No assumption that a merged model is better or that a 12 GB GPU can train every candidate. No automatic retraining or deployment.

## Acceptance matrix (proposed evaluation, no measurements yet)
Use identical prompts, character profile, context, quantization metadata and sampling settings; repeat at least three seeds where supported. Blind human preference comparisons and task-specific checks, not solely general knowledge leaderboards. No real personal records in fixtures.
- Identity: strangers do not invent shared memories; consistent biography and speech style; switch characters without memory leakage.
- Relationship: recall approved revisions across chats, respect a platonic path or a changed relationship, no engagement pressure, no message-count romance.
- Dialogue: natural English/Russian, ordinary profanity and mature non-graphic discussion, humor, uncertainty, spontaneous but contextual responses, avoid generic assistant tone; no promise of unlimited model behavior.
- Long history: retrieve earlier evidence, distinguish uncertain facts, correction retention, no invented events, preserve context after model swap and restart.
- Research: no lookup on ordinary chat when unnecessary, factual freshness and source grounding when needed, no private queries, degrade gracefully offline.
- Media/voice: actual modality capability, speech recognition accuracy EN/RU, expression/voice consistency, first audible response latency, endpoint false positives and interruption handling, no audio retention by default.
- Runtime: measure cold/warm TTFT, prompt processing and output tokens/sec, p50/p95 end-to-end latency, peak VRAM/RSS, cache size, CPU/GPU utilization, 30-minute sustained thermals and battery on phone, out-of-memory recovery and 16 KiB Android compatibility. Use llama-bench for engine-level performance, separate application-level tests. Do not infer speed or compatibility from model size alone.
- Hard release gates: no cross-character/account leaks, no corrupt history, no unauthorized network access, correct cancellation, license and integrity verification, native device builds and rollback. Record quality and latency tradeoffs separately rather than collapsing all into one score.

## Source references
- https://huggingface.co/Qwen/Qwen3.5-9B
- https://huggingface.co/Qwen/Qwen3.5-4B
- https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- https://github.com/ggml-org/llama.cpp/blob/master/docs/multimodal.md
- https://github.com/ggml-org/llama.cpp/blob/master/tools/llama-bench/README.md
- https://github.com/arcee-ai/mergekit/blob/main/docs/merge_methods.md
- https://github.com/huggingface/trl/blob/main/docs/source/unsloth_integration.md
- https://github.com/k2-fsa/sherpa-onnx
- https://github.com/pipecat-ai/smart-turn
- https://github.com/TwinVoice/TwinBench (persona evaluation reference, inspect data/license before use)

This is a design and evaluation decision, not a trained model, native build or implemented scheduler. Existing reference modules remain unchanged. The next release gate remains native local inference and real-device tests.
