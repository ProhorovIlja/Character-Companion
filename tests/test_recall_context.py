from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/dev/caicompanion/app'

class RecallContextTests(unittest.TestCase):
    def test_selection_and_explicit_merge(self):
        ui = (JAVA / 'RoleplayToolkitUi.kt').read_text()
        for token in ('chooseRecall(activity, profile, result)', 'RecallContext.select(profile, entries,',
                      'context(activity, text)', 'recalled == null || it != RoleplayToolkit.Slot.MEMORY',
                      'RecallContext.combine(recalled, data)', 'research(activity, combined)'):
            self.assertIn(token, ui)
        self.assertNotIn('CharacterContext.compose(mapOf(RoleplayToolkit.Slot.MEMORY to result.text))', ui)
    def test_bilingual(self):
        for locale in ('values', 'values-ru'):
            root = ET.parse(ROOT / f'app/src/main/res/{locale}/strings.xml').getroot()
            for suffix in ('add_fields', 'choose', 'empty'):
                self.assertEqual(len(root.findall(f"string[@name='recall_context_{suffix}']")), 1)
