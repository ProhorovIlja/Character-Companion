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
    tools = {name: shutil.which(name) for name in ('java', 'javac', 'kotlinc', 'clang++')}
    if not all(tools.values()):
        print('JNI_HOST_FAILED: java, javac, kotlinc and clang++ required', file=sys.stderr)
        return 1
    jdk = Path(tools['javac']).resolve().parent.parent
    native = ROOT / 'app/src/main/cpp/mandy/mandy_llama.cpp'
    kotlin = ROOT / 'app/src/main/java/dev/caicompanion/app'
    tests = ROOT / 'tests/native'
    sources = [kotlin / (name + '.kt') for name in ('EmbeddedLlama', 'EmbeddedTextSink', 'NativeConversation', 'NativeModelLibrary', 'ResidentModel', 'ModelAdmission')]
    sources.extend([tests / 'JniBoundarySmoke.kt', tests / 'ResidentModelSmoke.kt'])
    try:
        for source in [native, *sources, tests / 'jni-stub/llama.h', jdk / 'include/jni.h', jdk / 'include/linux/jni_md.h']:
            if not source.is_file():
                raise ValueError('Missing input: ' + str(source))
        with tempfile.TemporaryDirectory(prefix='mandy-jni-host-') as temporary:
            stage = Path(temporary)
            library = stage / 'libmandy_llama.so'
            jar = stage / 'smoke.jar'
            subprocess.run([tools['clang++'], '-std=c++17', '-shared', '-fPIC', '-Wall', '-Wextra',
                            '-I' + str(jdk / 'include'), '-I' + str(jdk / 'include/linux'),
                            '-I' + str(tests / 'jni-stub'), str(native), '-o', str(library)], cwd=ROOT, check=True)
            subprocess.run([tools['kotlinc'], *map(str, sources), '-include-runtime', '-d', str(jar)], cwd=ROOT, check=True)
            subprocess.run([tools['java'], '-Xcheck:jni', '-Djava.library.path=' + str(stage), '-jar', str(jar)], cwd=ROOT, check=True)
        print('HOST_JNI_TEST=PASSED SIMULATED_BACKEND=YES ANDROID_BUILD_TESTED=NO REAL_MODEL_TESTED=NO')
        return 0
    except (OSError, ValueError, subprocess.CalledProcessError) as exc:
        print('JNI_HOST_FAILED: ' + str(exc), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
