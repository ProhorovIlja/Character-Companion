import shutil
import subprocess
import sys
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import check_mandy_exports as gate

VALID = ''.join(name + ' T 100 10\n' for name in sorted(gate.EXPECTED))


class MandyExports(unittest.TestCase):
    def test_exact_exports(self):
        gate.validate_exports(VALID)
        for output in ('', VALID.replace('nativeLoadV2', 'nativeLoad'),
                       VALID.replace(' T ', ' W '), VALID + 'ggml_init T 200 10\n',
                       VALID + VALID.splitlines()[0] + '\n', VALID.split('\n', 1)[1]):
            with self.subTest(output=output), self.assertRaises(ValueError):
                gate.validate_exports(output)

    def test_missing_duplicate_empty_and_oversized(self):
        with tempfile.TemporaryDirectory() as tmp:
            apk = Path(tmp) / 'fixture.apk'
            for entries in ([], [b''], [b'a', b'b'], [b'abcd']):
                with warnings.catch_warnings(), zipfile.ZipFile(apk, 'w') as z:
                    warnings.simplefilter('ignore', UserWarning)
                    for entry in entries:
                        z.writestr(gate.LIB, entry)
                with patch.object(gate, 'MAX_LIB_BYTES', 3), patch.object(gate.subprocess, 'check_output') as nm:
                    with self.assertRaises(ValueError):
                        gate.audit_apk_exports(apk, 'nm')
                    nm.assert_not_called()

    @unittest.skipUnless(all(shutil.which(x) for x in ('clang', 'ld.lld', 'nm')), 'Clang/LLD and nm required')
    def test_real_arm64_symbols_from_apk(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            source = root / 'fixture.c'
            binary = root / 'fixture.so'
            apk = root / 'fixture.apk'
            for mode in ('valid', 'stale', 'leaked'):
                names = sorted(gate.EXPECTED)
                if mode == 'stale':
                    names = [name.replace('nativeLoadV2', 'nativeLoad') for name in names]
                if mode == 'leaked':
                    names.append('ggml_init')
                source.write_text(''.join('int ' + name + '(void) { return 1; }\n' for name in names))
                subprocess.run(['clang', '--target=aarch64-linux-android29', '-fuse-ld=lld',
                                '-nostdlib', '-shared', '-Wl,-z,max-page-size=16384',
                                str(source), '-o', str(binary)], check=True, capture_output=True)
                with zipfile.ZipFile(apk, 'w', compression=zipfile.ZIP_DEFLATED) as z:
                    z.write(binary, gate.LIB)
                if mode == 'valid':
                    gate.audit_apk_exports(apk, 'nm')
                else:
                    with self.assertRaisesRegex(ValueError, 'does not match Mandy ABI'):
                        gate.audit_apk_exports(apk, 'nm')


if __name__ == '__main__':
    unittest.main()
