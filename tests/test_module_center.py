from pathlib import Path
import unittest
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/dev/caicompanion/app'

class ModuleCenterTests(unittest.TestCase):
    def test_catalog_routes_and_labels(self):
        catalog = (JAVA / 'CompanionModules.kt').read_text()
        modules = set(re.findall(r'\b([A-Z_]+)\(Group\.[A-Z_]+\)', catalog))
        self.assertEqual(len(modules), 15)
        ui = (JAVA / 'RoleplayToolkitUi.kt').read_text()
        dispatch = ui.split('internal fun open(', 1)[1].split('private fun edit(', 1)[0]
        screen = (JAVA / 'ModuleCenterActivity.kt').read_text()
        labels = screen.split('private fun label(', 1)[1].split('private fun groupLabel(', 1)[0]
        for source in (dispatch, labels):
            self.assertEqual(set(re.findall(r'CompanionModules\.Module\.([A-Z_]+) ->', source)), modules)
        self.assertIn('Intent(activity, ModuleCenterActivity::class.java)', ui)
        self.assertIn('android:name=".ModuleCenterActivity" android:exported="false"', (ROOT / 'app/src/main/AndroidManifest.xml').read_text())
        for token in ('CompanionModules.sections(query, pins)', 'minHeight = dp(48)', 'ScrollView(this)', 'ViewCompat.setAccessibilityHeading', 'FLAG_SECURE'):
            self.assertIn(token, screen)
        for token in ('openConnection', 'evaluateJavascript', 'loadUrl(', 'DexClassLoader'):
            self.assertNotIn(token, screen)
    def test_pins_and_manual_tools(self):
        screen = (JAVA / 'ModuleCenterActivity.kt').read_text()
        ui = (JAVA / 'RoleplayToolkitUi.kt').read_text()
        for token in ('CompanionModules.decodePins', 'CompanionModules.encodePins(pins)', 'CompanionModules.toggle(pins, module)',
                      'CheckBox(this)', 'module_center_pin', 'sections.pinned', 'sections.groups'):
            self.assertIn(token, screen)
        self.assertIn('VoiceDraftActivity.handoffIntent(activity, false)', ui)
        self.assertIn('CompanionModules.Module.UPDATES -> ReleaseCenter.show(activity)', ui)
    def test_localization(self):
        for locale in ('values', 'values-ru'):
            root = ET.parse(ROOT / f'app/src/main/res/{locale}/strings.xml').getroot()
            keys = {e.get('name') for e in root}
            self.assertTrue({'module_center_' + k for k in ('title', 'search', 'creator', 'knowledge', 'utilities', 'empty', 'pinned', 'pin')} <= keys)
