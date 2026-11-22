# Security and privacy model · v0.4.0

The Android client is a native wrapper that loads first-party Character.AI content. No native password form, Character.AI token extraction, private API calls, JS credential bridge, proxy/VPN, analytics SDK, automated tasks or content storage exist. The UI communicates via the official `WebView`; the website remains under Character.AI's control.

- **Navigations:** top-level links load in-app only for HTTPS Character.AI/c.ai and a small list of external identity origins. Other HTTPS hosts open in the system browser. HTTP, `intent:`, `javascript:` and `file:` navigation are refused. Standard HTTPS port only. Provider OAuth may still refuse embedded login.
- **TLS:** SSL handler always cancels certificate errors. Only trusted in-app page TLS errors trigger the full-page overlay; errors in unrelated subresources should not hide a valid chat page.
- **Media:** mic/camera denied by default. When allowed in settings, require trusted HTTPS origin, confirmation prompt and Android runtime permission for each request. No persistent site-level grants.
- **Browser:** `allowFileAccess=false`, file-to-file and universal file URLs disabled, no mixed content, safe browsing enabled, no developer WebView debugging.
- **Filtering:** exact host/subdomain rules. Built-in set has 21 domains; optional strict set has 20 more. Custom rules bounded to 500 lines each; allowlist overrides blocked entries and first-party service hosts cannot be blocked. No subscription circumvention or traffic decryption.
- **Bookmarks:** only first-party HTTPS URLs; remove fragments and unrecognized query parameters that could include tokens/state codes. JSON settings export does not include chat content, credentials, OAuth state, last page, or cookies.
- **Import:** read limit 200,000 decoded characters; reject invalid schema; store a complete imported profile using one SharedPreferences edit transaction.
- **Backup/storage:** application backups disabled in manifest and data-extraction XML. Browser cookies remain in Android WebView storage; manual settings export is user-controlled. Operating system and Character.AI still have their own privacy policies.
- **Limitations:** other apps/browsers may track independently, server-side first-party ads are not necessarily blocked, and Android WebView vulnerabilities/bugs depend on the installed provider version. No formal security audit or penetration test has been performed.

## v0.4 privacy hardening

- Only approved first-party content URLs may be bookmarked, copied or shared. Account, OAuth, callback, token, login and session paths are rejected, including decoded URL path segments. Unknown query keys and fragments are removed.
- Old saved bookmarks are re-sanitized on manual export, and `last_visited` is sanitized on read. The archive does not include cookies or chat messages.
- External navigation requires a user gesture and a confirmation prompt. Non-gesture third-party redirects are cancelled. Downloads are user-confirmed; session cookies are attached only for the trusted first-party host.
- Permission requests awaiting user decision are denied when navigation starts, app settings forbid media, or WebView is destroyed. No persistent microphone or camera grants are created by this shell.
- Four preset profiles modify shell options only. Private mode denies third-party cookies, so some sign-in or embedded site features may stop working. Compatibility mode explicitly disables local filtering but is not a bypass of Character.AI protections.
