"""Audit the standalone Whisper JNI library before Android packaging."""
import os
from pathlib import Path
import re
import subprocess
import sys

JNI = 'Java_dev_caicompanion_app_EmbeddedWhisper_nativeTranscribeV2'
SYSTEM_LIBS = frozenset(('libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libandroid.so'))


def validate(exports, dynamic):
    rows = [line.split() for line in exports.splitlines() if line.strip()]
    if len(rows) != 1 or len(rows[0]) < 2 or rows[0][:2] != [JNI, 'T']:
        raise ValueError('Whisper JNI export set is not isolated')
    needed = []
    sonames = []
    nulls = 0
    for line in dynamic.splitlines():
        tag = re.match(r'^\s*0x[0-9a-fA-F]+\s+\(([^)]+)\)\s*(.*)$', line)
        if not tag:
            continue
        name, value = tag.groups()
        if name in ('RPATH', 'RUNPATH', 'TEXTREL') or (name == 'FLAGS' and 'TEXTREL' in value.split()):
            raise ValueError('Forbidden Whisper dynamic loader setting')
        if name == 'NULL':
            nulls += 1
        if name in ('NEEDED', 'SONAME'):
            match = re.fullmatch(r'[^\[\]]*\[([^\[\]]+)\]\s*', value)
            if not match:
                raise ValueError('Malformed Whisper dynamic table')
            (needed if name == 'NEEDED' else sonames).append(match.group(1))
    if nulls != 1 or sonames != ['libmandy_whisper.so']:
        raise ValueError('Missing or unexpected Whisper dynamic identity')
    if len(set(needed)) != len(needed) or not set(needed) <= SYSTEM_LIBS:
        raise ValueError('Unexpected Whisper shared dependency')
    return tuple(sorted(needed))


def ndk_tools(sdk):
    host = {'win32': 'windows-x86_64', 'linux': 'linux-x86_64', 'darwin': 'darwin-x86_64'}.get(sys.platform)
    if host is None:
        raise ValueError('Unsupported NDK host')
    suffix = '.exe' if sys.platform == 'win32' else ''
    directory = Path(sdk) / 'ndk/29.0.13113456/toolchains/llvm/prebuilt' / host / 'bin'
    tools = tuple(directory / (name + suffix) for name in ('llvm-nm', 'llvm-readelf'))
    if not all(path.is_file() for path in tools):
        raise ValueError('Pinned NDK symbol audit tools missing')
    return tools


def audit_linkage(binary, nm, readelf):
    def run(args):
        return subprocess.check_output([str(x) for x in args], text=True, encoding='utf-8',
                                       stderr=subprocess.PIPE, env=dict(os.environ, LC_ALL='C'))
    exports = run([nm, '--dynamic', '--defined-only', '--format=posix', binary])
    dynamic = run([readelf, '--dynamic', '--wide', binary])
    return validate(exports, dynamic)
