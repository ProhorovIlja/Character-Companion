# Official Character.AI XAPK — read-only analysis plan

## Source and transfer status (23 November 2026)

**Research update:** A second XAPK, `character-ai-1-17-4.xapk` (180,553,623 bytes), was subsequently uploaded to the same private Drive folder and successfully downloaded. Its static metadata **has now been inspected**; see [OFFICIAL_APK_AUDIT_1.17.4.md](OFFICIAL_APK_AUDIT_1.17.4.md). The original, different 278,738,234-byte APKPure XAPK remains inaccessible because of the 256-MiB limit. Do not conflate these two archives or claim they have matching hashes.

User supplied `Character+AI_+Chat,+Talk,+Text_1.17.4_APKPure.xapk` in the specified private Google Drive folder (gungrain@gmail.com). Drive metadata reports **278,738,234 bytes** (`application/x-zip`). The connected Drive download explicitly rejects files above **268,435,456 bytes (256 MiB)** with HTTP 413. **This original, larger binary was NOT read or unpacked.** The newer, smaller user-provided XAPK was read and audited separately; its version comes from binary AndroidManifest rather than the filename.

The original source is a third-party APKPure package, and the two archives are not assumed equivalent. The smaller source has a certificate fingerprint consistent with historical APKMirror metadata; formal `apksigner` signature verification and exact Google Play provenance are still unverified. Never execute, install or redistribute unverified app modules automatically. Do not publish copied Character.AI code, binaries, icons, assets or decompiled source to the public project repository.

## Send unmodified archive through the connector limit

The file does not need conversion into a single APK. Split **raw bytes** of the unchanged XAPK into parts below the Drive limit. This works wherever Python 3 is available and only writes local files:

```bash
python3 tools/split_xapk.py "Character+AI_+Chat,+Talk,+Text_1.17.4_APKPure.xapk" --out xapk-parts
```

The default part size is **190 MiB**. Upload the resulting `.part001`, `.part002`, etc. and the `.parts-manifest.json` to the same Drive folder. Part order and the original SHA-256 are recorded in the manifest. No encoding, recompression or transformation of the XAPK occurs. Do not put the original XAPK in public GitHub or Git LFS.

If both parts are available locally, run a read-only inspection (replace the example checksum with `source_sha256` from the manifest):

```bash
python3 tools/inspect_xapk.py --parts \
  xapk-parts/Character+AI_+Chat,+Talk,+Text_1.17.4_APKPure.xapk.part001 \
  xapk-parts/Character+AI_+Chat,+Talk,+Text_1.17.4_APKPure.xapk.part002 \
  --expect-sha256 ORIGINAL_SHA256 --json xapk-audit.json
```

Alternatively inspect a complete XAPK directly with `python3 tools/inspect_xapk.py original.xapk --json xapk-audit.json`.

## What the inspection actually does

- Safely lists ZIP contents, rejects suspicious traversal/symlink/encrypted entries and limits expansion/entry sizes; no archive member is extracted into user directories.
- Inventories nested `.apk` files and counts DEX, assets, resources and native ABI libraries.
- Parses a restricted subset of Android binary Manifest XML: package ID, min/target SDK, version and declared permissions. Report may omit fields encoded using resource IDs or unsupported XML variants.
- Calculates original XAPK and per-module SHA-256 hashes. It **does not validate APK signing certificates**, decompile app logic, decrypt traffic or bypass server-side features.
- Keeps the analysis offline and returns only metadata. It does not upload results automatically.

## Further checks after static inspection

1. Check APK signing certificates using the official Android `apksigner` tool and cross-check signing identity against an authenticated trusted build.
2. Compare package splits, native ABIs, Java/Kotlin/React Native/Flutter or WebView indicators, network and login libraries and permissions.
3. Run JADX/Apktool read-only for additional UI/architecture understanding *without copying copyrighted code/assets* into the public client.
4. Use confirmed functional observations to define compatible first-party UX and performance work. Do not invent private API endpoints or token-extraction flows.
