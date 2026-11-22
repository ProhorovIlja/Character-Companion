# Security and privacy

The client is a browser shell, **not** a Character.AI API client. It never accepts user passwords in a native form, does not inject JavaScript bridges, doesn't save chat text, and doesn't proxy WebView traffic to a third-party server. HTTPS enforced for top-level navigation and downloads; TLS failures are canceled. All intent and origin handling is defensive, but it has not undergone formal security review.

Browser data stays in the Android WebView profile. `allowBackup=false` and Android 12+ data extraction rules exclude backups. The system itself or Character.AI may still process data under their own policies. No analytics dependency, scheduled jobs, work manager, wake locks, foreground service or system-wide VPN is used.

The host filter matches exact domains or subdomains. It cannot inspect encrypted app traffic beyond WebView's exposed request URLs; server-side, first-party, WebSocket, blobs and redirect chains can evade it. The conservative bundled rules are not a guarantee that Character.AI or other entities cannot track usage.

Mic and camera are disabled by default and, when enabled, require an in-app confirmation for requests from trusted first-party origins as well as Android runtime permission. Files are selected by Android system picker; downloads are delegated to Android DownloadManager. Cookies are only forwarded to a download for first-party service hosts.

JSON exports include preferences, user-created domain rules and bookmarks, not browser cookies/tokens, chat messages or credentials. Exports are written only when the user chooses a destination; protect those files if bookmarks are sensitive.

Future audit: OAuth and pop-up flows, multiwindow support, WebRTC call compatibility, remote navigation edge cases, Android 16 edge-to-edge/UI layout, actual HTTPS/download authorization and Network Security Config. Never circumvent Google embedded OAuth restrictions by spoofing credentials.
