"""Source gates for Android integration; executable recovery behavior in PolicySmoke."""
import unittest
from pathlib import Path
import xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
JAVA=ROOT/'app/src/main/java/dev/caicompanion/app'
VOICE=(JAVA/'VoiceDraftActivity.kt').read_text()
HANDOFF=(JAVA/'VoiceComposerHandoff.kt').read_text()
BROWSER=(JAVA/'BrowserSessionActivity.kt').read_text()
MAIN=(JAVA/'MainActivity.kt').read_text()
GECKO=(ROOT/'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt').read_text()
class VoiceResilience(unittest.TestCase):
    def test_private_editor_opts_out_of_view_state_and_keyboard_learning(self):
        for token in ('isSaveEnabled = false','isSaveFromParentEnabled = false','IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS','IME_FLAG_NO_PERSONALIZED_LEARNING'):
            self.assertIn(token, VOICE)
    def test_legacy_and_modern_back_confirm_discard(self):
        self.assertIn('OnBackInvokedCallback { confirmExit() }',VOICE)
        self.assertIn('override fun onBackPressed() { confirmExit() }',VOICE)
        self.assertIn('editor.text.isNotBlank()',VOICE)
        self.assertIn('R.string.voice_discard_confirm',VOICE)
        self.assertIn('unregisterOnBackInvokedCallback(it)',VOICE)
    def test_no_copy_save_or_clear_during_recognition(self):
        for method in ('copyDraft','saveDraft','clearDraft'):
            self.assertIn('draftActionReady()',VOICE.split('private fun '+method+'(',1)[1].split('\n    }',1)[0])
        self.assertIn('resources.configuration.fontScale >= 1.25f',VOICE)
    def test_failed_recovery_remains_available_without_automatic_insert(self):
        self.assertIn('recovery.preserve(draft)',HANDOFF)
        self.assertIn('if (recovery.hasDraft)',HANDOFF)
        self.assertIn('val opened = recovery.open',HANDOFF)
        self.assertIn('else R.string.voice_handoff_recover',HANDOFF)
        self.assertIn('if (!wasAwaiting) ticket = null',HANDOFF)
        self.assertIn('if (pending != null || recovery.hasDraft) return',HANDOFF)
        self.assertIn('recovery.clear()',HANDOFF)
    def test_browser_mode_failures_do_not_finish_source(self):
        for source,link in ((MAIN,'initialShare'),(GECKO,'shared')):
            self.assertIn(f'if (BrowserSessionActivity.enter(this, {link} ?: HOME, openNow = {link} != null)) return',source)
        self.assertIn('R.string.browser_mode_open_failed',BROWSER)
        self.assertIn('R.string.browser_mode_switch_failed',BROWSER)
        self.assertIn('store.browserSessionPreferred = true',BROWSER.split('private fun returnToEmbedded()',1)[1])
        self.assertIn('onRetainNonConfigurationInstance(): Any = BrowserSessionPolicy.safeTabUrl(destination)',BROWSER)
        self.assertIn('if (!openTab()) store.browserSessionProvider = previous',BROWSER)
        self.assertIn('permissionPending || draft == null',VOICE)
    def test_translations(self):
        for loc in ('values','values-ru'):
            keys={e.get('name') for e in ET.parse(ROOT/f'app/src/main/res/{loc}/strings.xml').getroot()}
            self.assertTrue({'voice_discard_confirm','voice_discard','voice_action_wait','voice_editor_open_failed','voice_handoff_recover','browser_mode_open_failed','browser_mode_switch_failed'} <= keys)
if __name__=='__main__': unittest.main()
