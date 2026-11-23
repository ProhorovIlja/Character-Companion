using CefSharp;
using CefSharp.Handler;
using CharacterCompanion.Windows.Security;

namespace CharacterCompanion.Windows.Browser;

/// <summary>
/// Blocks only unsafe TOP-LEVEL navigation; the site may still load its authentication,
/// CDN and content subresources. Tracker blocking is opt-in and checked per request.
/// CEF callbacks are not WPF UI-thread callbacks; the host must marshal the actions.
/// </summary>
internal sealed class CompanionRequestHandler : RequestHandler
{
    private readonly Func<bool> _filterEnabled;
    private readonly Action<string> _externalNavigation;
    private static readonly IResourceRequestHandler Blocked = new DenyResourceRequestHandler();

    internal CompanionRequestHandler(Func<bool> filterEnabled, Action<string> externalNavigation)
    {
        _filterEnabled = filterEnabled;
        _externalNavigation = externalNavigation;
    }

    protected override bool OnBeforeBrowse(IWebBrowser chromiumWebBrowser, IBrowser browser,
        IFrame frame, IRequest request, bool userGesture, bool isRedirect)
    {
        if (frame?.IsMain != true) return false;
        if (SitePolicy.IsFirstParty(request.Url)) return false;
        // A passive script or redirect must not cause an unsolicited external
        // browser prompt. Only a direct user navigation may request one.
        if (userGesture && SitePolicy.IsExternalHttps(request.Url)) _externalNavigation(request.Url);
        // No file:, http:, javascript: or unapproved external top-level navigation.
        return true;
    }

    protected override IResourceRequestHandler? GetResourceRequestHandler(
        IWebBrowser chromiumWebBrowser, IBrowser browser, IFrame frame, IRequest request,
        bool isNavigation, bool isDownload, string requestInitiator, ref bool disableDefaultHandling)
    {
        // Never filter a top-level navigation; OnBeforeBrowse handles it.
        if (isNavigation && frame?.IsMain == true) return null;
        return TrackerPolicy.Block(request.Url, _filterEnabled()) ? Blocked : null;
    }

    private sealed class DenyResourceRequestHandler : ResourceRequestHandler
    {
        protected override CefReturnValue OnBeforeResourceLoad(IWebBrowser chromiumWebBrowser,
            IBrowser browser, IFrame frame, IRequest request, IRequestCallback callback) => CefReturnValue.Cancel;
    }
}
