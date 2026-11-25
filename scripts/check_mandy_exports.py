"""Check the text JNI ABI in the actual APK, using the pinned NDK nm."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

LIB = 'lib/arm64-v8a/libmandy_llama.so'
MAX_LIB_BYTES = 256 * 1024 * 1024
EXPECTED = frozenset('Java_dev_caicompanion_app_EmbeddedLlama_' + name for name in
                     ('nativeLoadV2', 'nativeClose', 'nativeGenerateV3'))


def validate_exports(output):
    rows = [line.split() for line in output.splitlines() if line.strip()]
    if (len(rows) != len(EXPECTED) or
            {row[0] for row in rows if len(row) >= 2 and row[1] == 'T'} != EXPECTED):
        raise ValueError('Packaged text JNI export set does not match Mandy ABI')


def audit_apk_exports(apk, nm):
    with zipfile.ZipFile(apk) as archive:
        entries = [entry for entry in archive.infolist() if entry.filename == LIB]
        if len(entries) != 1 or not 0 < entries[0].file_size <= MAX_LIB_BYTES:
            raise ValueError('Missing, duplicate, empty or oversized text JNI library')
        with tempfile.TemporaryDirectory(prefix='mandy-exports-') as tmp:
            library = Path(tmp) / 'libmandy_llama.so'
            with archive.open(entries[0]) as source, library.open('wb') as target:
                shutil.copyfileobj(source, target, length=128 * 1024)
            output = subprocess.check_output(
                [str(nm), '--dynamic', '--defined-only', '--format=posix', str(library)],
                text=True, encoding='utf-8', stderr=subprocess.PIPE,
                env=dict(os.environ, LC_ALL='C'))
            validate_exports(output)
