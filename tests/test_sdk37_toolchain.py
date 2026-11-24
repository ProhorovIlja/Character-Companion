"""Guard the toolchain compatibility discovered in manual GitHub run #2."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[1]

class ToolchainCompatibilityTests(unittest.TestCase):
    def test_agp_gradle_and_android37(self):
        root=(ROOT/'build.gradle.kts').read_text()
        app=(ROOT/'app/build.gradle.kts').read_text()
        props=(ROOT/'gradle/wrapper/gradle-wrapper.properties').read_text()
        self.assertIn('version "9.3.1"',root)
        self.assertIn('version = release(37)',app)
        self.assertIn('minorApiLevel = companionSdkMinor',app)
        self.assertIn('gradleProperty("companionSdkMinor").orElse("2")',app)
        self.assertIn('companionSdkMinor in setOf(0, 2)',app)
        self.assertIn('targetSdk = 36',app)
        self.assertNotIn('org.jetbrains.kotlin.android',root+app)
        self.assertNotIn('kotlinOptions',app)
        self.assertIn('gradle-9.7.0-bin.zip',props)
        self.assertIn('84fbba45c7f4c64abc77460e1c00f541e9f960e3c7ed2538f1ede19eacd873ae',props)

    def test_build_helpers_require_android37(self):
        for path in ('scripts/doctor.sh','scripts/build-apk-windows.ps1'):
            self.assertIn('37.',(ROOT/path).read_text())
        workflow=(ROOT/'.github/workflows/build-android-apk-manual.yml').read_text()
        self.assertIn('bash scripts/install-android37-sdk.sh',workflow)
        installer=(ROOT/'scripts/install-android37-sdk.sh').read_text()
        self.assertIn('"platforms;android-$sdk_version"', installer)
        self.assertIn('legacy) sdk_version=37.0',installer)
        self.assertIn('gecko) sdk_version=37.2',installer)
        self.assertIn('bash scripts/install-android37-sdk.sh gecko',workflow)
        self.assertIn('platforms/android-$sdk_version/android.jar', installer)
        self.assertNotIn("'platforms;android-37'", installer)
        self.assertIn('ANDROID_COMPILE_SDK_GECKO=37.2',workflow)
        self.assertNotIn('ANDROID_COMPILE_SDK_LEGACY=37.0',workflow)
        self.assertIn('workflow_dispatch:',workflow)
        for trigger in ('push:', 'pull_request:', 'schedule:', 'workflow_run:'):
            self.assertNotIn('\n  '+trigger,workflow)

if __name__=='__main__': unittest.main()
