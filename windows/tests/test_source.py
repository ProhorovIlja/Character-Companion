"""Static Windows-source validation; does NOT replace Windows compilation/device tests."""
from pathlib import Path
import re
import unittest
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]
WIN = ROOT / 'windows' / 'CharacterCompanion.Windows'

class WindowsSourceAudit(unittest.TestCase):
    def src(self, path):
        return (WIN / path).read_text(encoding='utf8')

    def test_wpf_xml(self):
        for path in WIN.rglob('*.xaml'):
            with self.subTest(file=path.name): ElementTree.parse(path)

    def test_xaml_handlers_exist(self):
        code = self.src('MainWindow.xaml.cs')
        text = self.src('MainWindow.xaml')
        for handler in set(re.findall(r'\b(?:Click|Checked|Unchecked|SelectionChanged)="(\w+)"',text)):
            with self.subTest(handler=handler): self.assertIn(handler+'(', code)

    def test_packages_and_wpf_target(self):
        project = ElementTree.parse(WIN / 'CharacterCompanion.Windows.csproj').getroot()
        self.assertEqual(project.findtext('.//TargetFramework'), 'net10.0-windows')
        self.assertEqual(project.findtext('.//UseWPF'), 'true')
        packages = {x.attrib['Include']:x.attrib['Version'] for x in project.findall('.//PackageReference')}
        self.assertIn('Microsoft.Web.WebView2', packages)
        self.assertIn('NAudio', packages)
        self.assertNotIn('Electron', packages)

    def test_trust_boundary(self):
        url = self.src('Security/SitePolicy.cs')
        bridge = self.src('Security/ComposerBridge.cs')
        self.assertIn('Uri.UriSchemeHttps',url)
        self.assertIn('hostname.EndsWith(".character.ai"',url)
        self.assertIn('unique.length !== 1',bridge)
        self.assertIn('buttons.length !== 1',bridge)
        self.assertIn('JsonSerializer.Serialize(text)',bridge)
        self.assertIn('untrusted-origin',bridge)
        self.assertIn('click-invoked-unconfirmed',bridge)

    def test_no_cloud_audio(self):
        speech = (self.src('Speech/WhisperCliTranscriber.cs')+
                  self.src('Speech/MicrophoneRecorder.cs'))
        self.assertIn('whisper-cli.exe', speech)
        self.assertIn('ArgumentList.Add', speech)
        self.assertIn('UseShellExecute = false', speech)
        self.assertIn('RecordingStopped', speech)
        self.assertIn('EraseTemporaryRecording', speech)
        self.assertNotIn('HttpClient',speech)
        self.assertNotIn('https://',speech)

    def test_explicit_send_button(self):
        code = self.src('MainWindow.xaml.cs')
        self.assertIn('RecordAndSend_Click',code)
        self.assertIn('StopAndTranscribeAsync(true)',code)
        self.assertIn('StopAndTranscribeAsync(false)',code)
        self.assertIn('SaveDraft_Click',code)
        self.assertIn('CopyDraft_Click',code)
        self.assertNotIn('Task.Run(() => InsertDraftAsync',code)

    def test_browser_handoff_is_not_cookie_import(self):
        code = self.src('MainWindow.xaml.cs')
        self.assertIn('SitePolicy.IsIdentityOrTokenUrl',code)
        self.assertIn('MessageBoxButton.YesNo',code)
        self.assertIn('SitePolicy.Home : address',code)
        self.assertNotIn('CookieManager.GetCookiesAsync',code)
        self.assertNotIn('AddOrUpdateCookie',code)

    def test_dictation_never_auto_sends_existing_draft(self):
        code=self.src('MainWindow.xaml.cs')
        self.assertIn('draftBeforeTranscription',code)
        self.assertIn('draftEditedWhileWorking',code)
        self.assertIn('canAutoSend = sendAfter && string.IsNullOrWhiteSpace(draftBeforeTranscription)',code)
        self.assertIn('if (canAutoSend)',code)
        self.assertIn('await InsertDraftAsync(true, fromTranscription: true)',code)
        self.assertIn('_bridgeBusy',code)
        self.assertIn('(_busy && !fromTranscription)',code)
        self.assertIn('DraftText_KeyDown',code)
        self.assertIn('ClearDraft_Click',code)

    def test_whisper_cleanup_waits_for_exit(self):
        code=self.src('Speech/WhisperCliTranscriber.cs')
        self.assertIn('process.Kill(entireProcessTree: true)',code)
        self.assertIn('await process.WaitForExitAsync(CancellationToken.None)',code)
        self.assertIn('WaitAsync(TimeSpan.FromSeconds(5))',code)

    def test_origin_rejects_credential_userinfo(self):
        code=self.src('Security/SitePolicy.cs')
        self.assertIn('!string.IsNullOrEmpty(uri.UserInfo)',code)

    def test_no_background_schedulers(self):
        code='\n'.join(p.read_text(encoding='utf8') for p in (ROOT/'windows').rglob('*.cs'))
        for forbidden in ['TaskSchedulerService','PeriodicTimer','Register-ScheduledTask','schtasks.exe','StartupTask']:
            self.assertNotIn(forbidden,code)

if __name__=='__main__': unittest.main()
