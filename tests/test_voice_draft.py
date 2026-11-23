"""Structural Android voice-draft gate; executable pure draft rules are in PolicySmoke."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = (ROOT/'app/src/main/java/dev/caicompanion/app/VoiceDraftActivity.kt').read_text()
GECKO = (ROOT/'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt').read_text()
LEGACY = (ROOT/'app/src/main/java/dev/caicompanion/app/MainActivity.kt').read_text()
MANIFEST = (ROOT/'app/src/main/AndroidManifest.xml').read_text()

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

    def test_locales_expose_same_strings(self):
        en = ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot()
        ru = ET.parse(ROOT/'app/src/main/res/values-ru/strings.xml').getroot()
        a = {x.attrib['name'] for x in en.findall('string') if x.attrib['name'].startswith('voice_')}
        b = {x.attrib['name'] for x in ru.findall('string') if x.attrib['name'].startswith('voice_')}
        self.assertEqual(a, b)
        self.assertGreaterEqual(len(a), 22)
        self.assertIn('voice_privacy', a)

if __name__ == '__main__': unittest.main()
