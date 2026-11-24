"""Structural Android voice-draft gate; executable pure draft rules are in PolicySmoke."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (ROOT/'app/src/main/java/dev/caicompanion/app/VoiceDraftActivity.kt').read_text()
GECKO = (ROOT/'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt').read_text()
LEGACY = (ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
MANIFEST = (ROOT/'app/src/main/AndroidManifest.xml').read_text()
GUARD = (ROOT/'app/src/main/java/dev/caicompanion/app/VoiceRecognitionGuard.kt').read_text()
FEEDBACK = (ROOT/'app/src/main/java/dev/caicompanion/app/VoiceRecognitionFeedback.kt').read_text()

class VoiceDraft(unittest.TestCase):
    def test_all_flavors_have_explicit_entrypoint(self):
        self.assertIn('voiceHandoff.launch(this, if (sessionReady()) activeUrl else null)', GECKO)
        self.assertIn('Intent(this, VoiceDraftActivity::class.java)', LEGACY)
        self.assertIn('android:name=".VoiceDraftActivity" android:exported="false"', MANIFEST)
        self.assertIn('android.speech.RecognitionService', MANIFEST)

    def test_private_on_device_is_default_and_system_requires_warning(self):
        self.assertIn('SpeechRecognizer.isOnDeviceRecognitionAvailable(this)', ACTIVITY)
        self.assertIn('SpeechRecognizer.createOnDeviceSpeechRecognizer(this)', ACTIVITY)
        self.assertIn('checkSelfPermission(Manifest.permission.RECORD_AUDIO)', ACTIVITY)
        self.assertIn('.setMessage(R.string.voice_system_warning)', ACTIVITY)
        self.assertIn('startActivityForResult(requestIntent(), SYSTEM_RECOGNITION_REQUEST)', ACTIVITY)
        self.assertIn('RecognizerIntent.EXTRA_PREFER_OFFLINE, true', ACTIVITY)
        self.assertNotIn('android.webkit.WebView', ACTIVITY)
        self.assertNotIn('getCookie(', ACTIVITY)
        self.assertNotIn('httpURLConnection', ACTIVITY)

    def test_draft_does_not_automatically_send_or_persist(self):
        self.assertIn('VoiceDraftPolicy.insert(original, candidate, start, end)', ACTIVITY)
        self.assertIn('Intent.ACTION_CREATE_DOCUMENT', ACTIVITY)
        self.assertIn('ClipData.newPlainText', ACTIVITY)
        self.assertIn('override fun onPause()', ACTIVITY)
        self.assertIn('if (listening) stopListening()', ACTIVITY)
        self.assertIn('recognizer?.destroy()', ACTIVITY)
        self.assertNotIn('onSaveInstanceState', ACTIVITY)
        self.assertNotIn('SharedPreferences', ACTIVITY)
        self.assertNotIn('session.loadUri', ACTIVITY)

    def test_stale_recognition_callbacks_cannot_mutate_a_new_session(self):
        self.assertIn('val sessionToken = recognitionGuard.start()', ACTIVITY)
        self.assertIn('if (!recognitionGuard.finish(sessionToken)) return', ACTIVITY)
        self.assertGreaterEqual(ACTIVITY.count('if (active())'), 3)
        self.assertIn('recognitionGuard.cancel()\n        runCatching { recognizer?.cancel() }', ACTIVITY)
        self.assertIn('fun finish(token: Long): Boolean', GUARD)
        self.assertIn('if (!isCurrent(token)) return false', GUARD)

    def test_save_is_snapshot_and_process_death_fails_closed(self):
        self.assertIn('pendingSaveText = snapshot', ACTIVITY)
        self.assertIn('val snapshot = pendingSaveText', ACTIVITY)
        self.assertIn('if (snapshot == null) { status.setText(R.string.voice_save_failed); return }', ACTIVITY)
        self.assertIn('snapshot.toByteArray(Charsets.UTF_8)', ACTIVITY)
        self.assertIn('override fun onRetainNonConfigurationInstance()', ACTIVITY)
        self.assertIn('lastNonConfigurationInstance as? RetainedDraft', ACTIVITY)
        self.assertNotIn('onSaveInstanceState', ACTIVITY)

    def test_language_choice_is_retained_only_in_memory(self):
        self.assertIn('val languageIndex: Int', ACTIVITY)
        self.assertIn('languageSpinner.setSelection(VoiceRecognitionOptions.index(retained.languageIndex))', ACTIVITY)
        self.assertIn('RetainedDraft(editor.text.toString(), pendingSaveText, languageSpinner.selectedItemPosition,', ACTIVITY)
        self.assertNotIn('onSaveInstanceState', ACTIVITY)
        self.assertNotIn('SharedPreferences', ACTIVITY)

    def test_actionable_recognizer_errors_keep_private_draft_unchanged(self):
        self.assertIn('VoiceRecognitionFeedback.issue(error)', ACTIVITY)
        self.assertIn('VoiceIssue.MODEL_MISSING', ACTIVITY)
        self.assertIn('VoiceIssue.LANGUAGE_NOT_SUPPORTED', ACTIVITY)
        self.assertIn('VoiceIssue.PERMISSION', ACTIVITY)
        self.assertIn('12 -> VoiceIssue.LANGUAGE_NOT_SUPPORTED', FEEDBACK)
        self.assertIn('13 -> VoiceIssue.MODEL_MISSING', FEEDBACK)
        self.assertIn('9 -> VoiceIssue.PERMISSION', FEEDBACK)
        # The numeric classifier cannot carry audio, transcript or credentials.
        for token in ['Context', 'Bundle', 'InputStream', 'SpeechRecognizer', 'StringBuilder']:
            if token == 'SpeechRecognizer':
                self.assertNotIn('import android.speech', FEEDBACK)
            else:
                self.assertNotIn(token, FEEDBACK)
        for rel in ('values', 'values-ru'):
            strings = ET.parse(ROOT/f'app/src/main/res/{rel}/strings.xml').getroot()
            names = {e.attrib['name'] for e in strings.findall('string')}
            for key in ('voice_error_no_speech', 'voice_error_language_unsupported',
                        'voice_error_model_missing', 'voice_error_busy',
                        'voice_error_microphone', 'voice_error_service'):
                self.assertIn(key, names)

    def test_clipboard_handoff_is_user_initiated_and_keeps_draft_on_failure(self):
        self.assertIn('setOnClickListener { copyAndReturn() }', ACTIVITY)
        self.assertIn('if (copyDraft()) finish()', ACTIVITY)
        self.assertIn('if (content.isBlank()) {', ACTIVITY)
        self.assertIn('ClipDescription.EXTRA_IS_SENSITIVE', ACTIVITY)
        self.assertIn('PersistableBundle()', ACTIVITY)
        self.assertIn('status.setText(R.string.voice_copy_failed)', ACTIVITY)
        self.assertNotIn('evaluateJavascript(', ACTIVITY)
        self.assertNotIn('webView.loadUrl(', ACTIVITY)
        for rel in ('values', 'values-ru'):
            strings = ET.parse(ROOT/f'app/src/main/res/{rel}/strings.xml').getroot()
            names = {e.attrib['name'] for e in strings.findall('string')}
            self.assertTrue({'voice_copy_return', 'voice_copy_empty', 'voice_copy_failed'} <= names)


    def test_native_voice_screen_remains_scrollable_with_ime_and_large_fonts(self):
        self.assertIn('val scroll = ScrollView(this).apply {', ACTIVITY)
        self.assertIn('isFillViewport = true', ACTIVITY)
        self.assertIn('setContentView(scroll)', ACTIVITY)
        self.assertIn('FrameLayout.LayoutParams(', ACTIVITY)
        self.assertIn('ViewGroup.LayoutParams.MATCH_PARENT, dp(196)', ACTIVITY)
        self.assertIn('EditorInfo.IME_FLAG_NO_EXTRACT_UI', ACTIVITY)
        self.assertIn('View.ACCESSIBILITY_LIVE_REGION_POLITE', ACTIVITY)
        self.assertNotIn('ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f', ACTIVITY)

    def test_speech_inserts_at_caret_and_restores_edit_selection_volatile_only(self):
        policy=(ROOT/'app/src/main/java/dev/caicompanion/app/VoiceDraftPolicy.kt').read_text()
        self.assertIn('fun insert(current: String, candidate: String?, start: Int, end: Int): Edit?', policy)
        self.assertIn('val bounds = selection(current, start, end)', policy)
        self.assertIn('Character.isHighSurrogate', policy)
        self.assertIn('editor.selectionStart.takeIf { it >= 0 } ?: original.length', ACTIVITY)
        self.assertIn('editor.setSelection(edit.caret)', ACTIVITY)
        self.assertIn('val selectionStart: Int, val selectionEnd: Int', ACTIVITY)
        self.assertIn('editor.setSelection(restored.first, restored.last)', ACTIVITY)
        self.assertNotIn('onSaveInstanceState', ACTIVITY)

    def test_undo_is_memory_only_and_does_not_overwrite_manual_edits(self):
        self.assertIn('lastSpeechEdit?.restore(editor.text.toString())', ACTIVITY)
        self.assertIn('lastSpeechEdit = null', ACTIVITY)
        self.assertIn('lastSpeechEdit?.available(s.toString())', ACTIVITY)
        self.assertIn('lastSpeechEdit = retained.speechUndo?.takeIf', ACTIVITY)
        self.assertIn('target.scheme != "content"', ACTIVITY)
        self.assertIn('VoiceDraftPolicy.validText(content)', ACTIVITY)

    def test_manual_offline_support_query_is_not_recording_or_downloading(self):
        probe = ACTIVITY.split('private fun checkOfflineModel()', 1)[1].split('private fun startOnDevice()', 1)[0]
        self.assertIn('Build.VERSION.SDK_INT < 33', probe)
        self.assertIn('checkRecognitionSupport(request, mainExecutor', probe)
        self.assertIn('supportProbe !== probe || !resumed', probe)
        self.assertIn('previous?.destroy()', probe)
        self.assertNotIn('startListening(', probe)
        self.assertNotIn('triggerModelDownload(', probe)
        self.assertIn('cancelSupportProbe()', ACTIVITY.split('override fun onPause()', 1)[1])
        self.assertIn('Settings.ACTION_VOICE_INPUT_SETTINGS', ACTIVITY)

    def test_partial_preview_never_mutates_draft(self):
        block = ACTIVITY.split('override fun onPartialResults(partialResults: Bundle?)', 1)[1].split('override fun onEvent', 1)[0]
        self.assertIn('!active() || !resumed', block)
        self.assertIn('VoiceRecognitionOptions.preview', block)
        self.assertNotIn('editor.', block)
        self.assertNotIn('acceptRecognition(', block)
        self.assertIn('speechPreview.text = ""', ACTIVITY)
        self.assertIn('languageSpinner.isEnabled = false', ACTIVITY)
        self.assertIn('VoiceRecognitionOptions.candidate', ACTIVITY)

    def test_locales_expose_same_strings(self):
        en = ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot()
        ru = ET.parse(ROOT/'app/src/main/res/values-ru/strings.xml').getroot()
        a = {x.attrib['name'] for x in en.findall('string') if x.attrib['name'].startswith('voice_')}
        b = {x.attrib['name'] for x in ru.findall('string') if x.attrib['name'].startswith('voice_')}
        self.assertEqual(a, b)
        self.assertGreaterEqual(len(a), 22)
        self.assertIn('voice_privacy', a)

if __name__ == '__main__': unittest.main()
