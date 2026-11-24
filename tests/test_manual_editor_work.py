from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/dev/caicompanion/app'

class ManualEditorWorkTests(unittest.TestCase):
    def test_one_shot_and_lifecycle(self):
        worker = (JAVA / 'ManualEditorWork.kt').read_text()
        self.assertIn('Thread({', worker)
        self.assertIn('activity.runOnUiThread { complete(result) }', worker)
        self.assertIn('!activity.isFinishing && !activity.isDestroyed && dialog.isShowing', worker)
        self.assertIn('controls.forEach { it.isEnabled = false }', worker)
        self.assertIn('view.isEnabled = states[index]', worker)
        self.assertIn('dialog.setCancelable(false)', worker)
        for token in ('WorkManager', 'postDelayed', 'Timer(', 'schedule('):
            self.assertNotIn(token, worker)
    def test_editor_uses_application_context(self):
        ui = (JAVA / 'RoleplayToolkitUi.kt').read_text()
        for token in ('RoleplayToolkitStore.load(context, slot)', 'RoleplayToolkitStore.save(context, slot, text)',
                      'RoleplayToolkitStore.clear(context, slot)', 'MemoryBankStore.load(context)',
                      'MemoryBankStore.remove(context, entry)', 'MemoryBankStore.put(context, entries, entry)',
                      'ManualEditorWork.run(activity, dialog, listOf(input)', 'characterCounter(activity, body, MemoryBank.MAX_BODY)'):
            self.assertIn(token, ui)
        self.assertNotIn('Store.load(activity', ui)
        self.assertNotIn('Store.save(activity', ui)
        self.assertNotIn('Store.put(activity', ui)
        self.assertNotIn('Store.remove(activity', ui)
    def test_strings(self):
        for locale in ('values', 'values-ru'):
            root = ET.parse(ROOT / f'app/src/main/res/{locale}/strings.xml').getroot()
            for key in ('editor_working', 'editor_character_count'):
                self.assertEqual(len(root.findall(f"string[@name='{key}']")), 1)
