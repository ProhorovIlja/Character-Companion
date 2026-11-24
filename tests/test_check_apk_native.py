import io
import stat
import struct
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import check_apk_native as audit


def elf(align=16384, address=0, offset=0, machine=183, filesz=120, memsz=120, kind=1):
    header = struct.pack('<16sHHIQQQIHHHHHH', b'\x7fELF\x02\x01\x01', 3, machine, 1,
                         0, 64, 0, 0, 64, 56, 1, 0, 0, 0)
    return header + struct.pack('<IIQQQQQQ', kind, 5, offset, address, 0, filesz, memsz, align)


class NativeAudit(unittest.TestCase):
    def verify(self, data, machine=183):
        return audit.check_elf(io.BytesIO(data), len(data), machine)

    def test_valid_alignment(self):
        for align in (16384, 65536):
            self.assertIsNone(self.verify(elf(align=align)))

    def test_bad_alignment(self):
        for align in (0, 4096, 24576):
            self.assertIsNotNone(self.verify(elf(align=align)))
        self.assertIsNotNone(self.verify(elf(address=4096)))

    def test_bad_headers(self):
        for data in (b'', elf()[:80], elf(machine=62), elf(kind=2), elf(filesz=121), elf(memsz=100)):
            self.assertIsNotNone(self.verify(data))
        self.assertIsNone(self.verify(elf(machine=62), 62))

    def test_apk_flavors_and_duplicates(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'test.apk'
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('classes.dex', b'test')
            self.assertEqual(audit.audit(path, 'legacy'), (0, []))
            self.assertTrue(audit.audit(path, 'gecko')[1])
            with zipfile.ZipFile(path, 'a') as archive:
                archive.writestr('lib/arm64-v8a/libtest.so', elf())
            self.assertEqual(audit.audit(path, 'gecko'), (1, []))
            with warnings.catch_warnings(), zipfile.ZipFile(path, 'a') as archive:
                warnings.simplefilter('ignore', UserWarning)
                archive.writestr('lib/arm64-v8a/libtest.so', elf())
            self.assertTrue(audit.audit(path, 'gecko')[1])

    def test_wrong_abi(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'test.apk'
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('lib/x86_64/libtest.so', elf(machine=62))
            self.assertTrue(audit.audit(path, 'gecko')[1])
            self.assertEqual(audit.audit(path, 'legacy'), (1, []))

    def test_virtual_address_overflow(self):
        address = (1 << 64) - 16384
        self.assertIsNone(self.verify(elf(address=address, memsz=16384)))
        self.assertEqual(self.verify(elf(address=address, memsz=16385)), 'invalid PT_LOAD bounds')

    def test_empty_or_bss_only_load(self):
        for memsz in (0, 16384):
            self.assertEqual(self.verify(elf(filesz=0, memsz=memsz)), 'no file-backed PT_LOAD segments')

    def test_native_symlink_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'test.apk'
            entry = zipfile.ZipInfo('lib/arm64-v8a/libtest.so')
            entry.create_system = 3
            entry.external_attr = (stat.S_IFLNK | 0o777) << 16
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr(entry, elf())
            count, errors = audit.audit(path, 'gecko')
            self.assertEqual(count, 1)
            self.assertIn('symbolic link', errors[0])


if __name__ == '__main__':
    unittest.main()
