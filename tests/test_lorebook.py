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

    def test_local_editor(self):
        a = (JAVA/'LorebookActivity.kt').read_text()
        for token in ('LorebookEditor.search(it, query, reviewOnly.isChecked)',
                      'visibleIndices.getOrNull(index)?.let(::entryActions)',
                      'LorebookEditor.replace(current, index,',
                      'LorebookEditor.remove(current, index)',
                      'LorebookEditor.metadata(current,',
                      'current.entries.any(LorebookEditor::review)',
                      'dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener',
                      'R.string.lorebook_close_warning',
                      'isSaveFromParentEnabled = false',
                      'if (keys.text.toString() == originalKeys) entry.keys'):
            self.assertIn(token, a)

    def test_explicit_private_drafts(self):
        a = (JAVA/'LorebookActivity.kt').read_text()
        store = (JAVA/'LorebookDraftStore.kt').read_text()
        for token in ('LorebookDraftStore.save(context, id, snapshot)',
                      'LorebookDraftStore.list(context)', 'LorebookDraftStore.load(context, item.id)',
                      'LorebookDraftStore.delete(context, item.id)', 'book === savedBook',
                      'savedId = stored', 'savedBook = snapshot',
                      'R.string.lorebook_library_delete_confirm', 'R.string.lorebook_creator_tips'):
            self.assertIn(token, a)
        for token in ('MAX_BOOKS = 16', 'context.filesDir', 'AtomicFile', 'UUID.randomUUID()',
                      'idPattern.matches(id)', '@Synchronized fun save',
                      'target.finishWrite(stream)', 'target.failWrite(stream)',
                      'read(target, LorebookDraftCodec::read) == book'):
            self.assertIn(token, store)
        self.assertNotIn('WorkManager', store)
        self.assertNotIn('http', store)

    def test_png_card_metadata_import(self):
        a = (JAVA/'LorebookActivity.kt').read_text()
        parser = (JAVA/'PngLorebookCard.kt').read_text()
        self.assertIn('PngLorebookCard.isPng(input)', a)
        self.assertIn('PngLorebookCard.extract(input) { closed.get() }', a)
        for token in ('MAX_PNG = 32L * 1024 * 1024', 'MAX_CHUNKS = 4096',
                      'CRC32()', 'Base64.getDecoder()', 'CodingErrorAction.REPORT',
                      'v3 ?: v2 ?: return null', 'stream.read() == -1'):
            self.assertIn(token, parser)
        self.assertNotIn('BitmapFactory', parser)

    def test_localization(self):
        def names(locale):
            root = ET.parse(ROOT/f'app/src/main/res/{locale}/strings.xml').getroot()
            return {x.get('name') for x in root if x.get('name', '').startswith('lorebook_')}
        self.assertEqual(names('values'), names('values-ru'))
        self.assertGreaterEqual(len(names('values')), 19)

if __name__ == '__main__':
    unittest.main()
