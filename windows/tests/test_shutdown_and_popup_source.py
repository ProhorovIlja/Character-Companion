"""Foreground shutdown / popup controls (source audit, not Windows compilation)."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2] / 'windows' / 'CharacterCompanion.Windows'

class ShutdownAndPopupSource(unittest.TestCase):
    def src(self, p): return (ROOT/p).read_text(encoding='utf8')

    def test_wpf_close_cancels_lifetime_before_disposing_cef(self):
        s=self.src('MainWindow.xaml.cs')
        self.assertIn('private readonly CancellationTokenSource _windowLifetime = new();',s)
        self.assertRegex(s, r'Closing \+= [\s\S]*?_windowLifetime\.Cancel\(\)')
        self.assertLess(s.index('_windowLifetime.Cancel();'),s.index('Browser.Dispose();'))
        self.assertIn('_windowLifetime.Dispose()',s)

    def test_transcription_observes_window_lifetime_and_preserves_cleanup(self):
        s=self.src('MainWindow.xaml.cs')
        self.assertRegex(s, r'WhisperCliTranscriber\.TranscribeAsync\([\s\S]*?_windowLifetime\.Token\)')
        self.assertIn('catch (OperationCanceledException) when (_windowLifetime.IsCancellationRequested)',s)
        self.assertIn('MicrophoneRecorder.EraseTemporaryRecording(wav ?? recorder.FilePath)',s)
        self.assertIn('if (!_closing) SetRecordingButtons(false)',s)
        self.assertIn('if (_closing) return;',s)
        self.assertIn('private volatile bool _closing;',s)
        self.assertIn('if (!_closing)\n            {\n                SetRecordingButtons(false);',s)
        self.assertIn('QueueBrowserAction(() => OfferExternal(uri))',s)
        self.assertIn('if (!_closing && !Dispatcher.HasShutdownStarted) action();',s)

    def test_transcriber_does_not_spawn_when_already_cancelled(self):
        s=self.src('Speech/WhisperCliTranscriber.cs')
        self.assertLess(s.index('cancellationToken.ThrowIfCancellationRequested();'),s.index('if (!process.Start())'))
        self.assertIn('process.Kill(entireProcessTree: true)',s)

    def test_bridge_rejects_closed_window_before_cef_dispatch(self):
        s=self.src('MainWindow.xaml.cs')
        dispatch=s.index('Browser.EvaluateScriptAsync(script')
        self.assertIn('if (_closing || _windowLifetime.IsCancellationRequested) return;',s[:dispatch])
        self.assertIn('if (_closing || _windowLifetime.IsCancellationRequested) return;',s[dispatch:])

    def test_popup_requires_direct_user_gesture(self):
        s=self.src('Browser/CompanionPopupHandler.cs')
        self.assertIn('if (userGesture && !string.IsNullOrWhiteSpace(targetUrl)) _onPopup(targetUrl);',s)
        self.assertIn('return true;',s)

    def test_top_level_external_navigation_requires_user_gesture(self):
        s=self.src('Browser/CompanionRequestHandler.cs')
        self.assertIn('if (frame?.IsMain != true) return false;',s)
        self.assertIn('if (userGesture && SitePolicy.IsExternalHttps(request.Url))',s)
        self.assertIn('return true;',s)

if __name__=='__main__': unittest.main()
