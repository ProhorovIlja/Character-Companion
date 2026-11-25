#!/usr/bin/env python3
"""Explicit local JVM regression checks; no native SDK, network, or scheduled work."""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'dev.caicompanion.app'
SOURCES = (
    'NativeConversation', 'NativeChatWorkspace', 'NativeModelLibrary',
    'EmbeddedTextSink', 'SpokenText',
)
SUITES = (
    'CommittedReplyRecoverySmoke', 'DraftWriteSmoke', 'KnownDigestImportSmoke',
    'RecoverWithoutModelSmoke', 'StreamingBufferSmoke', 'SpokenTextSmoke',
    'ConversationFidelitySmoke',
)


def main():
    java, kotlin = shutil.which('java'), shutil.which('kotlinc')
    if not java or not kotlin:
        print('CORE_TEST_FAILED: Java and kotlinc required', file=sys.stderr)
        return 1
    source_dir = ROOT / 'app/src/main/java/dev/caicompanion/app'
    test_dir = ROOT / 'tests/native'
    files = [source_dir / (name + '.kt') for name in SOURCES]
    files += [test_dir / (name + '.kt') for name in SUITES]
    try:
        for path in files:
            if not path.is_file():
                raise ValueError('Missing source: ' + str(path.relative_to(ROOT)))
        with tempfile.TemporaryDirectory(prefix='mandy-core-') as temp:
            jar = Path(temp) / 'core.jar'
            subprocess.run([kotlin, *map(str, files), '-include-runtime', '-d', str(jar)], cwd=ROOT, check=True)
            for name in SUITES:
                subprocess.run([java, '-cp', str(jar), PACKAGE + '.' + name + 'Kt'], cwd=ROOT, check=True)
                print('CORE_SUITE_PASSED=' + name, flush=True)
        print('CORE_SUITES_PASSED=' + str(len(SUITES)))
        print('ANDROID_BUILD_TESTED=NO WINDOWS_BUILD_TESTED=NO REAL_MODEL_TESTED=NO')
        return 0
    except (OSError, ValueError, subprocess.CalledProcessError) as exc:
        print('CORE_TEST_FAILED: ' + str(exc), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
