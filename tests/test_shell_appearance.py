from pathlib import Path
import unittest
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT/'app/src/main/java/dev/caicompanion/app'
RES = ROOT/'app/src/main/res'

class ShellAppearanceTest(unittest.TestCase):
    def test_theme_applied_before_activity_creation(self):
        for path in (JAVA/'MainActivity.kt', JAVA/'BrowserSessionActivity.kt',
                     JAVA/'VoiceDraftActivity.kt',
                     ROOT/'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt'):
            self.assertIn('ShellAppearance.apply(this)\n        super.onCreate(savedInstanceState)', path.read_text())

    def test_palette_attributes(self):
        themes = ET.parse(RES/'values/styles.xml').getroot()
        attrs = ET.parse(RES/'values/attrs.xml').getroot()
        declared = {item.attrib['name'] for item in attrs}
        for name in ('AppTheme', 'AppTheme.Amoled'):
            theme = themes.find(f"style[@name='{name}']")
            colors = {item.attrib['name']: item.text for item in theme}
            self.assertTrue(declared <= colors.keys())
            if name.endswith('Amoled'):
                self.assertEqual(colors['shellBackground'], '#000000')
                self.assertEqual(colors['shellChrome'], '#000000')
        self.assertIn('?attr/shellBackground', (RES/'layout/activity_main.xml').read_text())
        self.assertIn('?attr/shellAction', (RES/'drawable/settings_action.xml').read_text())

    def test_appearance_is_local_and_backward_compatible(self):
        store = (JAVA/'SettingsStore.kt').read_text()
        helper = (JAVA/'ShellAppearance.kt').read_text()
        self.assertIn('getBoolean("amoled_shell", false)', store)
        self.assertIn('put("amoledShell", amoledShell)', store)
        self.assertIn('parsed.optBoolean("amoledShell", false)', store)
        self.assertIn('R.string.shell_theme_restart', helper)
        self.assertIn('activity.recreate()', helper)
        self.assertNotIn('evaluateJavascript', helper)
        self.assertNotIn('setAlgorithmicDarkening', helper)

if __name__ == '__main__':
    unittest.main()
