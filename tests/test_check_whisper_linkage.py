import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import check_whisper_linkage as gate
import check_apk_native as elf

EXPORT = gate.JNI + ' T 4364 14\n'
DYNAMIC = ' 0x1 (NEEDED) Shared library: [libc.so]\n 0xe (SONAME) Library soname: [libmandy_whisper.so]\n 0x0 (NULL) 0x0\n'


class LinkageGate(unittest.TestCase):
    def test_valid(self):
        self.assertEqual(gate.validate(EXPORT, DYNAMIC), ('libc.so',))
        self.assertEqual(gate.validate(EXPORT, DYNAMIC.replace('libc.so', 'libm.so')), ('libm.so',))

    def test_exports(self):
        for exports in ('', EXPORT.replace(' T ', ' W '), EXPORT.replace('V2', 'V1'), EXPORT + 'ggml_init T 123 42\n', EXPORT * 2):
            with self.subTest(exports=exports), self.assertRaises(ValueError):
                gate.validate(exports, DYNAMIC)

    def test_dependencies(self):
        for dependency in ('libggml.so', 'libc++_shared.so', 'libwhisper.so', '/tmp/libc.so', 'libc.so.6'):
            with self.subTest(dependency=dependency), self.assertRaises(ValueError):
                gate.validate(EXPORT, DYNAMIC.replace('libc.so', dependency))
        with self.assertRaises(ValueError):
            gate.validate(EXPORT, '0x1 (NEEDED) Shared library: [libc.so]\n' + DYNAMIC)

    def test_dynamic_identity(self):
        for dynamic in ('', DYNAMIC.replace('libmandy_whisper.so', 'libother.so'), DYNAMIC.replace('(NULL)', '(FLAGS)'), DYNAMIC.replace('[libc.so]', 'libc.so')):
            with self.subTest(dynamic=dynamic), self.assertRaises(ValueError):
                gate.validate(EXPORT, dynamic)

    def test_loader_settings(self):
        for tag in ('(RPATH) Library rpath: [/tmp]', '(RUNPATH) Library runpath: [/tmp]', '(TEXTREL) 0x0', '(FLAGS) TEXTREL BIND_NOW'):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                gate.validate(EXPORT, DYNAMIC + '0x12 ' + tag + '\n')

    def test_ndk_tool_discovery(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(gate.sys, 'platform', 'win32'):
            folder = Path(temp) / 'ndk/29.0.13113456/toolchains/llvm/prebuilt/windows-x86_64/bin'
            folder.mkdir(parents=True)
            with self.assertRaises(ValueError):
                gate.ndk_tools(temp)
            expected = tuple(folder / name for name in ('llvm-nm.exe', 'llvm-readelf.exe'))
            for path in expected:
                path.touch()
            self.assertEqual(gate.ndk_tools(temp), expected)

    @unittest.skipUnless(sys.platform == 'linux' and all(shutil.which(x) for x in ('clang', 'ld.lld', 'nm', 'readelf')), 'Linux Clang/LLD and ELF tools required')
    def test_real_arm64_elf(self):
        # Test-only cross-linked ELF: never loads or distributes these binaries.
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'dep.c').write_text('int fixture_dependency(void) { return 42; }')
            (root / 'fixture.c').write_text('extern int fixture_dependency(void);\nint ' + gate.JNI + '(void) { return fixture_dependency(); }\nint ggml_conflict(void) { return 1; }\n')
            (root / 'exports.map').write_text('{ global: ' + gate.JNI + '; local: *; };')
            def link(*args):
                subprocess.run(['clang', '--target=aarch64-linux-android29', '-fuse-ld=lld', '-nostdlib', '-shared', *args], cwd=root, check=True, capture_output=True)
            link('dep.c', '-Wl,-soname,libc.so', '-o', 'libc.so')
            link('dep.c', '-Wl,-soname,libggml.so', '-o', 'libggml.so')
            common = ['fixture.c', '-L.', '-Wl,-soname,libmandy_whisper.so,-z,max-page-size=16384']
            isolated = ['-Wl,--version-script=exports.map']
            for name, args, valid in (
                ('good.so', ['-lc', *isolated], True),
                ('leaked.so', ['-lc'], False),
                ('dependency.so', ['-lggml', *isolated], False),
                ('runpath.so', ['-lc', *isolated, '-Wl,-rpath,/tmp'], False),
            ):
                link(*common, *args, '-o', name)
                path = root / name
                with path.open('rb') as stream:
                    self.assertIsNone(elf.check_elf(stream, path.stat().st_size, 183))
                if valid:
                    self.assertEqual(gate.audit_linkage(path, 'nm', 'readelf'), ('libc.so',))
                else:
                    with self.assertRaises(ValueError):
                        gate.audit_linkage(path, 'nm', 'readelf')


if __name__ == '__main__':
    unittest.main()
