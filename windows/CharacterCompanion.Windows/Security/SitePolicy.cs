namespace CharacterCompanion.Windows.Security;

public static class SitePolicy
{
    public const string Home = "https://character.ai/";

    public static bool IsFirstParty(string? address)
    {
        if (!Uri.TryCreate(address, UriKind.Absolute, out var uri)) return false;
        if (uri.Scheme != Uri.UriSchemeHttps || !uri.IsDefaultPort ||
            !string.IsNullOrEmpty(uri.UserInfo)) return false;
        var hostname = uri.IdnHost.TrimEnd('.');
        return hostname.Equals("character.ai", StringComparison.OrdinalIgnoreCase)
               || hostname.EndsWith(".character.ai", StringComparison.OrdinalIgnoreCase);
    }

    public static bool IsExternalHttps(string? address) =>
        Uri.TryCreate(address, UriKind.Absolute, out var uri) &&
        uri.Scheme == Uri.UriSchemeHttps && uri.IsDefaultPort &&
        string.IsNullOrEmpty(uri.UserInfo) && !IsFirstParty(address);

    public static bool IsIdentityOrTokenUrl(string? address)
    {
        if (!Uri.TryCreate(address, UriKind.Absolute, out var uri)) return false;
        var host = uri.IdnHost;
        if (host.Equals("accounts.google.com", StringComparison.OrdinalIgnoreCase) ||
            host.Equals("appleid.apple.com", StringComparison.OrdinalIgnoreCase) ||
            host.EndsWith(".auth0.com", StringComparison.OrdinalIgnoreCase)) return true;
        // Do not forward any in-progress credential exchange into a different browser session.
        var query = uri.Query.ToLowerInvariant();
        return query.Contains("code=") || query.Contains("access_token=") ||
               query.Contains("id_token=") || query.Contains("state=");
    }
}
