# Creator and application feature research — 2026-11-24

This is a scoped research checkpoint, not an exhaustive audit of the official app binary or every public repository. The official web Help Center is the primary source for supported capabilities. The legacy Character Book landing page was located but full text could not be retrieved in this session. No authenticated application UI or current proprietary APK was examined.

| Source / topic | Documented behavior | Companion implementation / remaining work |
|---|---|---|
| Official Lorebooks | Eight keyword limit, three entry fields, shared world details, entry templates, placeholders, draft versus publish | Existing import/editor, new local creator guidance and explicit persistent drafts; service publishing/linking remains official UI |
| What makes a good Lorebook? | Contextual behavioral guidance, corrections, supporting characters, relationships, facts and world rules | Local guide explains actionable entries and shared placeholder scope; future optional quality checks should be advisory |
| Importing Lorebooks | JSON and structured plain text, conversion limits | Existing import/export; Android/official-editor acceptance still unverified |
| Creator Guide introduction, quickstart, greeting/voice | Identity, avatar, tags, tagline, description, greetings, definition, voice, visibility | Future native character/persona draft editor; keep official publication and voice selection separate |
| Character Definition | Definition length specification 32,000 characters; front-load identity/behavior, examples and placeholders | Future field-level validation, examples and character-card conversion; do not infer all current account limits |
| Templates and Examples | Plan characters before entering fields | Future native planning templates and preview |
| Scene Creation Quickstart, Creator Insights | Official scene creation and creator account tools | Future safe official navigation, no unsupported API calls or inferred private analytics |

Primary sources:
- https://support.character.ai/hc/en-us/categories/15097346523803-Guides
- https://support.character.ai/hc/en-us/articles/52739596326811-Lorebooks
- https://support.character.ai/hc/en-us/articles/52739683169179-What-makes-a-good-Lorebook
- https://support.character.ai/hc/en-us/articles/54347105439515-Importing-Lorebooks
- https://support.character.ai/hc/en-us/articles/50608794517915-1-Welcome-to-the-Creator-Guide
- https://support.character.ai/hc/en-us/articles/50608869548699-2-Creating-a-Character-Quickstart-Guide-%CF%89
- https://support.character.ai/hc/en-us/articles/50609011294235-4-Greeting-and-Voice-%EF%BC%90-%E3%83%8E
- https://support.character.ai/hc/en-us/articles/50609183646875-5-Character-Definition
- https://support.character.ai/hc/en-us/articles/50609592926235-9-Templates-and-Examples
- https://support.character.ai/hc/en-us/articles/41918454359451-Scene-Creation-Quickstart-Guide
- https://support.character.ai/hc/en-us/articles/51616603738395-Creator-Insights

Open-source design references (reviewed public project descriptions, not imported code): Silly-Tavern-Explorer (round-trip preservation and stable local identities), SillyTavern-CharacterLibrary (version snapshots and creator forms), CharacterCardEditor (embedded PNG metadata and advanced editing), ST-cardEditor (offline editor and search), card-forge (PNG and JSON interchange), CharacterVault (standalone lorebook library). They use more expressive schemas than Character.AI, so conversion cannot promise full compatibility.
- https://github.com/proto-deus/Silly-Tavern-Explorer
- https://github.com/Sillyanonymous/SillyTavern-CharacterLibrary
- https://github.com/dwenne/CharacterCardEditor
- https://github.com/maxime-fleury/ST-cardEditor
- https://github.com/numb747/card-forge
- https://github.com/spaceman2408/CharacterVault

Android implementation reference: https://developer.android.com/reference/android/util/AtomicFile and https://developer.android.com/training/data-storage/app-specific . Explicit saves only; no periodic tasks or unattended activity. Next: full Android compile, on-device creator workspace QA, PNG character-card import, native character/persona drafts, data-preserving merging, accessibility, actual performance measurements. No claim that the official app was fully reverse-engineered.

## Native Character Studio (2026-11-24)
New private screen in all session modes. V1/V2/V3 JSON and standard character-card PNG text metadata reuse the existing bounded reader. Five editable target fields (name, tagline, description, greeting, definition), individual clipboard copy, advisory documented field lengths 20/50/500/4096/32000 and name checks. Conversion keeps the source description untruncated in both editable profile and labelled definition sections, followed by personality, scenario and dialogue examples. Alternate greetings can explicitly replace the primary greeting; tags and creator notes/system/post-history instructions are separately reviewable/copyable, not automatically merged into the definition. Unknown fields, assets and embedded lore are not transferred; use Lorebook Studio separately. The source is untouched. Draft is retained during ordinary Activity recreation, not written to disk or automatically published; copy fields before closing. No remote assets or new dependencies. Character.AI does not document a full character-card import endpoint in these guides; this is a preparation workflow, not a direct account import. Sources: https://support.character.ai/hc/en-us/articles/50608939675035-3-Profile-and-Presentation , https://support.character.ai/hc/en-us/articles/50609011294235-4-Greeting-and-Voice , https://support.character.ai/hc/en-us/articles/50609183646875-5-Character-Definition , https://github.com/kwaroran/character-card-spec-v3 , https://github.com/malfoyslastname/character-card-spec-v2 . Reviewed public descriptions of CCEditor, ST-cardEditor, CharacterVault, CharacterCardEditor and SillyTavern-CreativeStudio; no external code copied. JSON adapter and Android UI still need device tests. This is not an exhaustive app audit.
