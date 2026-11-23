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
            var settings = new CefSettings { CachePath = profile, RootCachePath = profile };
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
