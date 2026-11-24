from manual_workflow_policy import manual_workflow_issues
"""Focused Gecko SAF and prompt regression checks (structural, no Android emulator)."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
GECKO = (ROOT/'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt').read_text()
POLICY = (ROOT/'app/src/main/java/dev/caicompanion/app/GeckoTransferPolicy.kt').read_text()
BUILD = (ROOT/'app/build.gradle.kts').read_text()

class GeckoTransferTests(unittest.TestCase):
    def test_file_upload_is_explicit_android_saf_only(self):
        for token in ['onFilePrompt(', 'Intent.ACTION_OPEN_DOCUMENT',
                      'Intent.CATEGORY_OPENABLE', 'Intent.EXTRA_MIME_TYPES',
                      'Intent.EXTRA_ALLOW_MULTIPLE', 'onActivityResult(',
                      'prompt.confirm(applicationContext, safe.toTypedArray())']:
            self.assertIn(token, GECKO)
        self.assertNotIn('MediaStore.ACTION_IMAGE_CAPTURE', GECKO)
        self.assertNotIn('file://', GECKO)

    def test_file_chooser_requires_trusted_page_and_origin_stability(self):
        self.assertIn('!UrlPolicy.isTrustedMediaOrigin(activeUrl)', GECKO)
        self.assertIn('filePickerOrigin == activeUrl', GECKO)
        self.assertIn('GeckoTransferPolicy.isSelectedDocumentUri(it.toString())', GECKO)
        self.assertIn('GeckoTransferPolicy.MAX_SELECTED_FILES', GECKO)
        self.assertIn('selected.take(maxSelected)', GECKO)
        self.assertIn('pendingFileResult != null', GECKO)

    def test_unsupported_folder_prompt_is_denied(self):
        self.assertIn('prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.FOLDER', GECKO)
        self.assertIn('return GeckoResult.fromValue(prompt.dismiss())', GECKO)

    def test_download_is_user_confirmed_and_bounded(self):
        for token in ['onExternalResponse(', 'response.isSecure', 'response.statusCode',
                      'GeckoTransferPolicy.isDownloadSourceAllowed(',
                      'download_confirm_title', 'Intent.ACTION_CREATE_DOCUMENT',
                      'resolver.openOutputStream(target, "w")',
                      'GeckoTransferPolicy.copy(source, it)', 'isDaemon = true',
                      'resolver.delete(target, null, null)']:
            self.assertIn(token, GECKO)
        self.assertIn('MAX_DOWNLOAD_BYTES: Long = 256L * 1024L * 1024L', POLICY)
        self.assertIn('response.setReadTimeoutMillis(30_000L)', GECKO)
        self.assertIn('input: InputStream', POLICY)
        self.assertNotIn('DownloadManager', GECKO)

    def test_defective_input_stream_cannot_spin_unbounded(self):
        self.assertIn('var emptyReads = 0', POLICY)
        self.assertIn('if (++emptyReads >= 8) throw IOException("download_stream_stalled")', POLICY)
        self.assertIn('emptyReads = 0', POLICY)
        self.assertIn('if (n.toLong() > maxBytes - total)', POLICY)
        smoke = (ROOT / 'tests/PolicySmoke.kt').read_text(encoding='utf-8')
        self.assertIn('check(stalled.reads == 8)', smoke)
        self.assertIn('resumedOutput.toByteArray().contentEquals(byteArrayOf(42))', smoke)

    def test_cancel_and_activity_teardown_close_pending_streams(self):
        self.assertIn('try { pending?.body?.close() }', GECKO)
        self.assertIn('try { response.body?.close() }', GECKO)
        self.assertIn('if (!downloadPickerOpen) abandonDownload()', GECKO)
        self.assertIn('if (!filePickerOpen) finishFilePrompt(emptyList())', GECKO)
        self.assertIn('finishFilePrompt(emptyList())\n        abandonDownload()', GECKO)
        self.assertIn('override fun onDestroy()', GECKO)

    def test_pending_transfers_revoke_on_navigation_and_reload(self):
        self.assertIn('private var downloadPage: String? = null', GECKO)
        self.assertIn('downloadPage = activeUrl', GECKO)
        self.assertIn('val initialPage = downloadPage', GECKO)
        self.assertIn('GeckoTransferOriginPolicy.isSamePage(initialPage, activeUrl)', GECKO)
        self.assertIn('GeckoTransferOriginPolicy.isSamePage(filePickerOrigin, url)', GECKO)
        self.assertIn('GeckoTransferOriginPolicy.isSamePage(downloadPage, url)', GECKO)
        on_page_start = GECKO.split('override fun onPageStart(', 1)[1].split('override fun onPageStop(', 1)[0]
        self.assertIn('finishFilePrompt(emptyList())', on_page_start)
        self.assertIn('abandonDownload()', on_page_start)
        self.assertGreaterEqual(GECKO.count('downloadPage = null'), 2)
        pure = (ROOT/'app/src/main/java/dev/caicompanion/app/GeckoTransferOriginPolicy.kt').read_text()
        self.assertIn('original == current', pure)
        self.assertIn('UrlPolicy.isTrustedMediaOrigin(current)', pure)

    def test_modal_prompts_other_than_file_upload_remain_handled(self):
        for token in ['onAlertPrompt(', 'onButtonPrompt(', 'onTextPrompt(',
                      'onChoicePrompt(', 'setOnDismissListener',
                      'prompt.confirm(choices[index].id)', 'prompt.confirm(field.text.toString()']:
            self.assertIn(token, GECKO)

    def test_ru_en_strings_are_complete(self):
        for rel in ['app/src/main/res/values/strings.xml',
                    'app/src/main/res/values-ru/strings.xml']:
            root = ET.parse(ROOT/rel).getroot()
            keys = {e.attrib['name'] for e in root if e.tag == 'string'}
            self.assertIn('gecko_download_confirm',keys)
            self.assertIn('gecko_download_saved',keys)

    def test_application_version_moves_forward_without_platform_mixup(self):
        self.assertIn('versionCode = 35', BUILD)
        self.assertIn('versionName = "0.35.0-rc1"', BUILD)
        self.assertIn('create("gecko")',BUILD)
        self.assertIn('create("legacy")',BUILD)
        self.assertEqual(manual_workflow_issues(ROOT), [])

    def test_gecko_avoids_webview_cookie_copying_and_private_apis(self):
        for token in ['android.webkit.WebView','CookieManager','@JavascriptInterface',
                      'Authorization: Bearer', 'android.app.DownloadManager']:
            self.assertNotIn(token, GECKO)
        self.assertNotIn('https://api.character.ai', GECKO)

if __name__=='__main__': unittest.main()
