"""Exercise the APK verification gate using explicit mock SDK tools and ZIP fixtures."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


class DebugApkVerificationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'scripts').mkdir()
        for name in ('verify-debug-apk.sh', 'check_apk_native.py', 'check_apk_renderer.py'):
            shutil.copyfile(ROOT / 'scripts' / name, self.root / 'scripts' / name)
        (self.root / 'app').mkdir()
        shutil.copyfile(ROOT / 'app/build.gradle.kts', self.root / 'app/build.gradle.kts')
        self.sdk = self.root / 'sdk'
        self.tools = self.sdk / 'build-tools/36.0.0'
        self.tools.mkdir(parents=True)
        self.tool('apksigner', "printf '%s\\n' 'Signer #1 certificate DN: CN=Android Debug, O=Android, C=US'")
        self.tool('aapt', '''if [ "$2" = xmltree ]; then
    case "$3" in */gecko/*) flavor=gecko;; *) flavor=legacy;; esac
    printf '%s\n' '  E: manifest (line=2)' '    E: application (line=5)' '      E: meta-data (line=10)' '        A: android:name(0x01010003)="dev.caicompanion.app.RENDERER"'
    printf '        A: android:value(0x01010024)="%s"\n' "$flavor"
else
    printf "%s\n" "package: name='dev.caicompanion.app' versionCode='38' versionName='0.38.0-rc1'"
fi''')
        self.tool('zipalign', 'exit 0')
        self.env = dict(os.environ, ANDROID_HOME=str(self.sdk))

    def tool(self, name, source):
        p = self.tools / name
        p.write_text('#!/bin/sh\n' + source + '\n')
        p.chmod(0o755)

    def fixture(self, flavor, dex=True, native=True):
        p = self.root / f'app/build/outputs/apk/{flavor}/debug/app-{flavor}-debug.apk'
        p.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(p, 'w') as z:
            z.writestr('AndroidManifest.xml', b'mock only')
            if dex:
                z.writestr('classes.dex', b'mock only')
            if flavor == 'gecko' and native:
                z.writestr('lib/arm64-v8a/libxul.so', b'mock only')
        return p

    def verify(self, flavor):
        return subprocess.run(['bash', str(self.root / 'scripts/verify-debug-apk.sh'), flavor], env=self.env, capture_output=True, text=True)

    def output(self, flavor):
        return self.root / f'dist/CharacterCompanion-{flavor}-debug.apk'

    def test_verified_fixture_is_copied(self):
        for flavor in ('gecko', 'legacy'):
            p = self.fixture(flavor)
            result = self.verify(flavor)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(p.read_bytes(), self.output(flavor).read_bytes())
            report = (self.root / f'dist/native-audit-{flavor}.txt').read_text()
            self.assertIn('ZIP_ALIGNMENT_16K=PASSED', report)
            self.assertIn('DEVICE_16K_RUNTIME=NOT_TESTED', report)
            self.assertIn('ELF_CHECK_EXIT=NONZERO' if flavor == 'gecko' else 'ELF_CHECK_EXIT=0', report)

    def test_wrong_cert_rejected_and_stale_output_removed(self):
        self.fixture('legacy')
        output = self.output('legacy')
        output.parent.mkdir()
        output.write_bytes(b'stale')
        self.tool('apksigner', "printf '%s\\n' 'Signer #1 certificate DN: CN=Other'")
        self.assertNotEqual(self.verify('legacy').returncode, 0)
        self.assertFalse(output.exists())

    def test_invalid_signature_rejected(self):
        self.fixture('legacy')
        self.tool('apksigner', 'exit 1')
        self.assertNotEqual(self.verify('legacy').returncode, 0)
        self.assertFalse(self.output('legacy').exists())

    def test_wrong_version_rejected(self):
        self.fixture('legacy')
        self.tool('aapt', '''printf "%s\\n" "package: name='dev.caicompanion.app' versionCode='32' versionName='0.32.0-rc1'"''')
        self.assertNotEqual(self.verify('legacy').returncode, 0)
        self.assertFalse(self.output('legacy').exists())

    def test_wrong_renderer_manifest_rejected(self):
        self.fixture('gecko')
        self.tool('aapt', '''if [ "$2" = xmltree ]; then
    printf '%s\n' '  E: manifest (line=2)' '    E: application (line=5)' '      E: meta-data (line=10)' '        A: android:name(0x01010003)="dev.caicompanion.app.RENDERER"' '        A: android:value(0x01010024)="legacy"'
else
    printf "%s\n" "package: name='dev.caicompanion.app' versionCode='38' versionName='0.38.0-rc1'"
fi''')
        self.assertNotEqual(self.verify('gecko').returncode, 0)
        self.assertFalse(self.output('gecko').exists())

    def test_missing_dex_or_arm64_library_rejected(self):
        self.fixture('legacy', dex=False)
        self.assertNotEqual(self.verify('legacy').returncode, 0)
        self.fixture('gecko', native=False)
        self.assertNotEqual(self.verify('gecko').returncode, 0)
        self.assertFalse(self.output('gecko').exists())

if __name__ == '__main__':
    unittest.main()
