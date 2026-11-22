# Security and privacy model · v0.7.0

The Android client is a native wrapper that loads first-party Character.AI content. No native password form, Character.AI token extraction, private API calls, JS credential bridge, proxy/VPN, analytics SDK, automated tasks or content storage exist. The UI communicates via the official `WebView`; the website remains under Character.AI's control.

- **Navigations:** top-level links load in-app only for HTTPS Character.AI/c.ai and a small list of external identity origins. Other HTTPS hosts open in the system browser. HTTP, `intent:`, `javascript:` and `file:` navigation are refused. Standard HTTPS port only. Provider OAuth may still refuse embedded login.
- **TLS:** SSL handler always cancels certificate errors. Only trusted in-app page TLS errors trigger the full-page overlay; errors in unrelated subresources should not hide a valid chat page.
- **Media:** mic/camera denied by default. When allowed in settings, require trusted HTTPS origin, confirmation prompt and Android runtime permission for each request. No persistent site-level grants.
- **Browser:** `allowFileAccess=false`, `allowContentAccess=false`, file-to-file and universal file URLs disabled, no mixed content, safe browsing enabled, no developer WebView debugging.
- **Filtering:** exact host/subdomain rules. Built-in set has 21 domains; optional strict set has 20 more. Custom rules bounded to 500 lines each; allowlist overrides blocked entries and first-party service hosts cannot be blocked. No subscription circumvention or traffic decryption.
- **Bookmarks:** only first-party HTTPS URLs; remove fragments and unrecognized query parameters that could include tokens/state codes. JSON settings export does not include chat content, credentials, OAuth state, last page, or cookies.
- **Import:** read limit 200,000 decoded characters; reject invalid schema; store a complete imported profile using one SharedPreferences edit transaction.
- **Backup/storage:** application backups disabled in manifest and data-extraction XML. Browser cookies remain in Android WebView storage; manual settings export is user-controlled. Operating system and Character.AI still have their own privacy policies.
- **Limitations:** other apps/browsers may track independently, server-side first-party ads are not necessarily blocked, and Android WebView vulnerabilities/bugs depend on the installed provider version. No formal security audit or penetration test has been performed.

## v0.4 privacy hardening

- Only approved first-party content URLs may be bookmarked, copied or shared. Account, OAuth, callback, token, login and session paths are rejected, including decoded URL path segments. Unknown query keys and fragments are removed.
- Old saved bookmarks are re-sanitized on manual export, and `last_visited` is sanitized on read. The archive does not include cookies or chat messages.
- External navigation requires a user gesture and a confirmation prompt. Non-gesture third-party redirects are cancelled. Downloads are user-confirmed; session cookies are never forwarded to DownloadManager.
- Permission requests awaiting user decision are denied when navigation starts, app settings forbid media, or WebView is destroyed. No persistent microphone or camera grants are created by this shell.
- Four preset profiles modify shell options only. Private mode denies third-party cookies, so some sign-in or embedded site features may stop working. Compatibility mode explicitly disables local filtering but is not a bypass of Character.AI protections.


## v0.5 safeguards

- System `DownloadManager` may follow HTTPS redirects to other hosts. No WebView cookie header is ever attached; signed or private downloads may need manual full-browser use.
- In-memory blocking statistics contain matched rule domains only, capped at 128 domain entries. They reset on process exit or user command; no file export or analytics SDK.
- `ServiceWorkerControllerCompat` registers a process-wide callback capturing only the filter engine, not an Android Activity.
- OAuth URLs for Google/Apple receive a user-consent external-browser hand-off. Browser identity session cookies do not propagate back into WebView and successful client login is not guaranteed.
- The URI sanitizer rejects nested path escapes, traversal, control characters and untrusted bookmark/share hosts. Passwords, chat text and auth tokens are not collected.

## v0.6 download and history policy

- DownloadManager is permitted only for valid HTTPS URLs without query, fragment, identity provider or authentication path segments. Those values may carry signed credentials; explicit external-browser hand-off is safer than persisting the URL in the OS downloader. This policy is deliberately conservative and may prevent some downloads in-app.
- The browser never enables `content://` access. User-selected file picker handles are processed by the Android platform, not by exposing local files to arbitrary remote HTML.
- No `WebView.saveState()` data is written into Activity Bundles. Restoring browser history would persist URLs that may contain login tokens; optional resume persists only sanitized first-party content routes.
- Android back navigation is handled on API 33+ through the platform back dispatcher, unregistered when the Activity is destroyed.

## v0.7 manual domain-list ingestion

- Import begins only when the user selects a file through Android SAF; no remote fetches, subscriptions, scheduled refreshes or telemetry. The app supports only simple hostname rules, not full EasyList syntax.
- Bounded input and number of lines/domains; parse and validate before persisting. Unsupported lines are counted and previewed. AtomicFile replaces a complete list or preserves the prior list after write failure.
- Stored domains are private app data and excluded from the settings JSON; import the original file on a new device if wanted. No chat or login content is read.
- The first-party service and key identity domains are protected against accidental blocking, but additional website functionality can still break with aggressive third-party rules. Use Compatibility mode to diagnose.
- The Gradle Wrapper JAR in GitHub is copied from official Gradle v8.13.0 and SHA-256 checked against the upstream release checksum listing; the Gradle distribution download is also checksum-pinned.

## v0.8 manually shared links and screen-protection policy

- Android Share Sheet receives only user-initiated text/plain input; the parser caps received text at 16,384 characters and scans only HTTPS candidates. It delegates host, path and query sanitization to the existing `UrlPolicy`. No `VIEW` intent filters or unverified app links are registered. Test with malicious lookalike domains and account/callback URLs.
- After initial reception, the Activity replaces the retained untrusted Intent with an empty MAIN intent; subsequent shares are handled in `onNewIntent` without storing the raw shared content.
- Android WebView's automatic hierarchy state saving is explicitly disabled. No browser-cookie or WebView-state Bundle backups are created.
- The optional `FLAG_SECURE` toggle blocks ordinary platform screenshots, recording and recent-app thumbnails; it cannot guarantee protection from rooted devices, external cameras or compromised OS components. Default remains OFF so users can share their own screenshots if they wish.
- For manual JSON export, a null document-provider output stream is an error. No automatic cloud sync is implemented.
