from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT/'app/src/main/java/dev/caicompanion/app'

class CharacterDraftTests(unittest.TestCase):
    def test_shared_private_activity(self):
        for path in (JAVA/'MainActivity.kt', JAVA/'BrowserSessionActivity.kt',
                     ROOT/'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt'):
            self.assertIn('Intent(this, CharacterDraftActivity::class.java)', path.read_text())
        self.assertIn('android:name=".CharacterDraftActivity" android:exported="false"',
                      (ROOT/'app/src/main/AndroidManifest.xml').read_text())

    def test_local_import_and_editing(self):
        activity = (JAVA/'CharacterDraftActivity.kt').read_text()
        for token in ('PngLorebookCard.extract(input) { closed.get() }',
                      'BoundedText.read(reader, LorebookPolicy.MAX_CHARS)',
                      'CodingErrorAction.REPORT', 'CharacterCardJson::parse',
                      'CharacterDraftPolicy.needsReview', 'CharacterDraftPolicy.edit',
                      'CharacterDraftPolicy.useAlternative', 'if (result != null) draft = result',
                      'ClipDescription.EXTRA_IS_SENSITIVE', 'IME_FLAG_NO_PERSONALIZED_LEARNING',
                      'isSaveFromParentEnabled = false', 'uri.scheme != "content"'):
            self.assertIn(token, activity)
        for token in ('evaluateJavascript', 'CookieManager', 'https://', 'WorkManager', 'takePersistableUriPermission'):
            self.assertNotIn(token, activity)
        adapter = (JAVA/'CharacterCardJson.kt').read_text()
        for token in ('safeJsonDepth(text)', '"chara_card_v2", "chara_card_v3"',
                      'alternate_greetings', 'post_history_instructions',
                      'CharacterDraftPolicy.fromCard'):
            self.assertIn(token, adapter)

    def test_private_character_library(self):
        activity = (JAVA/'CharacterDraftActivity.kt').read_text()
        store = (JAVA/'CharacterDraftStore.kt').read_text()
        codec = (JAVA/'CharacterDraftCodec.kt').read_text()
        for token in ('CharacterDraftStore.save', 'CharacterDraftStore.load', 'CharacterDraftStore.delete',
                      'savedDraft = null', 'manual-character-library'):
            self.assertIn(token, activity)
        for token in ('noBackupFilesDir', 'AtomicFile', 'MAX_DRAFTS', '@Synchronized', 'failWrite'):
            self.assertIn(token, store)
        for token in ('CodingErrorAction.REPORT', 'MAX_CHARS', 'read() == -1'):
            self.assertIn(token, codec)

    def test_localization(self):
        def names(locale):
            root = ET.parse(ROOT/f'app/src/main/res/{locale}/strings.xml').getroot()
            return {x.get('name') for x in root if x.get('name','').startswith('character_draft_')}
        self.assertEqual(names('values'), names('values-ru'))
        self.assertGreaterEqual(len(names('values')), 26)

if __name__ == '__main__':
    unittest.main()
