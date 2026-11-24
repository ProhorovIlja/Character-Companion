"""Source-level gates for private, explicit dictation handoff (not a device/IME test)."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT/'app/src/main/java/dev/caicompanion/app'
HANDOFF = (JAVA/'VoiceComposerHandoff.kt').read_text()
VOICE = (JAVA/'VoiceDraftActivity.kt').read_text()
MAIN = (JAVA/'MainActivity.kt').read_text()
GECKO = (ROOT/'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt').read_text()
BROWSER = (JAVA/'BrowserSessionActivity.kt').read_text()
STORE = (JAVA/'SettingsStore.kt').read_text()
class VoiceHandoff(unittest.TestCase):
    def test_user_approved_private_result(self):
        self.assertIn('if (allowInsert)', VOICE)
        self.assertIn('setResult(RESULT_OK, Intent().putExtra(EXTRA_DRAFT, draft))', VOICE)
        self.assertIn('setIntent(Intent(this, VoiceDraftActivity::class.java))', VOICE)
        self.assertIn('activity.startActivityForResult(VoiceDraftActivity.handoffIntent(activity, next.eligible), REQUEST)', HANDOFF)
    def test_one_shot_focus_and_no_silent_send(self):
        for guard in ('activity.hasWindowFocus()', 'renderer.hasFocus()', 'renderer.isShown', 'renderer.onCheckIsTextEditor()', 'ticket?.consume(page)', 'TYPE_TEXT_FLAG_MULTI_LINE', 'TYPE_MASK_VARIATION'):
            self.assertIn(guard, HANDOFF)
        self.assertEqual(HANDOFF.count('connection.commitText(draft, 1)'), 1)
        self.assertLess(HANDOFF.index('pending = null', HANDOFF.index('fun deliver')), HANDOFF.index('tryInsert(renderer, draft)'))
        self.assertIn('VoiceDraftActivity.handoffIntent(activity, false, draft)', HANDOFF)
        for forbidden in ('evaluateJavascript', 'performEditorAction', 'sendKeyEvent', 'getTextBeforeCursor', 'ClipboardManager', 'requestFocus()', 'CookieManager', 'getTextAfterCursor'):
            self.assertNotIn(forbidden, HANDOFF)
    def test_both_flavors_invalidate_on_navigation_and_renderer_exit(self):
        for source in (MAIN, GECKO):
            self.assertIn('voiceHandoff.receive(resultCode, data)', source)
            self.assertIn('voiceHandoff.deliver(this,', source)
            self.assertIn('voiceHandoff.observe(url)', source)
            self.assertGreaterEqual(source.count('voiceHandoff.invalidate()'), 2)
            self.assertIn('voiceHandoff.clear()', source)
        self.assertIn('onPageStarted', MAIN)
        self.assertIn('onPageStart', GECKO)
    def test_recognition_results_are_consumed_only_once_and_no_late_permission_start(self):
        self.assertIn('if (!systemRecognitionPending) return', VOICE)
        self.assertIn('if (!permissionPending) return', VOICE)
        callback = VOICE.split('override fun onRequestPermissionsResult', 1)[1].split('override fun onRetainNonConfigurationInstance', 1)[0]
        self.assertNotIn('startOnDevice()', callback)
        self.assertIn('R.string.voice_permission_ready', callback)
    def test_provider_does_not_silently_switch(self):
        self.assertIn('store.browserSessionProvider = provider', BROWSER)
        self.assertIn('listOf(pinned), true)?.takeIf { it == pinned }', BROWSER)
        self.assertIn('browser_mode_provider_unavailable', BROWSER)
        self.assertIn('setMessage(R.string.browser_mode_change_warning)', BROWSER)
        self.assertIn('store.browserSessionProvider = ""', BROWSER)
        self.assertNotIn('put("browserSessionProvider"', STORE)
    def test_translations(self):
        for loc in ('values','values-ru'):
            names = {x.get('name') for x in ET.parse(ROOT/f'app/src/main/res/{loc}/strings.xml').getroot()}
            self.assertTrue({'voice_insert_return','voice_finish_first','voice_permission_ready','voice_handoff_check','voice_handoff_unavailable','browser_mode_change_provider','browser_mode_change_warning','browser_mode_provider_unavailable'} <= names)
if __name__ == '__main__': unittest.main()
