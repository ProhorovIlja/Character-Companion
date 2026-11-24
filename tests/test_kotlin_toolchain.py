"""Regression gates for the actual Kotlin metadata failure in manual build #5."""
import re
import unittest
from pathlib import Path
from manual_workflow_policy import manual_workflow_issues
ROOT = Path(__file__).resolve().parents[1]

class KotlinToolchainTests(unittest.TestCase):
    def test_compiler_and_stdlib_are_aligned(self):
        root = (ROOT/'build.gradle.kts').read_text()
        app = (ROOT/'app/build.gradle.kts').read_text()
        compiler = re.search(r'classpath\("org.jetbrains.kotlin:kotlin-gradle-plugin:([0-9.]+)"\)', root)
        self.assertIsNotNone(compiler)
        self.assertEqual(compiler.group(1), '2.4.21')
        self.assertIn('kotlin-bom:2.4.21', app)
        self.assertIn('kotlin-stdlib:2.4.21', app)
        self.assertIn('version "9.3.1"', root)
        self.assertNotIn('-Xskip-metadata-version-check', root + app)
        self.assertNotIn('android.builtInKotlin=false', (ROOT/'gradle.properties').read_text())
        self.assertNotIn('id("org.jetbrains.kotlin.android")', root + app)
        self.assertLess(root.index('storage-download.googleapis.com'), root.index('mavenCentral()'))

    def test_supported_pinned_gradle_and_current_actions(self):
        self.assertIn('gradle-9.7.0-bin.zip', (ROOT/'gradle/wrapper/gradle-wrapper.properties').read_text())
        source = (ROOT/'.github/workflows/build-android-apk-manual.yml').read_text()
        for action in ('actions/checkout@v7.0.1','actions/setup-java@v6.3.0','android-actions/setup-android@v4.0.4','actions/upload-artifact@v7.0.2'):
            self.assertIn(action, source)
        self.assertIn("java-version: '21'", source)
        self.assertEqual(manual_workflow_issues(ROOT), [])

    def test_independent_verified_flavors_do_not_hide_failure(self):
        source = (ROOT/'.github/workflows/build-android-apk-manual.yml').read_text()
        for flavor in ('gecko', 'legacy'):
            self.assertIn('id: ' + flavor, source)
            self.assertIn('bash scripts/build-apk.sh ' + flavor, source)
            self.assertIn('bash scripts/verify-debug-apk.sh ' + flavor, source)
            self.assertIn('steps.' + flavor + '.outcome', source)
        self.assertEqual(source.count('continue-on-error: true'), 2)
        self.assertLess(source.index('Upload verified debug APKs'), source.index('Report incomplete build as failure'))
        self.assertIn('Neither flavor produced a verified APK.', source)
        verify = (ROOT/'scripts/verify-debug-apk.sh').read_text()
        self.assertIn('CN=Android Debug', verify)
        self.assertIn('versionCode=', verify)
        self.assertIn('unzip -tqq', verify)
        self.assertIn("grep -Fxq 'classes.dex'", verify)

    def test_progress_receiver_is_explicit(self):
        source = (ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        self.assertIn('this.progress = store.textZoom - 75', source)

if __name__ == '__main__': unittest.main()
