"""Layout/interaction invariants for the compact Android shell (not a device render)."""
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'app/src/main/res'
JAVA=ROOT/'app/src/main/java/dev/caicompanion/app'
MAIN=(JAVA/'MainActivity.kt').read_text()
GECKO=(ROOT/'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt').read_text()
A='{http://schemas.android.com/apk/res/android}'
class ShellTests(unittest.TestCase):
    def test_legacy_has_two_compact_bars_and_four_actions(self):
        tree=ET.parse(RES/'layout/activity_main.xml').getroot()
        nodes={n.get(A+'id'):n for n in tree.iter() if n.get(A+'id')}
        self.assertEqual(nodes['@+id/chrome_header'].get(A+'layout_height'),'52dp')
        footer=nodes['@+id/chrome_footer']
        self.assertEqual(footer.get(A+'layout_height'),'56dp')
        self.assertEqual([n.get(A+'id') for n in footer],['@+id/home','@+id/bookmarks','@+id/voice','@+id/more'])
        self.assertEqual(nodes['@+id/web_container'].get(A+'layout_weight'),'1')
        for n in footer:
            self.assertTrue(n.get(A+'contentDescription'))
            self.assertTrue(n.get(A+'drawableTop'))
    def test_gecko_footer_after_renderer_and_ime(self):
        self.assertLess(GECKO.index('root.addView(surface'),GECKO.index('root.addView(actionsRow)'))
        self.assertIn('keyboardVisible = insets.isVisible(WindowInsets.Type.ime())',GECKO)
        self.assertIn('actionsRow.visibility = if (keyboardVisible) View.GONE else View.VISIBLE',GECKO)
        self.assertIn('chromeHeader.visibility = if (settings.compactChrome) View.GONE else View.VISIBLE',GECKO)
        self.assertEqual(GECKO.count('actionsRow.addView('),4)
    def test_overflow_preserves_commands_and_private_modes(self):
        for source in (MAIN,GECKO):
            for action in ('R.string.settings','R.string.forward','R.string.reload','R.string.share','R.string.browser_mode_sign_in','R.string.login_compatibility_button'):
                self.assertIn(action,source)
            self.assertIn('ShellMenu.show(this, anchor',source)
            self.assertIn('showPageMenu(',source)
        self.assertNotIn('addJavascriptInterface(',MAIN)
        self.assertNotIn('CookieManager',GECKO)
    def test_settings_grouped_and_direct_start_default(self):
        self.assertIn('showSettingsSection(categories[index])',MAIN)
        self.assertIn('showGeckoSettingsSection(categories[selected])',GECKO)
        self.assertIn('getBoolean("start_in_native_hub", false)',(JAVA/'SettingsStore.kt').read_text())
    def test_voice_stop_requests_final_result_without_invalidating_token(self):
        s=(JAVA/'VoiceDraftActivity.kt').read_text()
        fn=s.split('private fun requestFinalTranscript()',1)[1].split('private fun stopListening()',1)[0]
        self.assertIn('current.stopListening()',fn)
        self.assertNotIn('recognitionGuard.cancel()',fn)
        self.assertIn('cancelListenButton.visibility = View.VISIBLE',s)
        self.assertIn('if (listening) stopListening()',s)
    def test_bilingual_new_actions(self):
        for loc in ('values','values-ru'):
            keys={n.get('name') for n in ET.parse(RES/loc/'strings.xml').getroot()}
            self.assertTrue({'shell_more','shell_saved','shell_voice','voice_finish','shell_session_info','browser_mode_summary'}.issubset(keys))
if __name__=='__main__':unittest.main()
