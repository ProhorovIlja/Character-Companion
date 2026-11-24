from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT/'app/src/main/java/dev/caicompanion/app'

class LorebookTests(unittest.TestCase):
    def test_all_modes_and_private_activity(self):
        for p in (JAVA/'MainActivity.kt', JAVA/'BrowserSessionActivity.kt',
                  ROOT/'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt'):
            self.assertIn('Intent(this, LorebookActivity::class.java)', p.read_text())
        self.assertIn('android:name=".LorebookActivity" android:exported="false"',
                      (ROOT/'app/src/main/AndroidManifest.xml').read_text())

    def test_bounded_local_processing(self):
        a = (JAVA/'LorebookActivity.kt').read_text()
        for token in ('Intent.ACTION_OPEN_DOCUMENT', 'Intent.ACTION_CREATE_DOCUMENT',
                      'BoundedText.read(reader, LorebookPolicy.MAX_CHARS)', 'CodingErrorAction.REPORT',
                      'uri.scheme != "content"', 'if (parsed != null) book = parsed',
                      'R.string.lorebook_export_warning'):
            self.assertIn(token, a)
        for token in ('evaluateJavascript', 'CookieManager', 'https://', 'takePersistableUriPermission'):
            self.assertNotIn(token, a)
        j = (JAVA/'LorebookJson.kt').read_text()
        for token in ('safeJsonDepth(raw)', 'character_book', 'lorebook_v3', 'skipped++',
                      'put("comment", entry.title)', 'put("key", JSONArray(entry.keys))'):
            self.assertIn(token, j)

    def test_localization(self):
        def names(locale):
            root = ET.parse(ROOT/f'app/src/main/res/{locale}/strings.xml').getroot()
            return {x.get('name') for x in root if x.get('name', '').startswith('lorebook_')}
        self.assertEqual(names('values'), names('values-ru'))
        self.assertGreaterEqual(len(names('values')), 19)

if __name__ == '__main__':
    unittest.main()
