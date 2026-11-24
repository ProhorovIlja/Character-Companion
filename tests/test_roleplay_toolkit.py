from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/dev/caicompanion/app'

class RoleplayToolkitTests(unittest.TestCase):
    def test_all_modes(self):
        for path in (JAVA / 'MainActivity.kt', JAVA / 'BrowserSessionActivity.kt',
                     ROOT / 'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt'):
            self.assertIn('RoleplayToolkitUi.show(this)', path.read_text())

    def test_local_only(self):
        store = (JAVA / 'RoleplayToolkitStore.kt').read_text()
        ui = (JAVA / 'RoleplayToolkitUi.kt').read_text()
        for token in ('noBackupFilesDir', 'AtomicFile', 'finishWrite', 'failWrite', 'check(load(context, slot) == text)'):
            self.assertIn(token, store)
        for token in ('IME_FLAG_NO_PERSONALIZED_LEARNING', 'isSaveEnabled = false', 'EXTRA_IS_SENSITIVE',
                      'RoleplayToolkitStore.save(context, slot, text)', 'RoleplayToolkit.roll'):
            self.assertIn(token, ui)
        for forbidden in ('evaluateJavascript', 'CookieManager', 'openConnection', 'WorkManager', 'postDelayed'):
            self.assertNotIn(forbidden, ui + store)

    def test_localization(self):
        def names(locale):
            return {e.get('name') for e in ET.parse(ROOT / f'app/src/main/res/{locale}/strings.xml').getroot()
                    if e.get('name', '').startswith('roleplay_')}
        self.assertEqual(names('values'), names('values-ru'))
        self.assertEqual(len(names('values')), 23)
