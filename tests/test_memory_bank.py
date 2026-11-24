from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/dev/caicompanion/app'

class MemoryBankTests(unittest.TestCase):
    def test_private_and_explicit(self):
        store = (JAVA / 'MemoryBankStore.kt').read_text()
        ui = (JAVA / 'RoleplayToolkitUi.kt').read_text()
        for token in ('noBackupFilesDir', 'AtomicFile', 'check(current == expected)', 'check(load(context) == entries)'):
            self.assertIn(token, store)
        for token in ('MemoryBank.forProfile(entries, profile)', 'MemoryBank.recall(entries, profile',
                      'MemoryBankStore.put(context, entries, entry)', 'previewContext(activity, packet)'):
            self.assertIn(token, ui)
        for token in ('WorkManager', 'evaluateJavascript', 'openConnection'):
            self.assertNotIn(token, store)
    def test_localization(self):
        def keys(locale):
            return {x.get('name') for x in ET.parse(ROOT / f'app/src/main/res/{locale}/strings.xml').getroot()
                    if x.get('name', '').startswith('memory_bank_')}
        self.assertEqual(keys('values'), keys('values-ru'))
        self.assertEqual(len(keys('values')), 14)
