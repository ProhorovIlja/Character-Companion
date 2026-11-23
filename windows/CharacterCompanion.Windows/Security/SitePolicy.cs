using System.Text.RegularExpressions;

namespace CharacterCompanion.Windows.Security;

/// <summary>
/// Browser origin and one-way handoff policy. CEF cookies and OAuth callbacks are
/// never copied into the user's separate system-browser session.
/// </summary>
public static class SitePolicy
{
    public const string Home = "https://character.ai/";

    private static readonly HashSet<string> SensitiveKeys = new(StringComparer.OrdinalIgnoreCase)
    {
        "code", "state", "access_token", "id_token", "refresh_token", "token",
        "session", "session_id", "auth", "authorization", "client_secret",
        "secret", "password", "jwt", "key", "api_key", "sig", "signature",
        "redirect_uri", "callback", "continue", "ticket", "request_uri",
        "id_token_hint", "code_verifier", "sso", "oauth_token", "redirect",
        "redirect_url", "return_url", "return_to", "next", "url"
    };
    private static readonly HashSet<string> SensitivePathParts = new(StringComparer.OrdinalIgnoreCase)
    {
        "oauth", "authorize", "authorization", "auth", "login", "signin",
        "sign-in", "callback", "verify", "token", "session", "logout",
        "reset-password", "password", "account", "api"
    };

    private static bool TryHttps(string? address, out Uri uri)
    {
        uri = null!;
        if (string.IsNullOrEmpty(address) || address.Length > 8192 ||
            address.Any(c => char.IsControl(c) || c is '\u202a' or '\u202b' or '\u202c'
                or '\u202d' or '\u202e' or '\u2066' or '\u2067' or '\u2068' or '\u2069')) return false;
        if (!Uri.TryCreate(address, UriKind.Absolute, out var parsed) ||
            parsed.Scheme != Uri.UriSchemeHttps || !parsed.IsDefaultPort ||
            !string.IsNullOrEmpty(parsed.UserInfo) || string.IsNullOrEmpty(parsed.Host)) return false;
        uri = parsed;
        return true;
    }

    public static bool IsFirstParty(string? address) =>
        TryHttps(address, out var uri) &&
        (uri.IdnHost.TrimEnd('.').Equals("character.ai", StringComparison.OrdinalIgnoreCase) ||
         uri.IdnHost.TrimEnd('.').EndsWith(".character.ai", StringComparison.OrdinalIgnoreCase));

    public static bool IsExternalHttps(string? address) =>
        TryHttps(address, out _) && !IsFirstParty(address);

    /// <summary>
    /// At most four percent-decoding passes; remaining escapes or invalid encodings
    /// are treated as potentially sensitive rather than allowing obfuscated keys.
    /// </summary>
    private static string? DecodeLimited(string value)
    {
        try
        {
            for (var i = 0; i < 4; i++) value = Uri.UnescapeDataString(value);
            return Regex.IsMatch(value, "%[a-fA-F0-9]{2}") ? null : value;
        }
        catch (UriFormatException) { return null; }
        catch (ArgumentException) { return null; }
    }

    public static bool IsIdentityOrTokenUrl(string? address)
    {
        if (!TryHttps(address, out var uri)) return true;
        var host = uri.IdnHost.TrimEnd('.');
        if (host.Equals("accounts.google.com", StringComparison.OrdinalIgnoreCase) ||
            host.Equals("appleid.apple.com", StringComparison.OrdinalIgnoreCase) ||
            host.Equals("id.apple.com", StringComparison.OrdinalIgnoreCase) ||
            host.Equals("auth0.com", StringComparison.OrdinalIgnoreCase) ||
            host.EndsWith(".auth0.com", StringComparison.OrdinalIgnoreCase)) return true;

        var path = DecodeLimited(uri.GetComponents(UriComponents.Path, UriFormat.UriEscaped));
        if (path == null || path.Split('/').Any(SensitivePathParts.Contains)) return true;

        var query = uri.GetComponents(UriComponents.Query, UriFormat.UriEscaped);
        foreach (var pair in query.Split('&', StringSplitOptions.RemoveEmptyEntries))
        {
            var key = DecodeLimited(pair.Split('=', 2)[0]);
            if (key == null || SensitiveKeys.Contains(key)) return true;
        }
        // Nested redirect URLs can hide code/state parameters in an ordinary key's value.
        var expandedQuery = DecodeLimited(query);
        if (expandedQuery == null) return true;
        // Match nested parameter names against the COMPLETE sensitive-key set.
        // The previous regex checked only a subset, so a value such as
        // ?payload=%3Fredirect_uri%3D... could cross the browser boundary.
        foreach (Match match in Regex.Matches(expandedQuery,
            @"(?:^|[?&#])([A-Za-z_][A-Za-z0-9_-]{0,63})=",
            RegexOptions.IgnoreCase | RegexOptions.CultureInvariant))
        {
            if (SensitiveKeys.Contains(match.Groups[1].Value)) return true;
        }

        var fragment = DecodeLimited(uri.GetComponents(UriComponents.Fragment, UriFormat.UriEscaped));
        // Only unstructured page anchors are safe. OAuth implicit grants use fragments.
        if (fragment == null || fragment.Length > 80 ||
            (fragment.Length > 0 && !Regex.IsMatch(fragment, @"\A[A-Za-z0-9_-]+\z"))) return true;
        return false;
    }

    /// <summary>
    /// Safe result is either the unchanged explicit HTTPS URL, the fixed homepage
    /// for credential-bearing links, or null for unsupported/insecure schemes.
    /// </summary>
    public static string? SafeBrowserHandoff(string? address)
    {
        if (!IsFirstParty(address) && !IsExternalHttps(address)) return null;
        return IsIdentityOrTokenUrl(address) ? Home : address;
    }
}
