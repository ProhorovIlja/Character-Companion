from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/dev/caicompanion/app'

class GroupedShellMenuTests(unittest.TestCase):
    def test_native_submenus_and_dispatch(self):
        text = (JAVA / 'ShellMenu.kt').read_text()
        self.assertIn('popup.menu.addSubMenu(', text)
        self.assertIn('actions.size + section.ordinal + 1', text)
        self.assertIn('submenu.add(0, index + 1, order, action.title).isEnabled = action.enabled', text)
        self.assertIn('actions.getOrNull(item.itemId - 1)', text)
        self.assertIn('action == null || !action.enabled', text)
    def test_actions_stay_available(self):
        for path in (JAVA / 'MainActivity.kt', ROOT / 'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt'):
            source = path.read_text()
            menu = source.split('private fun showPageMenu(anchor: View)', 1)[1].split('\n    }', 1)[0]
            self.assertIn('R.string.refresh_current_chat else R.string.reload', menu)
            self.assertIn('ShellMenu.Action(R.string.settings)', menu)
            self.assertIn('ShellMenu.Action(R.string.roleplay_title)', menu)
            direct = [line for line in menu.splitlines() if 'ShellMenu.Action(' in line and 'section =' not in line]
            self.assertEqual(len(direct), 3)
            for section in ('CREATOR', 'PAGE', 'PRIVACY', 'SUPPORT'):
                self.assertIn('section = ShellMenu.Section.' + section, menu)
    def test_bilingual_sections(self):
        for locale in ('values', 'values-ru'):
            root = ET.parse(ROOT / f'app/src/main/res/{locale}/strings.xml').getroot()
            for name in ('creator', 'page', 'privacy', 'support'):
                self.assertEqual(len(root.findall(f"string[@name='shell_menu_{name}']")), 1)
