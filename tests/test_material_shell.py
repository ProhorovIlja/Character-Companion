from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
A = '{http://schemas.android.com/apk/res/android}'

class MaterialShellTests(unittest.TestCase):
    def test_theme_tokens_and_touch_target(self):
        tree = ET.parse(RES / 'values/styles.xml').getroot()
        def items(name):
            return {e.get('name'): e.text for e in tree.find(f"style[@name='{name}']")}
        base, amoled, button = (items(n) for n in ('AppTheme', 'AppTheme.Amoled', 'CompanionButton'))
        for key in ('shellText', 'shellMuted', 'shellAccent', 'shellRipple', 'shellDisabledText', 'shellDisabledAction'):
            self.assertIn(key, base)
        self.assertEqual(base['android:buttonStyle'], '@style/CompanionButton')
        for key in ('shellBackground', 'shellChrome', 'android:statusBarColor', 'android:navigationBarColor'):
            self.assertEqual(amoled[key], '#000000')
        self.assertEqual(button['android:minHeight'], '48dp')
        self.assertEqual(button['android:minWidth'], '48dp')
        self.assertEqual(button['android:textAllCaps'], 'false')
    def test_bounded_ripple_and_states(self):
        nav = ET.parse(RES / 'drawable/shell_nav_feedback.xml').getroot()
        self.assertEqual(nav.tag, 'ripple')
        self.assertEqual(nav.find('item').get(A+'id'), '@android:id/mask')
        button = ET.parse(RES / 'drawable/settings_action.xml').getroot()
        states = button.findall('./item/selector/item')
        self.assertEqual(states[0].get(A+'state_enabled'), 'false')
        self.assertEqual(states[1].get(A+'state_focused'), 'true')
    def test_vector_assets_and_license(self):
        for name in ('home', 'back', 'saved', 'mic', 'more'):
            vector = ET.parse(RES / f'drawable/ic_shell_{name}.xml').getroot()
            self.assertEqual(vector.get(A+'width'), '24dp')
            self.assertEqual(vector.get(A+'height'), '24dp')
            self.assertEqual(vector.find('group').get(A+'translateY'), '960')
            self.assertTrue(vector.find('./group/path').get(A+'pathData'))
            self.assertEqual(vector.get(A+'tint'), '@color/shell_control_text')
        self.assertEqual(ET.parse(RES / 'drawable/ic_shell_back.xml').getroot().get(A+'autoMirrored'), 'true')
        notice = (ROOT / 'app/src/main/assets/licenses/material-symbols.txt').read_text()
        self.assertIn('Apache License', notice)
        self.assertEqual(notice.count('(blob '), 5)
