"""Foreground test doubles for Android build helpers; NOT real APK/Android compilation."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]

class ManualBuildKit(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / 'app-source'
        (self.root/'scripts').mkdir(parents=True)
        (self.root/'gradle/wrapper').mkdir(parents=True)
        for filename in ('doctor.sh', 'build-apk.sh'):
            shutil.copyfile(ROOT/'scripts'/filename, self.root/'scripts'/filename)
        shutil.copyfile(ROOT/'gradle/wrapper/gradle-wrapper.properties',
                        self.root/'gradle/wrapper/gradle-wrapper.properties')
        sdk = self.root/'local-android-sdk'
        (sdk/'platforms/android-36').mkdir(parents=True)
        (sdk/'platforms/android-36/android.jar').write_bytes(b'fixture_not_a_real_sdk')
        bin_dir = self.root/'fake-bin'
        bin_dir.mkdir()
        self.gradle = bin_dir/'gradle'
        self.env = os.environ.copy()
        self.env['ANDROID_HOME'] = str(sdk)
        self.env['PATH'] = str(bin_dir) + os.pathsep + self.env['PATH']
        self.env['MOCK_OUTPUT'] = str(self.root/'mock-output.bin')
        self.setup_gradle()

    def setup_gradle(self, version='8.13', output=False, exit_code=0):
        self.gradle.write_text(f'''#!/bin/sh
if [ "$1" = '--version' ]; then
    printf '\\nGradle {version}\\n\\n'
    exit {exit_code}
fi
'''+ ('''mkdir -p app/build/outputs/apk/legacy/debug
cp "$MOCK_OUTPUT" app/build/outputs/apk/legacy/debug/app-legacy-debug.apk
''' if output else '') + 'exit 0\n')
        self.gradle.chmod(0o755)

    def run_helper(self, script, *args):
        return subprocess.run(['bash', str(self.root/'scripts'/script), *args],
                              env=self.env, capture_output=True, text=True)

    def test_doctor_only_accepts_pinned_local_gradle(self):
        okay=self.run_helper('doctor.sh')
        self.assertEqual(okay.returncode, 0, okay.stdout+okay.stderr)
        self.assertIn('GRADLE_VERSION=8.13_VERIFIED',okay.stdout)
        self.assertIn('BUILD_PREREQUISITES=PRESENT_UNVERIFIED', okay.stdout)
        for ver in ('8.12', '9.0', '8.13.1', 'not-a-version'):
            with self.subTest(version=ver):
                self.setup_gradle(ver)
                bad=self.run_helper('doctor.sh')
                self.assertNotEqual(bad.returncode, 0)
                self.assertIn('GRADLE_VERSION=UNSUPPORTED_EXPECT_8.13', bad.stdout)
        self.setup_gradle('8.13',exit_code=1)
        self.assertNotEqual(self.run_helper('doctor.sh').returncode,0)

    def test_stale_apk_cannot_be_reused(self):
        apk=self.root/'app/build/outputs/apk/legacy/debug/app-legacy-debug.apk'
        apk.parent.mkdir(parents=True)
        apk.write_bytes(b'old build must be deleted')
        result=self.run_helper('build-apk.sh','legacy')
        self.assertNotEqual(result.returncode,0)
        self.assertIn('did not produce the expected',result.stderr)
        self.assertFalse(apk.exists())

    def test_invalid_apk_output_is_refused(self):
        self.setup_gradle(output=True)
        Path(self.env['MOCK_OUTPUT']).write_bytes(b'not a ZIP or an APK')
        result=self.run_helper('build-apk.sh','legacy')
        self.assertNotEqual(result.returncode,0)
        self.assertIn('APK failed ZIP integrity',result.stderr)

    def test_zip_without_android_dex_is_refused(self):
        self.setup_gradle(output=True)
        with zipfile.ZipFile(self.env['MOCK_OUTPUT'],'w') as z:
            z.writestr('AndroidManifest.xml',b'not a real Android manifest')
        result=self.run_helper('build-apk.sh','legacy')
        self.assertNotEqual(result.returncode,0)
        self.assertIn('lacks AndroidManifest.xml or classes.dex',result.stderr)

    def test_powershell_helper_supports_verified_installed_gradle(self):
        ps=(ROOT/'scripts/build-apk-windows.ps1').read_text()
        self.assertIn("Get-Command 'gradle' -CommandType Application",ps)
        self.assertIn("$versionText -notmatch '(?m)^Gradle 8\\.13\\s*$'",ps)
        self.assertIn('GRADLE_PROVIDER=LOCAL_GRADLE_8.13',ps)
        self.assertIn("GetEntry('AndroidManifest.xml')",ps)
        self.assertIn("GetEntry('classes.dex')",ps)
        self.assertIn('Remove-Item -LiteralPath $apk -Force',ps)
        self.assertIn('distributionSha256Sum=$expectedDist',ps)

if __name__=='__main__': unittest.main()
