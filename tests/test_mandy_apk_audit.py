import struct
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import check_apk_native as audit


def elf(machine=183, align=16384):
    header = struct.pack('<16sHHIQQQIHHHHHH', b'\x7fELF\x02\x01\x01', 3, machine, 1,
                         0, 64, 0, 0, 64, 56, 1, 0, 0, 0)
    return header + struct.pack('<IIQQQQQQ', 1, 5, 0, 0, 0, 120, 120, align)


class MandyAudit(unittest.TestCase):
    def check_entries(self, entries, flavor='mandy'):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'test.apk'
            with warnings.catch_warnings(), zipfile.ZipFile(path, 'w') as archive:
                warnings.simplefilter('ignore', UserWarning)
                archive.writestr('classes.dex', b'test')
                for name, data in entries:
                    archive.writestr(name, data)
            return audit.audit(path, flavor)

    def test_required_library(self):
        self.assertIn('missing its embedded inference library', self.check_entries([])[1][0])
        self.assertEqual(self.check_entries([], 'legacy'), (0, []))
        self.assertTrue(self.check_entries([('lib/arm64-v8a/libother.so', elf())])[1])
        self.assertEqual(self.check_entries([('lib/arm64-v8a/libmandy_llama.so', elf())]), (1, []))

    def test_invalid_required_library(self):
        for data in (b'', elf(align=4096), elf(machine=62)):
            with self.subTest(data=data):
                self.assertTrue(self.check_entries([('lib/arm64-v8a/libmandy_llama.so', data)])[1])

    def test_gecko_and_non_arm64_rejected(self):
        for name, data in [('lib/arm64-v8a/libxul.so', elf()),
                           ('lib/arm64-v8a/libmozglue.so', elf()),
                           ('lib/x86_64/libextra.so', elf(machine=62))]:
            with self.subTest(name=name):
                entries = [('lib/arm64-v8a/libmandy_llama.so', elf()), (name, data)]
                self.assertTrue(self.check_entries(entries)[1])
                self.assertEqual(self.check_entries(entries, 'legacy'), (2, []))

    def test_duplicate_and_unknown_flavor(self):
        entries = [('lib/arm64-v8a/libmandy_llama.so', elf())] * 2
        self.assertTrue(self.check_entries(entries)[1])
        with self.assertRaisesRegex(ValueError, 'Unsupported APK flavor'):
            self.check_entries([], 'unknown')


if __name__ == '__main__':
    unittest.main()
