# Mandy AI — product and contextual search decision (2026-11-24)

Mandy AI is the product name for the independent private companion. Multiple fictional or user-created character profiles are supported in the product design, not tied to the name Mandy. Preserve existing repository URL, application IDs, data paths and signing identities until native migration is tested. Character.AI is optional legacy compatibility.

## Chat-first interface

One sidebar: character list, selected character's conversation history, create character, settings. Android uses adaptive drawer and phone navigation, Windows keyboard/mouse and resizable sidebar. No duplicate top-level Chats and Characters, no separate Research or Continuity page in the primary navigation. Search is a capability during an active conversation, not a composer mode. Show a compact searching state and expandable sources only when used. Settings: AI engines, voice library, privacy and data sources, appearance, storage/sync, updates. Character editor: avatar upload, identity, style, voice preset or authorized sample, lore, memories, relationship evolution, conversation history. Store both sides' text/voice transcripts, not recordings by default. Images/audio/video/link attachments require real declared model capabilities; distinguish transcript-only processing from actual audio understanding. No screen sharing. Existing native app UI is not changed in this revision.

Memory is character-specific learned information and experience; continuity is the replication of history, character versions, memories, settings and selected small assets between devices. Drive for encrypted private user data; GitHub for public code/release artifacts, never private conversation data. Sync and update checks only on explicit request; no timed/background/event jobs. Within a user-initiated turn, model-directed search may run without a separate search button after source permission is granted. Do not scan unrestricted local disks, connected Drive or GitHub accounts; scope to explicitly selected folders/repositories, read-only by default, no private data in public queries. No persistent microphone or independent research.

## Implemented reference

shared/research.py wraps the previous streaming provider, with a two-pass local model decision: JSON query or null, bounded single search, then enriched streaming answer. Compatible with chat_turn's provider interface; explicit caller wiring required. A trusted loopback SearXNG JSON endpoint is configured separately; no endpoint provisioned. No URL fetching or tool execution. Seven tests plus 16 existing conversation/history tests ran successfully on Python 3.13. Real model and live SearXNG were not tested. Sources are returned in the provider instance, not durably stored as structured citation metadata. Enabled defaults false until user grants public search; final UI should remember this choice. Latest user message only is sent to local planner, not whole history or identity. The generated query may still expose private data if model ignores the prompt: the instruction is not a privacy guarantee. Need robust consent/redaction and scope enforcement before native deployment. Untrusted evidence labeling is not immunity to prompt injection. Limits: 240 chars query, five results, 600 chars per snippet, 256 KiB response, ten-second socket timeout, two inference passes; no measured latency or tokenizer-based overall context budget. No local file/Drive/GitHub search adapters yet. No native builds.

## References and candidates

- llama.cpp server and function calling: https://github.com/ggml-org/llama.cpp/blob/master/tools/server/README.md — future structured tool calls with model template compatibility.
- SearXNG: https://docs.searxng.org/dev/search_api.html — JSON search endpoint must be enabled on the instance.
- SillyTavern: https://github.com/SillyTavern/SillyTavern-Docs — web context, avatars and character cards.
- Project Ciel: https://github.com/VuloB1/project-ciel — character identity, mood, memory, voice and avatar. GPL-3.0 project: architectural reference, no copied code.
- Android SAF: https://developer.android.com/training/data-storage/shared/documents-files — user-selected file/folder access.
- Material Symbols: https://github.com/google/material-design-icons — inspect license and bundle only required assets.
- YuriOS: https://github.com/yuri-os/YuriOS — scoped search/tool ideas only, exclude always-on behavior.
- Community: https://www.reddit.com/r/LocalLLaMA/comments/1x3rtkb/what_are_you_doing_for_a_reliable_well_designed/ — search reliability, fallback, context size; anecdotal.
