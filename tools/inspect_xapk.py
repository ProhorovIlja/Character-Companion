#!/usr/bin/env python3
"""Offline, read-only XAPK/APK inventory. No code execution, installation or decompilation.

Untrusted APKs are ZIP files. Never extract filenames or execute their contents.
'--parts' reads an original XAPK broken into ordered raw byte chunks, without
requiring conversion into a single APK. No source assets are included in reports.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import struct
import sys
import tempfile
import zipfile

MAX_ENTRIES = 50_000
MAX_EXPANDED = 4 * 1024**3
MAX_INDIVIDUAL = 1024**3
MAX_RATIO = 500
MAX_JSON = 2 * 1024**2
MAX_AXML = 8 * 1024**2


class UnsafeArchive(ValueError):
    pass


def sha256_file(filename: Path) -> str:
    digest = hashlib.sha256()
    with filename.open('rb') as src:
        for chunk in iter(lambda: src.read(4 * 1024**2), b''):
            digest.update(chunk)
    return digest.hexdigest()


def validate_info(info: zipfile.ZipInfo) -> str | None:
    """Return a warning; do not trust archive entry paths or size declarations."""
    name = info.filename.replace('\\', '/')
    parts = PurePosixPath(name).parts
    if (not name or name.startswith('/') or '\x00' in name or
            '..' in parts or any(':' in p for p in parts) or
            (info.create_system == 3 and ((info.external_attr >> 16) & 0o170000) == 0o120000)):
        return 'unsafe_path_or_symlink'
    if info.file_size < 0 or info.file_size > MAX_INDIVIDUAL:
        return 'oversized_entry'
    if info.file_size > 1024**2 and info.file_size / max(1, info.compress_size) > MAX_RATIO:
        return 'suspicious_compression_ratio'
    if info.flag_bits & 1:
        return 'encrypted_entry'
    return None


def inventory_zip(zf: zipfile.ZipFile) -> tuple[list[zipfile.ZipInfo], dict]:
    infos = zf.infolist()
    if len(infos) > MAX_ENTRIES:
        raise UnsafeArchive('too_many_zip_entries')
    total = sum(i.file_size for i in infos)
    if total > MAX_EXPANDED:
        raise UnsafeArchive('oversized_expanded_archive')
    safe = []
    warnings = []
    for info in infos:
        failure = validate_info(info)
        if failure:
            warnings.append({'entry': info.filename[:160], 'reason': failure})
        else:
            safe.append(info)
    return safe, {'entries': len(infos), 'uncompressed_bytes': total,
                  'unsafe_entries': warnings[:40], 'unsafe_entry_count': len(warnings)}


def _string_length8(data: bytes, at: int) -> tuple[int, int]:
    if at >= len(data):
        raise ValueError('truncated utf8 length')
    first = data[at]
    if first & 0x80:
        if at + 1 >= len(data):
            raise ValueError('truncated utf8 length')
        return ((first & 0x7f) << 8) | data[at+1], at + 2
    return first, at + 1


def _string_length16(data: bytes, at: int) -> tuple[int, int]:
    if at + 2 > len(data):
        raise ValueError('truncated utf16 length')
    first = struct.unpack_from('<H', data, at)[0]
    if first & 0x8000:
        if at + 4 > len(data):
            raise ValueError('truncated utf16 length')
        return ((first & 0x7fff) << 16) | struct.unpack_from('<H', data, at + 2)[0], at + 4
    return first, at + 2


def _read_strings(data: bytes, start: int, end: int) -> list[str]:
    if start + 28 > end:
        raise ValueError('truncated string pool')
    count, _, flags, string_start, _ = struct.unpack_from('<IIIII', data, start + 8)
    if count > 100_000 or 28 + 4 * count > end-start:
        raise ValueError('oversized string pool')
    utf8 = bool(flags & 0x100)
    strings = []
    for i in range(count):
        offset = struct.unpack_from('<I', data, start + 28 + 4*i)[0]
        p = start + string_start + offset
        if p >= end:
            raise ValueError('bad string offset')
        if utf8:
            _, p = _string_length8(data, p)
            byte_length, p = _string_length8(data, p)
            if p + byte_length + 1 > end or byte_length > MAX_AXML:
                raise ValueError('truncated string')
            string = data[p:p+byte_length].decode('utf8', errors='replace')
        else:
            char_length, p = _string_length16(data, p)
            byte_length = char_length * 2
            if p + byte_length + 2 > end or byte_length > MAX_AXML:
                raise ValueError('truncated string')
            string = data[p:p+byte_length].decode('utf-16le', errors='replace')
        strings.append(string)
    return strings


def parse_axml_manifest(data: bytes) -> dict:
    """Parse a small, strict subset of Android binary XML without trusting DEX code.

    Supports resource string pool and START_ELEMENT attributes only. Unknown
    chunks are ignored. A broken manifest returns an explicit error to caller.
    """
    if len(data) < 8 or struct.unpack_from('<H', data)[0] != 0x0003:
        raise ValueError('not_binary_android_xml')
    xml_size = struct.unpack_from('<I', data, 4)[0]
    if xml_size > len(data) or xml_size < 8:
        raise ValueError('invalid_binary_xml_size')
    pos = struct.unpack_from('<H', data, 2)[0]
    strings: list[str] = []
    result = {'package_name': None, 'version_name': None, 'version_code': None,
              'min_sdk': None, 'target_sdk': None, 'permissions': []}
    while pos + 8 <= xml_size:
        kind, header_size, chunk_size = struct.unpack_from('<HHI', data, pos)
        end = pos + chunk_size
        if chunk_size < 8 or header_size < 8 or header_size > chunk_size or end > xml_size:
            raise ValueError('invalid_chunk_size')
        if kind == 0x0001:
            strings = _read_strings(data, pos, end)
        elif kind == 0x0102 and strings:
            if chunk_size < 36:
                raise ValueError('truncated_start_element')
            name_idx = struct.unpack_from('<I', data, pos + 20)[0]
            name = strings[name_idx] if name_idx < len(strings) else ''
            attr_start, attr_size, attr_count = struct.unpack_from('<HHH', data, pos + 24)
            attr_pos = pos + 16 + attr_start
            if attr_count > 500 or attr_size < 20 or attr_pos + attr_count * attr_size > end:
                raise ValueError('invalid_attributes')
            attrs = {}
            for i in range(attr_count):
                a = attr_pos + i * attr_size
                _, key_idx, raw_idx, _, _, val_type, val_data = struct.unpack_from('<IIIHBBI', data, a)
                key = strings[key_idx] if key_idx < len(strings) else None
                if not key:
                    continue
                if raw_idx < len(strings):
                    value = strings[raw_idx]
                elif val_type == 0x03 and val_data < len(strings):
                    value = strings[val_data]
                elif val_type in (0x10, 0x11):
                    value = str(val_data)
                else:
                    continue
                attrs[key] = value
            if name == 'manifest':
                result['package_name'] = attrs.get('package')
                result['version_name'] = attrs.get('versionName')
                result['version_code'] = attrs.get('versionCode')
            elif name == 'uses-sdk':
                result['min_sdk'] = attrs.get('minSdkVersion')
                result['target_sdk'] = attrs.get('targetSdkVersion')
            elif name in ('uses-permission','uses-permission-sdk-23','uses-permission-sdk-m'):
                p = attrs.get('name')
                if p and p not in result['permissions']:
                    result['permissions'].append(p)
        pos = end
    return result


def inspect_apk(info: zipfile.ZipInfo, outer: zipfile.ZipFile) -> dict:
    """Copy nested APK to a temporary file; process ZIP directory without unpacking."""
    digest = hashlib.sha256()
    with tempfile.SpooledTemporaryFile(max_size=8 * 1024**2) as temp:
        with outer.open(info) as src:
            copied = 0
            for piece in iter(lambda: src.read(1024**2), b''):
                copied += len(piece)
                if copied > MAX_INDIVIDUAL:
                    raise UnsafeArchive('nested_apk_exceeds_limit')
                digest.update(piece)
                temp.write(piece)
        temp.seek(0)
        with zipfile.ZipFile(temp) as apk:
            entries, summary = inventory_zip(apk)
            filenames = [x.filename for x in entries]
            dex = sorted(x for x in filenames if re.fullmatch(r'classes\d*\.dex', x))
            native = sorted({p[1] for x in filenames if (p:=x.split('/')) and len(p) == 3
                             and p[0] == 'lib' and p[2].endswith('.so')})
            result = {'module': Path(info.filename).name, 'sha256': digest.hexdigest(),
                      'bytes': copied, 'dex_count': len(dex), 'native_abis': native,
                      'native_library_count': sum(1 for x in filenames if x.startswith('lib/') and x.endswith('.so')),
                      'assets_count': sum(x.startswith('assets/') for x in filenames),
                      'resources_count': sum(x.startswith('res/') for x in filenames),
                      'has_resource_table': 'resources.arsc' in filenames,
                      'zip_summary': summary}
            if 'AndroidManifest.xml' in filenames:
                manifest_info = apk.getinfo('AndroidManifest.xml')
                if manifest_info.file_size <= MAX_AXML:
                    try:
                        result['android_manifest'] = parse_axml_manifest(apk.read(manifest_info))
                    except (ValueError, IndexError, struct.error) as exc:
                        result['manifest_parse_error'] = str(exc)[:100]
                else:
                    result['manifest_parse_error'] = 'manifest_too_large'
            return result


def inspect_xapk(path: Path) -> dict:
    report = {'schema': 1, 'source_name': path.name, 'size_bytes': path.stat().st_size,
              'sha256': sha256_file(path), 'manifest': {}, 'apks': [], 'other_payload': []}
    with zipfile.ZipFile(path) as zf:
        good, summary = inventory_zip(zf)
        report['zip_summary'] = summary
        for entry in good:
            name = entry.filename
            if name == 'manifest.json' and entry.file_size <= MAX_JSON:
                try:
                    manifest = json.loads(zf.read(entry))
                    if isinstance(manifest, dict):
                        allow = ('name','package_name','version_name','version_code','min_sdk_version',
                                 'target_sdk_version','xapk_version')
                        report['manifest'] = {k: manifest[k] for k in allow if k in manifest and
                                              isinstance(manifest[k], (str, int, float))}
                except (ValueError, UnicodeError, RuntimeError) as exc:
                    report['manifest_error'] = str(exc)[:100]
            elif name.lower().endswith('.apk'):
                report['apks'].append(inspect_apk(entry, zf))
            elif not entry.is_dir():
                report['other_payload'].append({'name': name[:180], 'bytes': entry.file_size})
    return report


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description='Read-only metadata inventory of XAPK/APK modules.')
    parser.add_argument('archive', nargs='?', type=Path, help='Complete original XAPK')
    parser.add_argument('--parts', nargs='+', type=Path, metavar='PART',
                        help='Ordered raw byte chunks of the SAME original XAPK (not separate ZIPs)')
    parser.add_argument('--json', type=Path, metavar='OUTPUT', help='Write only metadata and hashes to JSON')
    parser.add_argument('--expect-sha256', help='Reject if reassembled XAPK does not match original SHA-256')
    options = parser.parse_args(argv)
    if bool(options.archive) == bool(options.parts):
        parser.error('provide a single XAPK or --parts ordered byte chunks')
    tmp = None
    try:
        if options.parts:
            fd, name = tempfile.mkstemp(suffix='.xapk')
            os.close(fd)
            tmp = Path(name)
            with tmp.open('wb') as out:
                for part in options.parts:
                    with part.open('rb') as src:
                        shutil.copyfileobj(src, out, 4 * 1024**2)
            target = tmp
        else:
            target = options.archive
        result = inspect_xapk(target)
        if options.expect_sha256 and result['sha256'].lower() != options.expect_sha256.strip().lower():
            raise UnsafeArchive('source_sha256_mismatch')
        if options.parts:
            result['source_name'] = 'reassembled_xapk'
            result['part_count'] = len(options.parts)
        rendered = json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True)
        if options.json:
            options.json.write_text(rendered + '\n', encoding='utf-8')
        else:
            print(rendered)
        return 0
    except (OSError, zipfile.BadZipFile, UnsafeArchive, ValueError, RuntimeError) as exc:
        print(f'XAPK_AUDIT_FAILED: {str(exc)[:240]}', file=sys.stderr)
        return 2
    finally:
        if tmp is not None:
            tmp.unlink(missing_ok=True)


if __name__ == '__main__':
    raise SystemExit(main())
