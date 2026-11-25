#!/usr/bin/env python3
"""Read-only ELF64 PT_LOAD audit for Character Companion APKs."""
import argparse
import stat
import struct
import zipfile

PAGE = 16384
MACHINES = {'arm64-v8a': 183, 'x86_64': 62}
MAX_ADDRESS = 1 << 64


def check_elf(stream, size, machine):
    header = stream.read(64)
    if len(header) != 64 or header[:7] != b'\x7fELF\x02\x01\x01':
        return 'invalid little-endian ELF64 header'
    fields = struct.unpack('<16sHHIQQQIHHHHHH', header)
    _, kind, arch, version, _, phoff, _, _, ehsize, phsize, count, _, _, _ = fields
    if (kind != 3 or arch != machine or version != 1 or ehsize != 64 or
            phsize != 56 or not 1 <= count <= 1024 or phoff < 64 or
            phoff + phsize * count > min(size, 1048576)):
        return 'invalid ELF type, machine or program header table'
    stream.seek(phoff)
    loads = 0
    file_backed = 0
    for _ in range(count):
        row = stream.read(56)
        if len(row) != 56:
            return 'truncated program header'
        kind, _, offset, address, _, filesz, memsz, align = struct.unpack('<IIQQQQQQ', row)
        if kind != 1:
            continue
        loads += 1
        if filesz > memsz or offset + filesz > size or address + memsz > MAX_ADDRESS:
            return 'invalid PT_LOAD bounds'
        if align < PAGE or align & (align - 1) or offset % align != address % align:
            return 'PT_LOAD is not 16 KiB compatible'
        if filesz:
            file_backed += 1
    if not loads:
        return 'no PT_LOAD segments'
    return None if file_backed else 'no file-backed PT_LOAD segments'


def audit(path, flavor):
    if flavor not in ('gecko', 'legacy', 'mandy'):
        raise ValueError('Unsupported APK flavor')
    errors = []
    count = 0
    seen = set()
    with zipfile.ZipFile(path) as archive:
        for entry in archive.infolist():
            name = entry.filename
            if not name.startswith('lib/') or not name.endswith('.so'):
                continue
            parts = name.split('/')
            if len(parts) != 3 or parts[1] not in MACHINES or not parts[2]:
                errors.append('unsupported native library path: ' + repr(name))
                continue
            if name in seen:
                errors.append('duplicate native library: ' + repr(name))
                continue
            seen.add(name)
            count += 1
            if flavor in ('gecko', 'mandy') and parts[1] != 'arm64-v8a':
                errors.append('unexpected ' + ('Gecko' if flavor == 'gecko' else 'Mandy') + ' ABI: ' + repr(name))
                continue
            if flavor == 'mandy' and parts[2].lower() in ('libxul.so', 'libmozglue.so'):
                errors.append('unexpected Gecko library in native Mandy APK: ' + repr(name))
                continue
            if entry.create_system == 3 and stat.S_ISLNK(entry.external_attr >> 16):
                errors.append('native library is a symbolic link: ' + repr(name))
                continue
            with archive.open(entry) as stream:
                issue = check_elf(stream, entry.file_size, MACHINES[parts[1]])
            if issue:
                errors.append(repr(name) + ': ' + issue)
    if flavor == 'gecko' and count == 0:
        errors.append('Gecko APK contains no supported native libraries')
    if flavor == 'mandy' and 'lib/arm64-v8a/libmandy_llama.so' not in seen:
        errors.append('Mandy APK is missing its embedded inference library')
    return count, errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk')
    parser.add_argument('flavor', choices=('gecko', 'legacy', 'mandy'))
    args = parser.parse_args()
    try:
        count, errors = audit(args.apk, args.flavor)
    except (OSError, ValueError, RuntimeError, zipfile.BadZipFile) as exc:
        print('ELF_AUDIT=FAILED: ' + type(exc).__name__)
        return 1
    for issue in errors:
        print(issue)
    print('NATIVE_LIBRARIES=' + str(count))
    print('ELF_AUDIT=' + ('FAILED' if errors else 'PASSED' if count else 'NO_NATIVE_LIBRARIES'))
    return int(bool(errors))


if __name__ == '__main__':
    raise SystemExit(main())
