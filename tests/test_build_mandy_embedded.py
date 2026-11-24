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

    def test_missing_checkout(self):
        with self.assertRaises(ValueError):
            build.check_checkout(self.root)


if __name__ == '__main__':
    unittest.main()
