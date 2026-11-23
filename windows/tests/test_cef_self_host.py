"""Offline source-structure guard for CefSharp .NET 10 self-contained child process host.

This cannot compile WPF/CEF: it guards a packaging regression that would let
renderer/GPU subprocesses start the UI or omit their required files.
"""
from pathlib import Path
from xml.etree import ElementTree
import unittest

ROOT = Path(__file__).resolve().parents[2]
APP = ROOT / 'windows/CharacterCompanion.Windows'


class CefSelfHostSource(unittest.TestCase):
    def test_self_host_has_explicit_sta_entry_point_before_wpf_ui(self):
        code = (APP / 'Program.cs').read_text(encoding='utf-8')
        self.assertIn('[STAThread]', code)
        self.assertIn('SelfHost.Main(args)', code)
        self.assertIn('if (subprocessExit >= 0) return subprocessExit;', code)
        self.assertLess(code.index('if (subprocessExit >= 0) return subprocessExit;'),
                        code.index('var app = new App();'))
        self.assertIn('app.InitializeComponent();', code)
        self.assertIn('return app.Run();', code)
        self.assertEqual(code.count('new App()'), 1)

    def test_wpf_project_selects_manual_entry_point_and_self_host_copy_policy(self):
        root = ElementTree.parse(APP / 'CharacterCompanion.Windows.csproj').getroot()
        self.assertEqual(root.findtext('.//StartupObject'), 'CharacterCompanion.Windows.Program')
        self.assertEqual(root.findtext('.//CefSharpExcludeSubProcessExe'), 'true')
        self.assertEqual(root.findtext('.//UseAppHost'), 'true')
        self.assertEqual(root.findtext('.//TargetFramework'), 'net10.0-windows')
        self.assertEqual(root.findtext('.//PlatformTarget'), 'x64')
        self.assertEqual(root.findtext('.//UseWPF'), 'true')
        self.assertNotIn('WebView2', (APP / 'App.xaml.cs').read_text(encoding='utf-8'))

    def test_cef_settings_uses_absolute_current_host_binary(self):
        app = (APP / 'App.xaml.cs').read_text(encoding='utf-8')
        self.assertIn('Environment.ProcessPath', app)
        self.assertIn('Path.IsPathFullyQualified(hostExecutable)', app)
        self.assertIn('CharacterCompanion.Windows.exe', app)
        self.assertIn('BrowserSubprocessPath = hostExecutable', app)
        self.assertIn('RootCachePath = profile', app)
        self.assertIn('Cef.Initialize(settings)', app)
        self.assertIn('Cef.Shutdown()', app)

    def test_manual_publish_fails_closed_if_cef_is_incomplete(self):
        build = (ROOT / 'windows/build-windows.ps1').read_text(encoding='utf-8')
        self.assertIn('--self-contained true', build)
        self.assertIn('-p:PublishSingleFile=false', build)
        self.assertIn('CefSharp.BrowserSubprocess.Core.dll', build)
        self.assertIn('icudtl.dat', build)
        self.assertIn('v8_context_snapshot.bin', build)
        self.assertIn('chrome_100_percent.pak', build)
        self.assertIn('CEF self-host runtime component missing', build)
        self.assertIn("Join-Path $published 'libcef.dll'", build)
        self.assertIn("Join-Path $published 'locales'", build)
        self.assertIn('CefSharp', build)
        self.assertNotIn('Start-Job', build)
        self.assertNotIn('Register-ScheduledTask', build)


if __name__ == '__main__': unittest.main()
