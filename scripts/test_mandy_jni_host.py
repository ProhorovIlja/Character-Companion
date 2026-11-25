#!/usr/bin/env python3
"""Explicit Linux host JNI smoke with a synthetic backend, never an APK build."""
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    if not sys.platform.startswith('linux'):
        print('JNI_HOST_FAILED: Linux host required', file=sys.stderr)
        return 1
    tools = {name: shutil.which(name) for name in ('java', 'javac', 'kotlinc', 'clang++', 'nm')}
    if not all(tools.values()):
        print('JNI_HOST_FAILED: java, javac, kotlinc, clang++ and nm required', file=sys.stderr)
        return 1
    jdk = Path(tools['javac']).resolve().parent.parent
    native = ROOT / 'app/src/main/cpp/mandy/mandy_llama.cpp'
    exports = native.with_name('exports.map')
    kotlin = ROOT / 'app/src/main/java/dev/caicompanion/app'
    tests = ROOT / 'tests/native'
    sources = [kotlin / (name + '.kt') for name in ('EmbeddedLlama', 'EmbeddedTextSink', 'NativeConversation', 'NativeModelLibrary', 'ResidentModel', 'ModelAdmission')]
    sources.extend([tests / 'JniBoundarySmoke.kt', tests / 'ResidentModelSmoke.kt'])
    try:
        for source in [native, exports, *sources, tests / 'jni-stub/llama.h', jdk / 'include/jni.h', jdk / 'include/linux/jni_md.h']:
            if not source.is_file():
                raise ValueError('Missing input: ' + str(source))
        with tempfile.TemporaryDirectory(prefix='mandy-jni-host-') as temporary:
            stage = Path(temporary)
            library = stage / 'libmandy_llama.so'
            jar = stage / 'smoke.jar'
            subprocess.run([tools['clang++'], '-std=c++17', '-shared', '-fPIC', '-Wall', '-Wextra',
                            '-I' + str(jdk / 'include'), '-I' + str(jdk / 'include/linux'),
                            '-I' + str(tests / 'jni-stub'), str(native), '-Wl,--exclude-libs,ALL',
                            '-Wl,--version-script=' + str(exports), '-o', str(library)], cwd=ROOT, check=True)
            symbols = subprocess.check_output([tools['nm'], '--dynamic', '--defined-only', '--format=posix', str(library)], text=True)
            expected = {'Java_dev_caicompanion_app_EmbeddedLlama_' + name
                        for name in ('nativeLoad', 'nativeClose', 'nativeGenerateV3')}
            rows = [line.split() for line in symbols.splitlines() if line.strip()]
            if len(rows) != len(expected) or {row[0] for row in rows if len(row) >= 2 and row[1] == 'T'} != expected:
                raise ValueError('Unexpected llama JNI export set')
            print('HOST_JNI_EXPORTS=PASSED', flush=True)
            subprocess.run([tools['kotlinc'], *map(str, sources), '-include-runtime', '-d', str(jar)], cwd=ROOT, check=True)
            subprocess.run([tools['java'], '-Xcheck:jni', '-Djava.library.path=' + str(stage), '-jar', str(jar)], cwd=ROOT, check=True)
        print('HOST_JNI_TEST=PASSED SIMULATED_BACKEND=YES ANDROID_BUILD_TESTED=NO REAL_MODEL_TESTED=NO')
        return 0
    except (OSError, ValueError, subprocess.CalledProcessError) as exc:
        print('JNI_HOST_FAILED: ' + str(exc), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
