import hashlib
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import build_mandy_windows as build


def fixture(directory):
    directory.mkdir()
    for name in build.REQUIRED:
        (directory / name).write_bytes(b'fixture')
    exe = bytearray(90)
    exe[:2] = b'MZ'
    struct.pack_into('<I', exe, 60, 64)
    exe[64:68] = b'PE\x00\x00'
    struct.pack_into('<H', exe, 68, 0x8664)
    struct.pack_into('<H', exe, 88, 0x20b)
    (directory / build.REQUIRED[0]).write_bytes(exe)


class PreviewBuild(unittest.TestCase):
    def test_audit(self):
        with tempfile.TemporaryDirectory() as tmp:
            stage = Path(tmp) / 'publish'
            fixture(stage)
            self.assertEqual(len(build.audit(stage)), len(build.REQUIRED))
            (stage / 'libcef.dll').write_bytes(b'fixture')
            with self.assertRaisesRegex(ValueError, 'Legacy browser'):
                build.audit(stage)

    def test_missing_component(self):
        with tempfile.TemporaryDirectory() as tmp:
            stage = Path(tmp) / 'publish'
            fixture(stage)
            (stage / 'e_sqlite3.dll').unlink()
            with self.assertRaisesRegex(ValueError, 'e_sqlite3'):
                build.audit(stage)

    def test_wrong_machine(self):
        with tempfile.TemporaryDirectory() as tmp:
            stage = Path(tmp) / 'publish'
            fixture(stage)
            path = stage / build.REQUIRED[0]
            data = bytearray(path.read_bytes())
            struct.pack_into('<H', data, 68, 0xaa64)
            path.write_bytes(data)
            with self.assertRaisesRegex(ValueError, 'x64'):
                build.audit(stage)

    def test_order_and_manifest(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            output = root / 'result'
            calls = []
            def run(args, **kwargs):
                self.assertTrue(kwargs['check'])
                calls.append(args)
                if args[1] == 'publish':
                    fixture(Path(args[-1]))
            build.build(root, output, 'dotnet', '10.0.404', run)
            self.assertEqual([c[1] for c in calls], ['run'] * 4 + ['publish'])
            self.assertEqual([c[3] for c in calls[:-1]], list(build.SMOKES))
            self.assertIn('windows/Mandy.MemorySmoke/Mandy.MemorySmoke.csproj', build.SMOKES)
            self.assertIn('windows/Mandy.AttachmentSmoke/Mandy.AttachmentSmoke.csproj', build.SMOKES)
            self.assertIn('--self-contained', calls[-1])
            info = (output / 'BUILD_INFO.txt').read_text().splitlines()
            self.assertEqual([line for line in info if line.startswith('SMOKE_PASSED=')],
                             ['SMOKE_PASSED=' + project for project in build.SMOKES])
            self.assertIn('UI_DEVICE_TESTED=NO', (output / 'BUILD_INFO.txt').read_text())
            for line in (output / 'SHA256SUMS.txt').read_text().splitlines():
                sha, name = line.split('  ', 1)
                self.assertEqual(hashlib.sha256((output / name).read_bytes()).hexdigest(), sha)
            with self.assertRaisesRegex(ValueError, 'existing output'):
                build.build(root, output, 'dotnet', '10.0.404', run)
            self.assertEqual(len(calls), 5)

    def test_smoke_failure_stops_publish(self):
        for index, project in enumerate(build.SMOKES):
            with self.subTest(project=project), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                output = root / 'result'
                calls = []
                def run(args, **kwargs):
                    calls.append(args)
                    if args[3] == project:
                        raise subprocess.CalledProcessError(1, args)
                with self.assertRaises(subprocess.CalledProcessError):
                    build.build(root, output, 'dotnet', '10.0.404', run)
                self.assertEqual([c[1] for c in calls], ['run'] * (index + 1))
                self.assertFalse(output.exists())

    def test_missing_runtime_component(self):
        for name in ('hostfxr.dll', 'hostpolicy.dll', 'Mandy.NativePreview.deps.json'):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as tmp:
                stage = Path(tmp) / 'publish'
                fixture(stage)
                (stage / name).unlink()
                with self.assertRaisesRegex(ValueError, name):
                    build.audit(stage)

    def test_failed_audit_no_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            output = root / 'result'
            def run(args, **kwargs):
                if args[1] == 'publish':
                    fixture(Path(args[-1]))
                    (Path(args[-1]) / 'coreclr.dll').unlink()
            with self.assertRaises(ValueError):
                build.build(root, output, 'dotnet', '10.0.404', run)
            self.assertFalse(output.exists())
            self.assertEqual(list(root.iterdir()), [])


if __name__ == '__main__':
    unittest.main()
