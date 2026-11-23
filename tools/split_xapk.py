#!/usr/bin/env python3
"""Explicit, local byte splitting for Drive's 256-MiB connector transfer limit.

Does not change the original XAPK, invoke a remote API or automatically upload.
Default part size (190 MiB) is safely beneath the Drive transfer limit.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys

DEFAULT_BYTES = 190 * 1024**2


def split_file(source: Path, destination: Path, part_bytes: int = DEFAULT_BYTES) -> dict:
    if part_bytes < 1024 or part_bytes > 220 * 1024**2:
        raise ValueError('part size must be 1 KiB to 220 MiB')
    if not source.is_file() or source.stat().st_size == 0:
        raise ValueError('source file missing or empty')
    destination.mkdir(parents=True, exist_ok=True)
    size = source.stat().st_size
    count = (size + part_bytes - 1) // part_bytes
    part_paths = [destination / f'{source.name}.part{i+1:03d}' for i in range(count)]
    manifest_path = destination / f'{source.name}.parts-manifest.json'
    if any(p.exists() for p in part_paths + [manifest_path]):
        raise FileExistsError('split output already exists; refusing to overwrite')
    original_digest = hashlib.sha256()
    manifest = {'format': 'ordered-raw-byte-chunks-v1', 'source_name': source.name,
                'source_bytes': size, 'part_bytes_limit': part_bytes, 'parts': []}
    try:
        with source.open('rb') as reader:
            for path in part_paths:
                part_digest = hashlib.sha256()
                remaining = part_bytes
                written = 0
                with path.open('xb') as output:
                    while remaining:
                        data = reader.read(min(4 * 1024**2, remaining))
                        if not data:
                            break
                        output.write(data)
                        original_digest.update(data)
                        part_digest.update(data)
                        remaining -= len(data)
                        written += len(data)
                manifest['parts'].append({'name': path.name, 'bytes': written,
                                          'sha256': part_digest.hexdigest()})
        manifest['source_sha256'] = original_digest.hexdigest()
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        return manifest
    except Exception:
        for part in part_paths:
            part.unlink(missing_ok=True)
        manifest_path.unlink(missing_ok=True)
        raise


def main(argv=None):
    parser=argparse.ArgumentParser(description='Split an original XAPK into ordered raw pieces.')
    parser.add_argument('xapk',type=Path)
    parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--part-mib',type=int,default=190)
    args=parser.parse_args(argv)
    try:
        report=split_file(args.xapk,args.out,args.part_mib*1024**2)
        print(f"PARTS_CREATED={len(report['parts'])}")
        print(f"ORIGINAL_SHA256={report['source_sha256']}")
        for part in report['parts']:
            print(f"PART={part['name']} BYTES={part['bytes']} SHA256={part['sha256']}")
    except (OSError,ValueError) as exc:
        print(f'SPLIT_FAILED: {exc}',file=sys.stderr)
        return 2
    return 0


if __name__=='__main__':raise SystemExit(main())
