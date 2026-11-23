using System.Windows;
using CefSharp;

namespace CharacterCompanion.Windows;

public partial class App : Application
{
    private bool _cefStarted;

    protected override void OnStartup(StartupEventArgs e)
    {
        try
        {
            // The embedded, app-distributed CEF runtime is initialized once before the WPF window.
            // The profile is private to this app; neither Edge nor Firefox sessions are imported.
            var profile = Path.Combine(AppSettings.UserFolder, "CefProfile");
            Directory.CreateDirectory(profile);
            var hostExecutable = Environment.ProcessPath;
            if (string.IsNullOrWhiteSpace(hostExecutable) || !Path.IsPathFullyQualified(hostExecutable) ||
                !Path.GetFileName(hostExecutable).Equals("CharacterCompanion.Windows.exe", StringComparison.OrdinalIgnoreCase))
                throw new InvalidOperationException("Cannot identify the CEF host executable.");
            // The self-contained WPF executable doubles as the CEF renderer/GPU
            // subprocess. Do not launch a second WPF UI for --type=renderer/etc.
            var settings = new CefSettings
            {
                CachePath = profile,
                RootCachePath = profile,
                BrowserSubprocessPath = hostExecutable
            };
            if (!Cef.Initialize(settings)) throw new InvalidOperationException("CEF initialization returned false");
            _cefStarted = true;
            base.OnStartup(e);
        }
        catch (Exception ex)
        {
            MessageBox.Show("Не удалось запустить встроенный CEF: " + ex.Message +
                "\nПроверь комплектность CEF в папке программы и архитектуру x64.",
                "Character Companion", MessageBoxButton.OK, MessageBoxImage.Error);
            Shutdown(1);
        }
    }

    protected override void OnExit(ExitEventArgs e)
    {
        if (_cefStarted) Cef.Shutdown();
        base.OnExit(e);
    }
}
