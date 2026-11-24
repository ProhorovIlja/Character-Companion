# Native character draft library

Explicit Save locally / Save a copy and load/delete actions are available in Character Studio. Up to 16 drafts are stored under app-private no-backup storage, independent of renderer. The CCDR v1 format retains all currently represented fields, alternatives, tags, source notes and conversion-loss flag. Bounded UTF-8 decoding and AtomicFile writes are used. No remote publishing, cloud sync, or background checks. Deletion of a saved entry does not remove the open editor contents.

Research: [Character Foundry](https://github.com/character-foundry/character-foundry), [Character Architect](https://github.com/character-foundry/character-architect), [Android storage](https://developer.android.com/training/data-storage/). Native representation is not a complete CCv3 interchange format. Android compile and device validation remain required.
