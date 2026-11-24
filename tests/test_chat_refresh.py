from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

class ChatRefreshTests(unittest.TestCase):
    def test_embedded_refresh_is_contextual_and_manual(self):
        for path, renderer, state in (
            ('app/src/main/java/dev/caicompanion/app/MainActivity.kt', 'viewAtMenu', 'viewAtMenu === webView'),
            ('app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt', 'sessionAtMenu', 'sessionAtMenu === session'),
        ):
            source = (ROOT / path).read_text()
            menu = source.split('private fun showPageMenu(anchor: View)', 1)[1].split('\n    }', 1)[0]
            action = menu.split('ShellMenu.Action(', 2)[1]
            self.assertIn('R.string.refresh_current_chat else R.string.reload', action)
            self.assertIn(state, action)
            self.assertIn('ChatRefreshPolicy.samePage(pageAtMenu,', action)
            self.assertIn('ChatRefreshPolicy.isChat(pageAtMenu)', menu)
            self.assertEqual(action.count(renderer + '.reload()'), 1)
            for forbidden in ('clearCache(', 'loadUrl(', 'loadUri(', 'postDelayed(', 'evaluateJavascript('):
                self.assertNotIn(forbidden, action)

    def test_localization(self):
        for locale in ('values', 'values-ru'):
            root = ET.parse(ROOT / f'app/src/main/res/{locale}/strings.xml').getroot()
            self.assertEqual(len(root.findall("string[@name='refresh_current_chat']")), 1)
