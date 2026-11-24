# Mandy AI — relationship continuity (2026-11-24)

The independent companion models fictional relationships per character, not per model, device or individual chat. Default is strangers; future character creation may seed an existing relationship. Acquaintances, friends, close friends, romantic partners, estranged and former partners are possible descriptions, not mandatory levels. No engagement streaks, message-count escalation, affection rewards, exclusivity demands or claims of real emotions. Changes can go in either direction. Natural swearing and mature discussion, conversational multilingual quality, distinct personalities, and local model choice remain product goals; no claim of unrestricted model behavior.

Implemented: shared/relationships.py adds an optional additive SQLite table to HistoryStore schema 1, preserving the previous messages and account boundary. Explicitly approved revisions reference a user message and a later character message of the same character and chat, verify expected revision and reject reuse/older evidence, and commit atomically. Evidence references do not prove semantic agreement or that the two messages were directly adjacent; caller review is required. A bounded note and current stage are passed to chat_turn via optional relationships argument, scoped to the actual character and same store, included in context budget. No automatic proposal generation yet. Multiple chats for a character share relationship state; new character IDs are separate. History page allows inspection, not deletion or rollback of old revisions. Prior message-only export does NOT include the relationship table; encrypted sync and revision conflict merge remain unimplemented. No private data should be uploaded unencrypted. No actual LLM, voice or native app integration. No new scheduled jobs.

Five new tests and all 23 previous history/conversation/research tests passed (28 total). Tests use synthetic dialogue and local provider fixtures. Test command: python -m unittest discover -s tests -q (reference subset in a clean directory). Default labels and explicit approval are design choices, not model-derived assessments. Future work: stable user personas, character profile seeding and corrections, natural in-turn proposal extraction, episodic retrieval, explicit confirmation of significant changes, and multilingual real-model evaluation.

References reviewed as architectural candidates, no code copied:
- https://docs.sillytavern.app/usage/core-concepts/worldinfo/ (contextual lore)
- https://github.com/aikohanasaki/SillyTavern-MemoryBooks (relationship developments in memory)
- https://github.com/senjinthedragon/Smart-Memory (relationship descriptors)
- https://github.com/Mint658/Ashira-memory (character/user relationship state)
- https://github.com/sachmo99/HearthSocial (persistent relationship state; do not adopt engagement scores)
- https://github.com/Shikigami-Lab/Shikigami-Protocol (persona evolution ideas only, not background behavior)
