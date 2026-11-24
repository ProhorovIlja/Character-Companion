#!/usr/bin/env python3
"""Explicit foreground Android ARM64 build; no installation, upload or scheduled work."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

from check_apk_native import audit

PIN = 'dbaf582af41941ebbfc4384fb9fa1b40dff7a510'
WRAPPER_SHA = '7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d'
DIST_SHA = '84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae'
LIB = 'lib/arm64-v8a/libmandy_llama.so'


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def output(args, cwd=None):
    return subprocess.check_output([str(x) for x in args], cwd=cwd, text=True, stderr=subprocess.STDOUT).strip()


def require(ok, message):
    if not ok:
        raise ValueError(message)


def check_checkout(path):
    require(path.is_dir() and (path / 'include/llama.h').is_file(), 'Missing llama.cpp source checkout')
    require(output(['git', '-C', path, 'rev-parse', 'HEAD']) == PIN, 'Unexpected llama.cpp revision')
    require(not output(['git', '-C', path, 'status', '--porcelain', '--untracked-files=all']), 'llama.cpp checkout is dirty')


def check_package(apk):
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        for name in ('AndroidManifest.xml', 'classes.dex', LIB):
            require(names.count(name) == 1 and archive.getinfo(name).file_size > 0, 'Missing, empty or duplicate APK entry: ' + name)
        require(all(n.startswith('lib/arm64-v8a/') for n in names if n.startswith('lib/') and n.endswith('.so')), 'Non-ARM64 native library')
        require(archive.testzip() is None, 'APK ZIP integrity failure')
    _, errors = audit(apk, 'gecko')
    require(not errors, 'ELF audit failed: ' + '; '.join(errors))


def preflight(root, sdk, llama):
    check_checkout(llama)
    suffix = '.exe' if os.name == 'nt' else ''
    tools = sdk / 'build-tools/36.0.0'
    java_home = os.environ.get('JAVA_HOME')
    java = str(Path(java_home) / 'bin' / ('java' + suffix)) if java_home else shutil.which('java')
    require(java and Path(java).is_file(), 'Java executable missing')
    require((sdk / 'platforms/android-37.2/android.jar').is_file(), 'Android SDK 37.2 missing')
    require((sdk / 'ndk/29.0.13113456/source.properties').is_file(), 'Pinned NDK missing')
    require((sdk / 'cmake/3.31.6/bin' / ('cmake' + suffix)).is_file(), 'Pinned CMake missing')
    for item in ('lib/apksigner.jar', 'aapt' + suffix, 'zipalign' + suffix):
        require((tools / item).is_file(), 'Missing Android build tool: ' + item)
    wrapper = root / 'gradle/wrapper/gradle-wrapper.jar'
    require(wrapper.is_file() and digest(wrapper) == WRAPPER_SHA, 'Gradle wrapper missing or digest mismatch')
    props = (root / 'gradle/wrapper/gradle-wrapper.properties').read_text().splitlines()
    # Reject duplicate keys and alternate distribution parameters.
    for key, expected in [('distributionUrl', r'https\://services.gradle.org/distributions/gradle-9.7.0-bin.zip'), ('distributionSha256Sum', DIST_SHA)]:
        require([line for line in props if line.startswith(key + '=')] == [key + '=' + expected], 'Invalid Gradle pin: ' + key)
    return java, wrapper, tools, suffix


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', required=True, type=Path)
    parser.add_argument('--llama', required=True, type=Path)
    parser.add_argument('--build', action='store_true', help='Actually invoke Gradle; otherwise preflight only')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    try:
        sdk, llama = args.sdk.resolve(), args.llama.resolve()
        java, wrapper, tools, suffix = preflight(root, sdk, llama)
        print('PREFLIGHT=PASSED', flush=True)
        if not args.build:
            return 0
        apk = root / 'app/build/outputs/apk/gecko/debug/app-gecko-debug.apk'
        apk.unlink(missing_ok=True)
        env = dict(os.environ, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk))
        subprocess.run([java, '-classpath', str(wrapper), 'org.gradle.wrapper.GradleWrapperMain', '--no-daemon', ':app:assembleGeckoDebug', '-PcompanionSdkMinor=2', '-PmandyLlamaDir=' + str(llama)], cwd=root, env=env, check=True)
        check_package(apk)
        certs = output([java, '-jar', tools / 'lib/apksigner.jar', 'verify', '--verbose', '--print-certs', apk])
        require('CN=Android Debug' in certs, 'Expected debug signing identity')
        require("package: name='dev.caicompanion.app'" in output([tools / ('aapt' + suffix), 'dump', 'badging', apk]), 'Unexpected application ID')
        subprocess.run([str(tools / ('zipalign' + suffix)), '-c', '-P', '16', '4', str(apk)], check=True)
        print('EMBEDDED_DEBUG_APK=' + str(apk))
        print('APK_SHA256=' + digest(apk))
        print('NATIVE_ELF_AND_ZIP_ALIGNMENT=PASSED\nDEVICE_TESTED=NO\nMODEL_INFERENCE_TESTED=NO')
        return 0
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError, zipfile.BadZipFile) as exc:
        print('MANDY_BUILD_FAILED: ' + str(exc), file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
