# Independent conversation reference — 2026-11-24

shared/conversation.py connects the existing SQLite history reference to a foreground OpenAI-compatible SSE provider. The only allowed HTTP destination is numeric IPv4 loopback, with no redirects, proxy, external credential, or cloud fallback. Start a trusted llama-server separately for integration testing; no engine is bundled or launched by this change. Do not expose it publicly. A local process can impersonate an unauthenticated loopback endpoint; native integration needs a managed/authenticated engine.

The caller supplies the character identity and stable account/device/chat/character/message IDs. User text is persisted first. Recent same-character/same-chat messages are selected within a UTF-16 budget; actual tokenizer-based budgets and character memory retrieval remain to be implemented. Output fragments are delivered as they arrive; only a normally completed stop + DONE text reply is persisted. A completed retry returns the saved reply without another HTTP request. A cancelled or interrupted request retains the user message. Concurrent history advance rejects the final reply. Separate in-flight requests may still duplicate inference. The prototype is for text, not delivered-audio transcript accounting; cancellation closes the iterator on its owning thread, not an immediate cross-thread interrupt.

Defaults: 12,000 UTF-16 content units, maximum 200 history rows, 1,024 generated tokens requested, 2 MiB response, 128 KiB line, 30 second socket inactivity timeout. These are provisional limits, not token-count guarantees or total deadlines. Length-limited replies are treated as unfinished. No actual model/quality/latency measurements, native UI, native adapter, encrypted cloud sync, voice or multimodal runtime. No background tasks.

Verification: 8 new unittest cases against a real loopback HTTP fixture, plus all 8 existing local HistoryStore tests, passed on Python 3.13. Run python -m unittest discover -s tests -p 'test_conversation.py' -v. Test fixture not a real LLM.

References reviewed, no code copied:
- https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md
- https://github.com/dojekon/AIPaca (local runtime lifecycle, streaming)
- https://github.com/gthgomez/PrismLocal (JNI, streamed tokens, RAG)
- https://github.com/SolvAI/PocketCompanion (on-device ASR/TTS)
- https://github.com/Vali-98/ChatterUI (local and API provider interface)
- https://github.com/rushi-gulum/LocalSLM (Material 3, JNI streaming)
- https://www.reddit.com/r/LocalLLaMA/comments/1u8lbxo/best_models_for_a_12gb_vram_card/ (community hardware discussion, not benchmark)
