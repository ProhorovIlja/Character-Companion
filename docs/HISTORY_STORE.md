# Local-first history store — 2026-11-24

Reference SQLite implementation of the v1 continuity protocol in shared/history_store.py. Not yet linked to Android or WPF; Python is for conformance, not an app runtime dependency.

- One local vault account UUID, immutable messages, chat ownership, atomic local logical clocks, transactional bounded imports, conflict rejection and retry idempotence.
- Indexed newest-first keyset pagination, 50 default / 200 maximum rows per call. Snapshot export splits by 2,048 events and 4 MiB encoded bytes, not a total history cap.
- WAL + FULL synchronous for durability on local disks; one connection per thread. Exhaust or close export iterators promptly; long snapshots can grow WAL. No copying live database/WAL files to Google Drive.
- Eight local tests cover reopening, account mismatch, clock advance, retries, rollback, isolation, paging, 2,051 messages, byte budget, concurrent snapshot and incompatible schema.
- No encryption, deletion/tombstones, edit/branch events, signed account binding on the wire, media persistence, native adapters or cloud transport. Do not upload plaintext exports; account UUID is local namespace only, not authentication.
- Next delivery gate: native SQLite adapters and a local chat screen before model integration, followed by authenticated encrypted sync and conflict/deletion tests. No scheduled or automatic jobs.

Design references reviewed (no third-party code imported):
https://sqlite.org/wal.html
https://developer.android.com/topic/performance/sqlite-performance-best-practices
https://learn.microsoft.com/en-us/dotnet/standard/data/sqlite/transactions
https://github.com/LettuceAI/app
https://github.com/Cassiur/JavaTavern
https://github.com/KatakiRPAI/kataki
https://github.com/SolvAI/PocketCompanion
https://github.com/DooDesch/SillyTavernCompanionApp

Candidate ideas: native streaming chat, provider independence, character-scoped memory, mobile GGUF runtime and resource management; verify license and real device performance before reuse.
