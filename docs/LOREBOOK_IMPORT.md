# Local lorebook import and conversion

This native workspace is available from the overflow menu of both embedded engines and browser mode in the single ARM64 APK. It requires no Character.AI account to prepare documents. Character.AI's official Create → Lorebook → Import screen handles account-backed imports, linking and publication. The companion does not bypass subscription checks, upload files, publish lorebooks, inspect chats or inject prompts.

Supported input: UTF-8 structured TXT (including pasted text and .md files using the same labelled grammar), JSON entries maps and arrays, SillyTavern World Info, embedded character_book in CCv2/v3 JSON, and standalone lorebook_v3. Arbitrary unstructured TXT, PNG card metadata, ZIP, PDF, DOCX and YAML are not supported. JSON uses the Android platform parser after a bounded nesting scan. Maximum 2 million decoded characters, 1000 entries, 256 keys per entry, 512-character title/key, 100,000-character content/description. These are local engineering limits, not service limits. Missing and excessive keywords are preserved and flagged; no silent truncation. Disabled source entries are skipped and counted; other metadata and activation features are not mapped and are flagged. Original files are not modified. TXT export refuses ambiguous fields; JSON export preserves name and description locally but the official editor may require entering those manually. Copy is limited to 100,000 characters. Use explicit Save to retain converted files in local or cloud document storage; current preview is memory-only, not a persistent lorebook database. No network, credentials or new permissions.

References:
- https://support.character.ai/hc/en-us/articles/54347105439515-Importing-Lorebooks
- https://support.character.ai/hc/en-us/articles/52739596326811-Lorebooks
- https://github.com/SillyTavern/SillyTavern-Docs/blob/main/Usage/worldinfo.md
- https://github.com/kwaroran/character-card-spec-v3/blob/main/SPEC_V3.md
- https://github.com/roleplay-studio/character-card
- https://github.com/proto-deus/Silly-Tavern-Explorer

QA: local pure Kotlin text grammar and bounds tests; Android JSON parser, SAF, clipboard, native preview and actual official import require Android build/device testing. No subscription bypass or automated publication.

## Local creation and editing
Create a new draft, edit book name/description, add, preview, edit, duplicate or explicitly delete entries. The original imported document is unchanged. Search titles, keywords and contents with an explicit Search action (up to eight terms); the review-only filter flags missing fields, over-eight keywords and repeated case-insensitive keywords within an entry. Search results map back to original indices. Keywords use one line per key; untouched imported keys are preserved verbatim. Invalid edits leave the dialog open and retain the existing draft. A final entry cannot be deleted, but a new book can replace the draft. No silent truncation or deduplication. The preview remains memory-only; export before closing, which now requires confirmation. Reviewed https://github.com/SamYTTK/ST-EnhancedLorebook and https://github.com/ActualBroeckchen/SLEd as interface references, not copied code. No new dependencies or network access. Actual Android UI testing required.
