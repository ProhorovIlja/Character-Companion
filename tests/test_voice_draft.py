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

class VoiceDraft(unittest.TestCase):
    def test_all_flavors_have_explicit_entrypoint(self):
        self.assertIn('Intent(this, VoiceDraftActivity::class.java)', GECKO)
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
        self.assertIn('VoiceDraftPolicy.append(editor.text.toString(), candidate)', ACTIVITY)
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

    def test_locales_expose_same_strings(self):
        en = ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot()
        ru = ET.parse(ROOT/'app/src/main/res/values-ru/strings.xml').getroot()
        a = {x.attrib['name'] for x in en.findall('string') if x.attrib['name'].startswith('voice_')}
        b = {x.attrib['name'] for x in ru.findall('string') if x.attrib['name'].startswith('voice_')}
        self.assertEqual(a, b)
        self.assertGreaterEqual(len(a), 22)
        self.assertIn('voice_privacy', a)

if __name__ == '__main__': unittest.main()
