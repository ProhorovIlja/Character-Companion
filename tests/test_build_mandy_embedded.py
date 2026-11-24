import struct
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import warnings
import zipfile

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import build_mandy_embedded as build


def elf(align=16384, machine=183):
    return struct.pack('<16sHHIQQQIHHHHHH', b'\x7fELF\x02\x01\x01', 3, machine, 1,
                       0, 64, 0, 0, 64, 56, 1, 0, 0, 0) + struct.pack('<IIQQQQQQ', 1, 5, 0, 0, 0, 120, 120, align)


class BuildGate(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.apk = self.root / 'fixture.apk'

    def package(self, libs, manifest=True):
        with warnings.catch_warnings(), zipfile.ZipFile(self.apk, 'w') as archive:
            warnings.simplefilter('ignore', UserWarning)
            if manifest:
                archive.writestr('AndroidManifest.xml', b'fixture')
            archive.writestr('classes.dex', b'fixture')
            for name, data in libs:
                archive.writestr(name, data)

    def test_valid_native_layout(self):
        self.package([(build.LIB, elf())])
        build.check_package(self.apk)

    def test_missing_native_engine(self):
        self.package([('lib/arm64-v8a/libother.so', elf())])
        with self.assertRaises(ValueError):
            build.check_package(self.apk)

    def test_alignment(self):
        self.package([(build.LIB, elf(4096))])
        with self.assertRaises(ValueError):
            build.check_package(self.apk)

    def test_duplicate(self):
        self.package([(build.LIB, elf()), (build.LIB, elf())])
        with self.assertRaises(ValueError):
            build.check_package(self.apk)

    def test_wrong_abi(self):
        self.package([(build.LIB, elf()), ('lib/x86_64/libother.so', elf(machine=62))])
        with self.assertRaises(ValueError):
            build.check_package(self.apk)

    def test_missing_manifest(self):
        self.package([(build.LIB, elf())], manifest=False)
        with self.assertRaises(ValueError):
            build.check_package(self.apk)

    def test_checkout(self):
        (self.root / 'include').mkdir()
        (self.root / 'include/llama.h').touch()
        with patch.object(build, 'output', side_effect=[build.PIN, '']):
            build.check_checkout(self.root)
        for replies in (['wrong'], [build.PIN, ' M file']):
            with patch.object(build, 'output', side_effect=replies), self.assertRaises(ValueError):
                build.check_checkout(self.root)

    def test_speech_checkout(self):
        (self.root / 'include').mkdir()
        (self.root / 'include/whisper.h').touch()
        with patch.object(build, 'output', side_effect=[build.WHISPER_PIN, '']):
            build.check_whisper_checkout(self.root)
        for replies in (['wrong'], [build.WHISPER_PIN, '?? file']):
            with patch.object(build, 'output', side_effect=replies), self.assertRaises(ValueError):
                build.check_whisper_checkout(self.root)

    def test_speech_staging(self):
        source = self.root / 'native.so'
        source.write_bytes(elf())
        stage = self.root / 'stage'
        sha = build.stage_whisper(source, stage)
        self.assertEqual(sha, build.digest(source))
        self.assertEqual((stage / 'arm64-v8a/libmandy_whisper.so').read_bytes(), source.read_bytes())

    def test_speech_bad_elf(self):
        source = self.root / 'native.so'
        for data in (b'', elf(4096), elf(machine=62), b'bad'):
            source.write_bytes(data)
            with self.assertRaises(ValueError):
                build.stage_whisper(source, self.root / 'stage')

    def test_speech_package(self):
        source = self.root / 'native.so'
        source.write_bytes(elf())
        self.package([(build.LIB, elf()), (build.WHISPER_LIB, elf())])
        build.check_package(self.apk, build.digest(source))
        for sha in (None, '0' * 64):
            with self.assertRaises(ValueError):
                build.check_package(self.apk, sha)

    def test_speech_missing_duplicate(self):
        for libs in ([(build.LIB, elf())], [(build.LIB, elf()), (build.WHISPER_LIB, elf()), (build.WHISPER_LIB, elf())]):
            self.package(libs)
            with self.assertRaises(ValueError):
                build.check_package(self.apk, '0' * 64)

    def test_speech_build_invocation(self):
        sdk = self.root / 'sdk'
        cmake = sdk / 'cmake/3.31.6/bin'
        cmake.mkdir(parents=True)
        for name in ('cmake', 'ninja'):
            (cmake / name).touch()
        toolchain = sdk / 'ndk/29.0.13113456/build/cmake/android.toolchain.cmake'
        toolchain.parent.mkdir(parents=True)
        toolchain.touch()
        scratch = self.root / 'scratch'
        out = scratch / 'whisper-output'
        out.mkdir(parents=True)
        (out / 'libmandy_whisper.so').write_bytes(elf())
        with patch.object(build.subprocess, 'run') as runner:
            stage, sha = build.build_whisper(self.root, sdk, self.root / 'checkout', scratch, '')
        self.assertEqual(runner.call_count, 2)
        self.assertIn('-DANDROID_STL=c++_static', runner.call_args_list[0].args[0])
        self.assertIn('-DCMAKE_BUILD_TYPE=Release', runner.call_args_list[0].args[0])
        self.assertEqual(runner.call_args_list[1].args[0][-2:], ['--target', 'mandy_whisper'])
        self.assertEqual(sha, build.digest(stage / 'arm64-v8a/libmandy_whisper.so'))

    def test_missing_checkout(self):
        with self.assertRaises(ValueError):
            build.check_checkout(self.root)


if __name__ == '__main__':
    unittest.main()
