using CefSharp.BrowserSubprocess;

namespace CharacterCompanion.Windows;

/// <summary>
/// One executable for the WPF browser process and CEF renderer/GPU/utility subprocesses.
/// CefSharp's .NET Core package omits the standalone BrowserSubprocess EXE from
/// self-contained publish output; a regular WPF auto-generated entry point would
/// accidentally start another UI in a CEF child process.
/// </summary>
internal static class Program
{
    [STAThread]
    public static int Main(string[] args)
    {
        // Official CefSharp path: a non-negative return is the CHILD process exit
        // code. It must never instantiate Application or show a window.
        var subprocessExit = SelfHost.Main(args);
        if (subprocessExit >= 0) return subprocessExit;

        // Only the main browser process creates the WPF window. App.OnStartup
        // initializes the bundled CEF runtime with a private application profile.
        var app = new App();
        app.InitializeComponent();
        return app.Run();
    }
}
