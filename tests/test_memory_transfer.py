from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/dev/caicompanion/app'

class MemoryTransferTests(unittest.TestCase):
    def test_explicit_private_transfer(self):
        activity = (JAVA / 'MemoryBackupActivity.kt').read_text()
        for token in ('Intent.ACTION_CREATE_DOCUMENT', 'Intent.ACTION_OPEN_DOCUMENT', 'uri?.scheme != "content"',
                      'ManualEditorWork.load(this', 'MemoryBankBackup.decode', 'MemoryBankBackup.encode',
                      'MemoryBankStore.merge(context, preview.current, preview.incoming)', 'R.string.memory_transfer_review'):
            self.assertIn(token, activity)
        for token in ('takePersistableUriPermission', 'evaluateJavascript', 'openConnection', 'WorkManager'):
            self.assertNotIn(token, activity)
        self.assertIn('android:name=".MemoryBackupActivity" android:exported="false"',
                      (ROOT / 'app/src/main/AndroidManifest.xml').read_text())
        self.assertIn('Intent(activity, MemoryBackupActivity::class.java)', (JAVA / 'RoleplayToolkitUi.kt').read_text())
    def test_compare_before_merge(self):
        store = (JAVA / 'MemoryBankStore.kt').read_text().split('@Synchronized fun merge', 1)[1].split('@Synchronized fun remove', 1)[0]
        self.assertIn('check(current == expected)', store)
        self.assertIn('if (plan.added > 0) save(context, plan.entries)', store)
    def test_localization(self):
        def keys(locale):
            return {x.get('name') for x in ET.parse(ROOT / f'app/src/main/res/{locale}/strings.xml').getroot()
                    if x.get('name', '').startswith('memory_transfer_')}
        self.assertEqual(keys('values'), keys('values-ru'))
        self.assertEqual(len(keys('values')), 9)
