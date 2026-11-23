using CefSharp;
using CefSharp.Handler;

namespace CharacterCompanion.Windows.Browser;

/// <summary>Suppresses CEF popups; asks the host to handle a verified URL.</summary>
internal sealed class CompanionPopupHandler : LifeSpanHandler
{
    private readonly Action<string> _onPopup;
    internal CompanionPopupHandler(Action<string> onPopup) => _onPopup = onPopup;

    protected override bool OnBeforePopup(IWebBrowser chromiumWebBrowser, IBrowser browser,
        IFrame frame, string targetUrl, string targetFrameName,
        WindowOpenDisposition targetDisposition, bool userGesture, IPopupFeatures popupFeatures,
        IWindowInfo windowInfo, IBrowserSettings browserSettings, ref bool noJavascriptAccess,
        out IWebBrowser newBrowser)
    {
        newBrowser = null!;
        _onPopup(targetUrl);
        return true;
    }
}
