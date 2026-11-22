import pathlib
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]

class Structure(unittest.TestCase):
    def test_resource_xml(self):
        for path in (ROOT/'app/src/main/res').rglob('*.xml'):
            with self.subTest(path=path):
                ET.parse(path)
        ET.parse(ROOT/'app/src/main/AndroidManifest.xml')

    def test_android_only(self):
        self.assertTrue((ROOT/'app/build.gradle.kts').exists())
        self.assertFalse((ROOT/'src/windows').exists())

    def test_privacy_defaults(self):
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        self.assertIn('android:allowBackup="false"',manifest)
        self.assertIn('android:usesCleartextTraffic="false"',manifest)
        self.assertIn('android:exported="true"',manifest)
        activity=(ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
        self.assertIn('handler.cancel()',activity)
        self.assertIn('onRenderProcessGone',activity)
        self.assertIn('ServiceWorkerControllerCompat',activity)
        self.assertNotIn('@JavascriptInterface',activity)

    def test_no_scheduled_actions(self):
        self.assertFalse((ROOT/'.github/workflows').exists())
        source=''.join(p.read_text(errors='ignore') for p in ROOT.rglob('*.kt'))
        self.assertNotIn('WorkManager',source)
        self.assertNotIn('AlarmManager',source)
        self.assertNotIn('JobScheduler',source)

    def test_manifest_has_minimum_permissions(self):
        manifest=(ROOT/'app/src/main/AndroidManifest.xml').read_text()
        permissions=set(re.findall(r'android.permission.[A-Z_]+',manifest))
        self.assertEqual(permissions, {'android.permission.INTERNET','android.permission.RECORD_AUDIO','android.permission.CAMERA'})

    def test_conservative_host_rules(self):
        lines=(ROOT/'app/src/main/assets/blocked-hosts.txt').read_text().splitlines()
        rules=[s.strip() for s in lines if s.strip() and not s.startswith('#')]
        self.assertTrue(len(rules)>=20)
        self.assertFalse(any('character.ai' in r or 'c.ai' in r for r in rules))

if __name__=='__main__': unittest.main()
