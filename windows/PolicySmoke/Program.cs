using CharacterCompanion.Windows.Security;

// Foreground-only smoke test with no network, Windows-specific APIs, automation, or NuGet packages.
// Run manually: dotnet run --project windows/PolicySmoke/CharacterCompanion.PolicySmoke.csproj
var checkedCount = 0;
void Check(bool actual, string name)
{
    checkedCount++;
    if (!actual) throw new InvalidOperationException("FAIL " + name);
}

Check(SitePolicy.IsFirstParty("https://character.ai/"), "first-party root");
Check(SitePolicy.IsFirstParty("https://sub.character.ai/chat"), "first-party subdomain");
Check(!SitePolicy.IsFirstParty("https://character.ai.evil.org"), "reject spoofed first-party");
Check(!SitePolicy.IsFirstParty("https://evil@character.ai/"), "reject URL userinfo");
Check(!SitePolicy.IsFirstParty("https://character.ai:8443/"), "reject alternate port");
Check(SitePolicy.SafeBrowserHandoff("http://example.org/") == null, "reject HTTP");
Check(SitePolicy.SafeBrowserHandoff("javascript:alert(1)") == null, "reject JS scheme");
Check(SitePolicy.SafeBrowserHandoff("https://user:password@example.org/") == null, "reject credentials");
Check(SitePolicy.SafeBrowserHandoff("https://character.ai/") == SitePolicy.Home, "safe homepage");
Check(SitePolicy.SafeBrowserHandoff("https://character.ai/chat/abc") ==
      "https://character.ai/chat/abc", "public chat URL");
Check(SitePolicy.SafeBrowserHandoff("https://news.example.com/article?ref=x#section") ==
      "https://news.example.com/article?ref=x#section", "ordinary external link");
Check(SitePolicy.SafeBrowserHandoff("https://accounts.google.com/o/oauth2/v2/auth") ==
      SitePolicy.Home, "Google identity");
Check(SitePolicy.SafeBrowserHandoff("https://id.apple.com/signin") ==
      SitePolicy.Home, "Apple identity");
Check(SitePolicy.SafeBrowserHandoff("https://tenant.auth0.com/authorize") ==
      SitePolicy.Home, "Auth0 identity");
Check(SitePolicy.SafeBrowserHandoff("https://character.ai/auth/callback?code=secret") ==
      SitePolicy.Home, "callback path");
Check(SitePolicy.SafeBrowserHandoff("https://example.org/page?code=secret") ==
      SitePolicy.Home, "OAuth code query");
Check(SitePolicy.SafeBrowserHandoff("https://example.org/page?%63ode=secret") ==
      SitePolicy.Home, "encoded OAuth key");
Check(SitePolicy.SafeBrowserHandoff("https://example.org/page?%2563ode=secret") ==
      SitePolicy.Home, "twice-encoded OAuth key");
Check(SitePolicy.SafeBrowserHandoff("https://example.org/page?x=a%26code%3Dsecret") ==
      SitePolicy.Home, "nested OAuth credential");
Check(SitePolicy.SafeBrowserHandoff("https://example.org/page?state=secret") ==
      SitePolicy.Home, "OAuth state");
Check(SitePolicy.SafeBrowserHandoff("https://example.org/page#access_token=secret") ==
      SitePolicy.Home, "fragment token");
Check(SitePolicy.SafeBrowserHandoff("https://example.org/page?signature=secret") ==
      SitePolicy.Home, "signed URL");
Check(SitePolicy.SafeBrowserHandoff("https://character.ai/%25252Fauth/callback") ==
      SitePolicy.Home, "nested encoded auth path");
Check(SitePolicy.SafeBrowserHandoff("https://example.org/page?statefulness=x") ==
      "https://example.org/page?statefulness=x", "avoid substring false positives");

Console.WriteLine($"PASS {checkedCount} external-handoff policy cases; tested production SitePolicy.cs");
