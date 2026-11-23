namespace CharacterCompanion.Windows.Security;

/// <summary>Small conservative third-party tracker filter. Not a full EasyList engine.</summary>
public static class TrackerPolicy
{
    private static readonly string[] Hosts =
    [
        "doubleclick.net", "googlesyndication.com", "google-analytics.com",
        "analytics.google.com", "scorecardresearch.com", "hotjar.com", "branch.io",
        "app-measurement.com", "facebook.net", "connect.facebook.net"
    ];

    public static bool Block(string? address, bool enabled)
    {
        if (!enabled || !Uri.TryCreate(address, UriKind.Absolute, out var uri) ||
            (uri.Scheme != Uri.UriSchemeHttps && uri.Scheme != Uri.UriSchemeHttp)) return false;
        var host = uri.IdnHost.TrimEnd('.');
        if (SitePolicy.IsFirstParty(address)) return false;
        return Hosts.Any(x => host.Equals(x, StringComparison.OrdinalIgnoreCase) ||
                              host.EndsWith("." + x, StringComparison.OrdinalIgnoreCase));
    }
}
