"""Real JVM rules are covered by PolicySmoke; these enforce Android integration and manifests."""
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
POLICY = ROOT / 'app/src/main/java/dev/caicompanion/app/ExternalBrowserPolicy.kt'
GECKO = (ROOT / 'app/src/gecko/java/dev/caicompanion/app/GeckoActivity.kt').read_text()


class ExternalBrowserSafety(unittest.TestCase):
    def test_external_policy_is_pure_and_does_not_transfer_secrets(self):
        text = POLICY.read_text()
        self.assertIn('object ExternalBrowserPolicy', text)
        self.assertIn('UrlPolicy.isHttps(raw)', text)
        self.assertIn('UrlPolicy.isIdentityUrl(raw)', text)
        for term in ('"access_token"', '"code"', '"state"', '"refresh_token"',
                     '"redirect_uri"', '"signature"'):
            self.assertIn(term, text)
        self.assertNotIn('getCookie', text)
        self.assertNotIn('Authorization: Bearer', text)

    def test_activity_never_hands_unreviewed_url_to_system_browser(self):
        block = GECKO.split('private fun confirmBrowser(', 1)[1].split('private fun navigateBack(', 1)[0]
        self.assertIn('ExternalBrowserPolicy.target(url) ?: return', block)
        self.assertIn('Uri.parse(target)', block)
        self.assertNotIn('Uri.parse(url)', block)
        self.assertIn('R.string.gecko_external_sensitive', block)
        self.assertIn('Intent.CATEGORY_BROWSABLE', block)
        self.assertIn('ActivityNotFoundException', block)
        self.assertIn('SecurityException', block)

    def test_nested_authorization_values_have_a_real_jvm_guard(self):
        policy = POLICY.read_text()
        smoke = (ROOT / 'tests/PolicySmoke.kt').read_text()
        self.assertIn('val expanded = decodeLimited(query) ?: return HOME', policy)
        self.assertIn('embeddedKey.findAll(expanded)', policy)
        self.assertIn('payload=https%3A%2F%2Felse.test', smoke)
        self.assertIn('payload=one%26access_token%3DSECRET', smoke)

    def test_media_permissions_are_origin_pinned_and_revoked(self):
        url_policy = (ROOT / 'app/src/main/java/dev/caicompanion/app/UrlPolicy.kt').read_text()
        self.assertIn('fun sameTrustedMediaOrigin(', url_policy)
        self.assertIn('request.host.equals(visible.host, ignoreCase = true)', url_policy)
        self.assertIn('UrlPolicy.sameTrustedMediaOrigin(uri, activeUrl)', GECKO)
        self.assertIn('UrlPolicy.sameTrustedMediaOrigin(uri, expected)', GECKO)
        self.assertIn('UrlPolicy.sameTrustedMediaOrigin(expected, activeUrl)', GECKO)
        self.assertIn('androidPermissionPage = activeUrl', GECKO)
        self.assertIn('mediaPromptPage = activeUrl', GECKO)
        self.assertIn('revokeMediaPermissions()', GECKO)

    def test_no_page_load_on_crashed_session(self):
        block = GECKO.split('override fun onNewIntent(intent: Intent)', 1)[1].split('private val promptDelegate', 1)[0]
        self.assertIn('!needsRecovery', block)

    def test_both_translations_have_the_prompt_and_no_embed_links(self):
        for locale in ('values', 'values-ru'):
            root = ET.parse(ROOT / f'app/src/main/res/{locale}/strings.xml').getroot()
            text = root.find("string[@name='gecko_external_sensitive']")
            self.assertIsNotNone(text)
            self.assertTrue(len(''.join(text.itertext())) >= 80)

    def test_android_sources_have_no_webview_fallback_in_gecko_activity(self):
        self.assertNotIn('android.webkit.WebView', GECKO)
        self.assertNotIn('Microsoft.Web.WebView2', GECKO)

if __name__ == '__main__':
    unittest.main()
