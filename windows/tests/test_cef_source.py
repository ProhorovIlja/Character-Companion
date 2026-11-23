"""Source-only regression tests: CEF source migration is NOT a .NET Windows build."""
from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
WIN = ROOT / "windows" / "CharacterCompanion.Windows"

class BrowserEngineMigrationTests(unittest.TestCase):
    def read(self, sub): return (WIN/sub).read_text(encoding='utf-8')

    def test_no_webview2_dependency_or_control(self):
        xml=self.read('CharacterCompanion.Windows.csproj')
        xaml=self.read('MainWindow.xaml')
        app=self.read('MainWindow.xaml.cs')
        packages={x.attrib['Include']:x.attrib['Version'] for x in ET.fromstring(xml).findall('.//PackageReference')}
        self.assertEqual(packages['CefSharp.Wpf.NETCore'],'156.0.300')
        self.assertNotIn('Microsoft.Web.WebView2',xml+xaml+app)
        self.assertNotIn('WebView2',xml+xaml+app)
        self.assertIn('ChromiumWebBrowser',xaml)
        self.assertIn('CefSharp',app)
        self.assertEqual(ET.fromstring(xml).findtext('.//PlatformTarget'),'x64')

    def test_cef_init_before_first_navigation_and_shutdown(self):
        app=self.read('App.xaml.cs')
        win=self.read('MainWindow.xaml.cs')
        self.assertIn('Cef.Initialize(settings)',app)
        self.assertIn('Cef.Shutdown()',app)
        self.assertIn('RootCachePath = profile',app)
        self.assertLess(win.index('Browser.RequestHandler ='), win.index('Browser.Load(SitePolicy.Home)'))
        self.assertLess(win.index('Browser.LifeSpanHandler ='), win.index('Browser.Load(SitePolicy.Home)'))

    def test_request_policy_blocks_external_navigation_and_tracking(self):
        handler=self.read('Browser/CompanionRequestHandler.cs')
        self.assertIn('frame?.IsMain != true',handler)
        self.assertIn('SitePolicy.IsFirstParty(request.Url)',handler)
        self.assertIn('SitePolicy.IsExternalHttps(request.Url)',handler)
        self.assertIn('TrackerPolicy.Block(request.Url, _filterEnabled())',handler)
        self.assertIn('CefReturnValue.Cancel',handler)
        self.assertIn('return true;',handler)
        popup=self.read('Browser/CompanionPopupHandler.cs')
        self.assertIn('OnBeforePopup',popup)
        self.assertIn('_onPopup(targetUrl)',popup)

    def test_origin_validation_and_json_script_envelope(self):
        win=self.read('MainWindow.xaml.cs')
        self.assertIn('SitePolicy.IsFirstParty(Browser.Address)',win)
        self.assertIn('TrimEnd(\';\')',win)
        self.assertIn('"JSON.stringify(" + expression + ")"',win)
        self.assertIn('Browser.EvaluateScriptAsync(script',win)
        self.assertIn('ComposerBridge.ParseResult(json)',win)
        self.assertIn('_bridgeBusy',win)

    def test_voice_and_draft_unchanged(self):
        win=self.read('MainWindow.xaml.cs')
        for name in ('WhisperCliTranscriber.TranscribeAsync','StopAndTranscribeAsync(true)',
                     'StopAndTranscribeAsync(false)','CopyDraft_Click','SaveDraft_Click',
                     'ClearDraft_Click','draftEditedWhileWorking','InsertDraftAsync(true, fromTranscription: true)'):
            self.assertIn(name,win)

if __name__=='__main__': unittest.main()
