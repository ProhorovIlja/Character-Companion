package dev.caicompanion.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.text.Editable
import android.text.TextWatcher
import android.text.InputFilter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.lang.Runtime
import java.io.ByteArrayInputStream
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewFeature
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabColorSchemeParams

/** Native Android shell. Browsing is first-party; no private API, HTML scraping or JS credential bridge. */
class MainActivity : Activity() {
    companion object {
        private const val HOME = "https://character.ai/"
        private const val FILE_PICKER = 701
        private const val MEDIA_PERMISSION = 702
        private const val EXPORT_SETTINGS = 703
        private const val IMPORT_SETTINGS = 704
        private const val IMPORT_FILTER_LIST = 705
        private const val EXPORT_FILTER_LIST = 706
        private val TEXT = Color.rgb(228, 231, 240)
        private val MUTED = Color.rgb(171, 178, 195)
        private val ACCENT = Color.rgb(168, 184, 255)
    }

    private lateinit var webView: WebView
    private lateinit var store: SettingsStore
    private lateinit var filter: FilterEngine
    private val loginProtection = LoginCompatibilitySession()
    private val voiceHandoff = VoiceComposerHandoff()
    private lateinit var importedRuleStore: ImportedRuleStore
    private var importedRules: RuleListParser.Rules = RuleListParser.Rules()
    private lateinit var container: FrameLayout
    private lateinit var errorOverlay: View
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var footer: View
    private lateinit var header: View
    private lateinit var titleLabel: TextView
    private var chooser: ValueCallback<Array<Uri>>? = null
    private var pendingMedia: PermissionRequest? = null
    private var lastUrl = HOME
    private var defaultAgent = ""
    private var pendingMediaResources: Array<String> = emptyArray()
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var destroyed = false
    private var rendererNeedsNavigation = false
    private var rendererDisposed = false
    private var keyboardVisible = false
    private var activityResumed = false
    private var nativeHub: View? = null
    private var modernBackCallback: OnBackInvokedCallback? = null
    private val rendererRecovery = RendererRecoveryBudget()
    private val deferredWebReload = DeferredWebReload()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The native workspace starts without initializing the Chromium WebView provider.
        store = SettingsStore(this)
        importedRuleStore = ImportedRuleStore(this)
        importedRules = importedRuleStore.load()
        val base = assets.open("blocked-hosts.txt").bufferedReader().use { it.readLines() }
        val strictRules = assets.open("strict-hosts.txt").bufferedReader().use { it.readLines() }
        filter = FilterEngine(base, strictRules).also {
            it.enabled = store.blocking; it.strict = store.strictBlocking
            it.replace(store.customRules, store.excludeRules, importedRules)
        }
        setContentView(R.layout.activity_main)
        container = findViewById(R.id.web_container)
        errorOverlay = findViewById(R.id.error_overlay)
        progress = findViewById(R.id.loading_progress)
        status = findViewById(R.id.status_badge)
        titleLabel = findViewById(R.id.page_title)
        footer = findViewById(R.id.chrome_footer)
        header = findViewById(R.id.chrome_header)
        bindControls()
        applySettings(reload = false)
        // Android 15-17 enforce edge-to-edge. Keep native controls outside system bars.
        val root = findViewById<View>(R.id.root)
        root.setOnApplyWindowInsetsListener { target, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Actual IME visibility, not a height heuristic that fails in multi-window.
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                keyboardVisible = insets.isVisible(WindowInsets.Type.ime())
                target.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                updateFooterVisibility()
            } else {
                @Suppress("DEPRECATION")
                target.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        root.requestApplyInsets()
        // Android 10: fall back to layout bounds because WindowInsets.Type.ime() is API 30+.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            root.viewTreeObserver.addOnGlobalLayoutListener {
                val visible = android.graphics.Rect().also { root.getWindowVisibleDisplayFrame(it) }
                keyboardVisible = root.rootView.height - visible.bottom > 180 * resources.displayMetrics.density
                updateFooterVisibility()
            }
        }
        // Do not persist browser history / OAuth state in framework instance-state Bundles.
        // Orientation is handled through configChanges; after process death restore only
        // an explicitly enabled and sanitized first-party content URL.
        val initialShare = incomingShareLink(intent)
        // Drop untrusted share extras from the Activity's retained Intent after validation.
        if (intent?.action == Intent.ACTION_SEND) setIntent(Intent(Intent.ACTION_MAIN))
        lastUrl = initialShare ?: (if (store.resumePage) store.lastVisited.ifBlank { HOME } else HOME)
        // An explicit Android Share request must take priority over the offline startup hub.
        if (store.browserSessionPreferred) {
            if (BrowserSessionActivity.enter(this, initialShare ?: HOME, openNow = initialShare != null)) return
        }
        if (NativeStartupPolicy.useHub(store.startInHub, initialShare)) showNativeHub()
        else openFirstPartyPage(lastUrl)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            modernBackCallback = OnBackInvokedCallback { navigateBack() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, modernBackCallback!!)
        }
    }

    /** Only user-initiated Android Share Sheet text is accepted, never arbitrary intents. */
    private fun incomingShareLink(shared: Intent?): String? {
        if (shared?.action != Intent.ACTION_SEND || shared.type?.substringBefore(';') != "text/plain") return null
        val text = runCatching { shared.getStringExtra(Intent.EXTRA_TEXT) }.getOrNull()
        return IncomingLinkParser.safeSharedLink(text)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_SEND && ::container.isInitialized) {
            val safe = incomingShareLink(intent)
            // This Activity is singleTop: clear the new, untrusted share Intent too.
            setIntent(Intent(Intent.ACTION_MAIN))
            if (safe == null) showToast(getString(R.string.shared_link_rejected))
            else openFirstPartyPage(safe)
        }
    }

    /** Allocate WebView only after an explicit navigation, never while browsing local bookmarks.
     * The XML layout deliberately contains no WebView to inflate at native startup.
     */
    private fun ensureWebView(): Boolean {
        if (::webView.isInitialized) return true
        var created: WebView? = null
        return try {
            WebView.setWebContentsDebuggingEnabled(false)
            val view = WebView(this)
            created = view
            defaultAgent = WebSettings.getDefaultUserAgent(this)
            installServiceWorkerFilter()
            configureWebView(view)
            container.addView(view, 0, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            webView = view
            syncRendererLifecycle()
            refreshBackForward()
            true
        } catch (_: RuntimeException) {
            created?.let { runCatching { (it.parent as? ViewGroup)?.removeView(it); it.destroy() } }
            showError(getString(R.string.error_loading))
            false
        }
    }

    private fun configureWebView(view: WebView) {
        // Do not let Android's view-hierarchy snapshots serialize browser-related state.
        view.isSaveEnabled = false
        view.isSaveFromParentEnabled = false
        view.setBackgroundColor(if (store.darkCanvas) Color.rgb(17, 18, 25) else Color.WHITE)
        view.settings.apply {
            javaScriptEnabled = true // Required by Character.AI
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            // FileChooserParams uses the platform picker; web pages never need content:// access.
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture = store.mediaRequiresGesture
            textZoom = store.textZoom
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = store.desktopMode
            loadWithOverviewMode = store.desktopMode
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = !store.reduceImages
            userAgentString = if (store.desktopMode) desktopAgent() else defaultAgent
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, store.cookies)
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val url = request.url.toString()
                if (url == "about:blank") return false
                if (!UrlPolicy.isHttps(url)) return true
                if (UrlPolicy.isIdentityUrl(url)) {
                    // Google and Apple may reject embedded OAuth. Explicit browser hand-off only.
                    // Browser sign-in cannot be silently transferred into this WebView session.
                    confirmIdentityNavigation(url)
                    return true
                }
                if (UrlPolicy.isInternal(url)) {
                    observeLoginPage(url)
                    return false
                }
                // Never launch an external application from a background redirect.
                if (request.hasGesture()) confirmExternalNavigation(url)
                return true
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                if (request.isForMainFrame) return null
                return filterResponse(request.url.toString())
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                if (destroyed || rendererNeedsNavigation || !::webView.isInitialized || view !== webView) return
                voiceHandoff.invalidate()
                observeLoginPage(url)
                // WebRTC grants are scoped to the page that requested them, not its successor.
                denyPendingMedia()
                errorOverlay.visibility = View.GONE
                lastUrl = UrlPolicy.sanitizeBookmark(url) ?: HOME
                status.setText(R.string.status_loading)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (destroyed || !::webView.isInitialized || view !== webView || url != view?.url) return
                observeLoginPage(url)
                titleLabel.text = view?.title?.take(65)?.ifBlank { "character.ai" } ?: "character.ai"
                // Persist only a canonical content link, not the raw page URL or fragment.
                if (store.resumePage) {
                    UrlPolicy.sanitizeBookmark(url)?.let { store.lastVisited = it }
                }
                updateStatus()
                // WebView handles cookies; flush them onPause instead of forcing disk IO
                // after each navigation (including the many SPA route changes).
                refreshBackForward()
            }
            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                if (destroyed || !::webView.isInitialized || view !== webView) return
                if (isReload) voiceHandoff.invalidate() else voiceHandoff.observe(url)
                observeLoginPage(url)
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && !destroyed) {
                    showError(error.description?.toString() ?: getString(R.string.error_loading))
                }
            }
            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest,
                response: android.webkit.WebResourceResponse) {
                if (!destroyed && request.isForMainFrame && response.statusCode in 500..599) {
                    showError(getString(R.string.error_server, response.statusCode))
                }
            }
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: android.net.http.SslError?) {
                handler.cancel() // Never ignore a certificate error.
                // A third-party image/ad TLS error must not replace a healthy main page.
                if (error != null && UrlPolicy.isInternal(error.url) &&
                    UrlPolicy.isInternal(view?.url)) showError(getString(R.string.error_tls))
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // Ignore stale termination callbacks from a previously discarded view.
                if (destroyed || rendererDisposed || !::webView.isInitialized || view !== webView) return true
                voiceHandoff.invalidate()
                rendererNeedsNavigation = true
                loginProtection.reset()
                filter.loginBypass = false
                denyPendingMedia()
                leaveFullscreen()
                chooser?.onReceiveValue(null)
                chooser = null
                val index = container.indexOfChild(view)
                container.removeView(view)
                rendererDisposed = true
                runCatching { view.destroy() }
                var replacement: WebView? = null
                try {
                    val next = WebView(this@MainActivity)
                    replacement = next
                    configureWebView(next)
                    container.addView(next, index.coerceIn(0, container.childCount), FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                    webView = next
                    rendererDisposed = false
                    syncRendererLifecycle()
                    if (!RendererRecoveryBudget.canRestoreAutomatically(detail.didCrash(), activityResumed, nativeHub != null)) {
                        showError(getString(R.string.renderer_recovery_required))
                    } else if (rendererRecovery.allowAutomaticRestart(SystemClock.elapsedRealtime())) {
                        rendererNeedsNavigation = false
                        deferredWebReload.consumedByNavigation()
                        showToast(getString(R.string.browser_restarted))
                        webView.loadUrl(NativeWorkspaceNavigation.safeDestination(lastUrl))
                    } else {
                        showError(getString(R.string.renderer_crash_loop))
                    }
                } catch (_: RuntimeException) {
                    // Never leave a destroyed WebView available to the UI after allocation failure.
                    rendererDisposed = true
                    destroyed = true
                    replacement?.let {
                        runCatching { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }
                    }
                    showToast(getString(R.string.error_loading))
                    finish()
                }
                return true
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(customView: View, callback: CustomViewCallback) {
                enterFullscreen(customView, callback)
            }
            override fun onHideCustomView() { leaveFullscreen() }
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 0..99 && fullscreenView == null) View.VISIBLE else View.GONE
                refreshBackForward()
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                titleLabel.text = title?.take(65)?.ifBlank { "character.ai" } ?: "character.ai"
            }
            override fun onPermissionRequest(request: PermissionRequest) = runOnUiThread {
                if (destroyed) request.deny() else requestMediaPermission(request)
            }
            override fun onPermissionRequestCanceled(request: PermissionRequest) = runOnUiThread {
                if (pendingMedia === request) {
                    pendingMedia = null
                    pendingMediaResources = emptyArray()
                }
            }
            override fun onShowFileChooser(
                webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                chooser?.onReceiveValue(null)
                chooser = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent()
                        ?: throw IllegalStateException("Picker unavailable")
                    startActivityForResult(intent, FILE_PICKER)
                    true
                } catch (_: Exception) {
                    chooser?.onReceiveValue(null)
                    chooser = null
                    false
                }
            }
        }
        view.setDownloadListener(DownloadListener { url, agent, disposition, mime, _ ->
            val safeUrl = url ?: return@DownloadListener
            when (DownloadPolicy.classify(safeUrl)) {
                DownloadPolicy.Kind.REJECT -> showToast(getString(R.string.download_url_rejected))
                DownloadPolicy.Kind.BROWSER_ONLY -> {
                    val target = ExternalBrowserPolicy.target(safeUrl) ?: return@DownloadListener
                    AlertDialog.Builder(this)
                        .setTitle(R.string.download_confirm_title)
                        .setMessage(if (target != safeUrl) R.string.gecko_external_sensitive
                            else R.string.download_browser_only_message)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.open_browser) { _, _ -> openExternal(target) }
                        .show()
                }
                DownloadPolicy.Kind.SAFE_SYSTEM -> {
                    val name = SafeFilename.from(URLUtil.guessFileName(safeUrl, disposition, mime))
                    val host = Uri.parse(safeUrl).host ?: return@DownloadListener
                    AlertDialog.Builder(this)
                        .setTitle(R.string.download_confirm_title)
                        .setMessage(getString(R.string.download_confirm_message, name, host))
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.confirm) { _, _ ->
                            enqueueDownload(safeUrl, agent, disposition, mime)
                        }.show()
                }
            }
        })
    }

    private fun enqueueDownload(safeUrl: String, agent: String?, disposition: String?, mime: String?) {
            try {
                val uri = Uri.parse(safeUrl)
                val filename = SafeFilename.from(URLUtil.guessFileName(safeUrl, disposition, mime))
                val request = DownloadManager.Request(uri)
                    .setTitle(filename).setMimeType(mime ?: "application/octet-stream")
                    .setDescription("Character Companion")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                request.addRequestHeader("User-Agent", agent ?: defaultAgent)
                // DownloadManager can follow redirects beyond our WebView URL policy.
                // Never export a WebView session cookie to DownloadManager headers.
                // Authenticated downloads may require opening the site in a full browser.
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                showToast(getString(R.string.download_requested))
            } catch (_: Exception) { showToast(getString(R.string.error_download)) }
    }

    /** Shared by IME insets, fullscreen video and settings; never obscure active text fields. */
    private fun updateFooterVisibility() {
        if (::footer.isInitialized) {
            footer.visibility = if (keyboardVisible || fullscreenView != null || nativeHub != null) View.GONE else View.VISIBLE
        }
    }

    private fun enterFullscreen(customView: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenView != null || destroyed) {
            callback.onCustomViewHidden()
            return
        }
        fullscreenView = customView
        fullscreenCallback = callback
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        (customView.parent as? ViewGroup)?.removeView(customView)
        container.addView(customView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        webView.visibility = View.GONE
        errorOverlay.visibility = View.GONE
        progress.visibility = View.GONE
        header.visibility = View.GONE
        footer.visibility = View.GONE
    }

    private fun leaveFullscreen() {
        val current = fullscreenView ?: return
        container.removeView(current)
        fullscreenView = null
        runCatching { fullscreenCallback?.onCustomViewHidden() }
        fullscreenCallback = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        webView.visibility = View.VISIBLE
        header.visibility = if (store.compactChrome || fullscreenView != null) View.GONE else View.VISIBLE
        updateFooterVisibility()
    }

    private fun desktopAgent(): String = defaultAgent.replace(Regex("\\([^)]*\\)"), "(X11; Linux x86_64)")
        .replace(" Mobile ", " ")

    private fun observeLoginPage(url: String?) {
        loginProtection.observePage(url)
        if (::filter.isInitialized) filter.loginBypass = loginProtection.bypass
        if (::status.isInitialized) updateStatus()
    }

    private fun toggleLoginProtection() {
        val page = if (::webView.isInitialized && activityResumed && nativeHub == null) webView.url else null
        if (LoginCompatibilityPolicy.pageKey(page) == null) {
            showToast(getString(R.string.login_compatibility_unavailable)); return
        }
        loginProtection.toggleManual(page)
        filter.loginBypass = loginProtection.bypass
        updateStatus()
        showToast(getString(if (loginProtection.bypass) R.string.login_compatibility_on
            else R.string.login_compatibility_off))
        if (::webView.isInitialized) webView.reload()
    }

    private fun filterResponse(url: String): WebResourceResponse? = emptyBlockedResponse(filter, url)

    private fun installServiceWorkerFilter() {
        // The ServiceWorker controller is process-wide. Its callback must not retain an
        // Activity, as it can outlive orientation changes and WebView recreation.
        val currentFilter = filter
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)) {
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(object : ServiceWorkerClientCompat() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
                    emptyBlockedResponse(currentFilter, request.url.toString())
            })
        }
    }

    private fun requestMediaPermission(request: PermissionRequest) {
        val resources = request.resources
        val validResources = resources.isNotEmpty() && resources.all {
            it == PermissionRequest.RESOURCE_AUDIO_CAPTURE || it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
        }
        // A hidden Activity or offline workspace must never surface a media permission prompt.
        if (!activityResumed || nativeHub != null || !store.mediaAllowed || !validResources ||
            !UrlPolicy.isTrustedMediaOrigin(request.origin.toString())) {
            request.deny(); return
        }
        pendingMedia?.deny()
        pendingMedia = request
        val microphone = resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        val camera = resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        val message = when {
            microphone && camera -> R.string.media_reason
            camera -> R.string.camera_reason
            else -> R.string.microphone_reason
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.media_permission_title)
            .setMessage(message)
            .setNegativeButton(R.string.cancel) { _, _ -> denyPendingMedia() }
            .setOnCancelListener { denyPendingMedia() }
            .setPositiveButton(R.string.confirm) { _, _ ->
                if (pendingMedia !== request || !UrlPolicy.isTrustedMediaOrigin(webView.url)) {
                    denyPendingMedia()
                    return@setPositiveButton
                }
                val needed = mutableListOf<String>()
                if (microphone && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                    needed.add(Manifest.permission.RECORD_AUDIO)
                if (camera && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                    needed.add(Manifest.permission.CAMERA)
                pendingMediaResources = resources
                if (needed.isEmpty()) grantPendingMedia() else requestPermissions(needed.toTypedArray(), MEDIA_PERMISSION)
            }.show()
    }

    private fun denyPendingMedia() {
        pendingMedia?.deny()
        pendingMedia = null
        pendingMediaResources = emptyArray()
    }
    private fun grantPendingMedia() {
        val request = pendingMedia ?: return
        val microphone = pendingMediaResources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        val camera = pendingMediaResources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        val permissionOk = (!microphone || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) &&
            (!camera || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        if (permissionOk && activityResumed && nativeHub == null && store.mediaAllowed &&
            UrlPolicy.isTrustedMediaOrigin(request.origin.toString()) &&
            UrlPolicy.isTrustedMediaOrigin(webView.url))
            request.grant(pendingMediaResources) else request.deny()
        pendingMedia = null
        pendingMediaResources = emptyArray()
    }

    private fun bindControls() {
        findViewById<View>(R.id.back).setOnClickListener { navigateBack() }
        findViewById<View>(R.id.home).setOnClickListener { openFirstPartyPage(HOME) }
        findViewById<View>(R.id.home).setOnLongClickListener { showNativeHub(); true }
        findViewById<View>(R.id.status_badge).setOnClickListener { showDiagnostics() }
        findViewById<View>(R.id.status_badge).setOnLongClickListener { toggleLoginProtection(); true }
        findViewById<View>(R.id.bookmarks).setOnClickListener { showBookmarks() }
        findViewById<View>(R.id.voice).setOnClickListener {
            voiceHandoff.launch(this, currentVoicePage())
        }
        findViewById<View>(R.id.more).setOnClickListener { showPageMenu(it) }
        findViewById<View>(R.id.error_retry).setOnClickListener {
            errorOverlay.visibility = View.GONE
            openFirstPartyPage(lastUrl)
        }
        findViewById<View>(R.id.error_open_browser).setOnClickListener {
            openBrowserTab(BrowserSessionPolicy.safeTabUrl(lastUrl))
        }
    }

    private fun showPageMenu(anchor: View) {
        val ready = ::webView.isInitialized && !destroyed && !rendererNeedsNavigation
        ShellMenu.show(this, anchor, listOf(
            ShellMenu.Action(R.string.settings) { showSettings() },
            ShellMenu.Action(R.string.release_center) { ReleaseCenter.show(this) },
            ShellMenu.Action(R.string.engine_use_gecko, BuildConfig.FLAVOR == "gecko") { returnToGecko() },
            ShellMenu.Action(R.string.forward, ready && webView.canGoForward()) { webView.goForward() },
            ShellMenu.Action(R.string.reload, ready) { webView.reload() },
            ShellMenu.Action(R.string.shell_refresh_cache, ready) {
                webView.clearCache(true); webView.reload(); showToast(getString(R.string.cache_refreshed))
            },
            ShellMenu.Action(R.string.share, ready) { sharePage() },
            ShellMenu.Action(R.string.shell_copy_link, ready) { copyPageLink() },
            ShellMenu.Action(if (store.blocking) R.string.shell_disable_shield else R.string.shell_enable_shield) {
                store.blocking = !store.blocking; applySettings(reload = true)
            },
            ShellMenu.Action(R.string.login_compatibility_button) { toggleLoginProtection() },
            ShellMenu.Action(R.string.filter_edit) { showFilterRules() },
            ShellMenu.Action(R.string.diagnostics) { showDiagnostics() },
            ShellMenu.Action(R.string.browser_mode_sign_in) { confirmBrowserSessionMode() },
            ShellMenu.Action(R.string.open_browser, ready) { openBrowserTab(BrowserSessionPolicy.safeTabUrl(webView.url)) },
            ShellMenu.Action(R.string.hub_open) { showNativeHub() }
        ))
    }

    private fun confirmIdentityNavigation(url: String) {
        if (!UrlPolicy.isIdentityUrl(url) || destroyed) return
        AlertDialog.Builder(this)
            .setTitle(R.string.external_identity_title)
            .setMessage(R.string.external_identity_message)
            .setNegativeButton(R.string.cancel, null)
            // Restart at the first-party homepage in the browser's own cookie jar.
            // Never forward an in-progress WebView OAuth URL/nonce to another session.
            .setPositiveButton(R.string.browser_mode_continue) { _, _ ->
                BrowserSessionActivity.enter(this, BrowserSessionPolicy.HOME, openNow = true)
            }
            .show()
    }

    private fun returnToGecko() {
        if (BuildConfig.FLAVOR != "gecko" || destroyed || isFinishing) return
        AlertDialog.Builder(this).setTitle(R.string.engine_use_gecko)
            .setMessage(R.string.engine_switch_warning)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val previous = store.preferWebView
                store.preferWebView = false
                try {
                    startActivity(Intent(Intent.ACTION_SEND).apply {
                        setClassName(packageName, "$packageName.GeckoActivity")
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, NativeWorkspaceNavigation.safeDestination(lastUrl))
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    })
                    finish()
                } catch (_: RuntimeException) {
                    store.preferWebView = previous
                    showToast(getString(R.string.engine_switch_failed))
                }
            }.show()
    }

    private fun confirmBrowserSessionMode() {
        if (destroyed || isFinishing) return
        AlertDialog.Builder(this).setTitle(R.string.browser_mode_title)
            .setMessage(R.string.browser_mode_explanation)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.browser_mode_continue) { _, _ ->
                BrowserSessionActivity.enter(this, BrowserSessionPolicy.HOME, openNow = true)
            }.show()
    }

    private fun confirmExternalNavigation(url: String) {
        val target = ExternalBrowserPolicy.target(url) ?: return
        val host = Uri.parse(target).host ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.external_link_title)
            .setMessage(if (target != url) getString(R.string.gecko_external_sensitive)
                else getString(R.string.external_link_message, host))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.open_browser) { _, _ -> openExternal(target) }
            .show()
    }

    /** Browser-backed mode for sign-in compatibility; it cannot inherit this WebView's cookies. */
    private fun openBrowserTab(url: String) {
        val safe = BrowserSessionPolicy.safeTabUrl(url)
        try {
            val colors = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(Color.rgb(21, 22, 30))
                .setNavigationBarColor(Color.rgb(21, 22, 30))
                .build()
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .setDefaultColorSchemeParams(colors)
                .build().launchUrl(this, Uri.parse(safe))
        } catch (_: ActivityNotFoundException) {
            openExternal(safe)
        } catch (_: SecurityException) {
            openExternal(safe)
        }
    }

    private fun openExternal(url: String) {
        val target = ExternalBrowserPolicy.target(url) ?: return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target)).addCategory(Intent.CATEGORY_BROWSABLE)
            startActivity(intent)
        } catch (_: ActivityNotFoundException) { showToast(getString(R.string.error_browser)) }
          catch (_: SecurityException) { showToast(getString(R.string.error_browser)) }
    }
    private fun copyPageLink() {
        val url = UrlPolicy.safeShareUrl(webView.url)
        if (url == null) { showToast(getString(R.string.link_unavailable)); return }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Character Companion link", url))
        showToast(getString(R.string.link_copied))
    }
    private fun showDiagnostics() {
        val packageInfo = if (Build.VERSION.SDK_INT >= 26) WebView.getCurrentWebViewPackage() else null
        val runtime = Runtime.getRuntime()
        val appMegabytes = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val text = getString(R.string.diagnostics_details,
            Build.VERSION.SDK_INT, packageInfo?.packageName ?: "unknown",
            packageInfo?.versionName ?: "unknown", appMegabytes,
            filter.ruleCount(), filter.blockedRequestCount.get(),
            if (store.blocking) getString(R.string.enabled) else getString(R.string.disabled))
        val snapshot = SupportReport.Snapshot(
            appVersion = BuildConfig.VERSION_NAME,
            androidApi = Build.VERSION.SDK_INT,
            webViewPackage = packageInfo?.packageName,
            webViewVersion = packageInfo?.versionName,
            javaHeapMb = appMegabytes.toLong(),
            filterRuleCount = filter.ruleCount(),
            importedRuleCount = importedRules.size,
            blockedRequests = filter.blockedRequestCount.get(),
            rendererRestarts = rendererRecovery.recentRestartCount(SystemClock.elapsedRealtime()),
            protectionEnabled = store.blocking,
            strictBlocking = store.strictBlocking
        )
        AlertDialog.Builder(this).setTitle(R.string.diagnostics).setMessage(text)
            .setNeutralButton(R.string.diagnostics_copy) { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Character Companion diagnostics",
                    SupportReport.format(snapshot)))
                showToast(getString(R.string.diagnostics_copied))
            }
            .setPositiveButton(R.string.confirm, null).show()
    }
    /** Manual Pixel/LineageOS compatibility check; never requests account or private data. */
    private fun showDeviceCompatibility() {
        DeviceRuntimeProbe.show(this, "legacy")
    }

    private fun showPrivacyStats() {
        val top = filter.topBlockedRules()
        val content = if (top.isEmpty()) getString(R.string.privacy_stats_empty) else
            top.joinToString("\n") { (domain, count) -> "$domain  ·  $count" }
        AlertDialog.Builder(this)
            .setTitle(R.string.privacy_stats)
            .setMessage(content)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.privacy_stats_reset) { _, _ ->
                filter.clearStats(); updateStatus()
            }
            .setPositiveButton(R.string.confirm, null).show()
    }
    private fun sharePage() {
        val url = UrlPolicy.safeShareUrl(webView.url)
        if (url == null) { showToast(getString(R.string.link_unavailable)); return }
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url)
        }, getString(R.string.share)))
    }
    private fun updateStatus() {
        val blocked = filter.blockedRequestCount.get()
        status.text = if (store.blocking && loginProtection.bypass) getString(R.string.shield_login)
            else if (store.blocking && blocked > 0) "◈ ${if (blocked > 999) "999+" else blocked.toString()}"
            else if (store.blocking) getString(R.string.shield_on) else getString(R.string.shield_off)
        status.contentDescription = if (store.blocking && loginProtection.bypass)
            getString(R.string.login_compatibility_on)
            else if (store.blocking) getString(R.string.status_blocked_accessibility, blocked)
            else getString(R.string.shield_disabled)
        status.setTextColor(if (store.blocking && !loginProtection.bypass) ACCENT else MUTED)
    }
    private fun refreshBackForward() {
        findViewById<View>(R.id.back).alpha = if (::webView.isInitialized && webView.canGoBack()) 1f else 0.55f
    }
    private fun showError(message: String) {
        errorOverlay.visibility = View.VISIBLE
        findViewById<TextView>(R.id.error_text).text = message.take(250)
        progress.visibility = View.GONE
    }
    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun applySettings(reload: Boolean) {
        if (!store.mediaAllowed) denyPendingMedia()
        // FLAG_SECURE blocks normal screenshots and recent-app thumbnails when explicitly enabled.
        // It is not a DRM mechanism and cannot prevent photos from another device.
        if (store.protectScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        filter.enabled = store.blocking
        filter.loginBypass = loginProtection.bypass
        filter.strict = store.strictBlocking
        filter.replace(store.customRules, store.excludeRules, importedRules)
        header.visibility = if (store.compactChrome || fullscreenView != null) View.GONE else View.VISIBLE
        updateFooterVisibility()
        if (::webView.isInitialized) {
            webView.setBackgroundColor(if (store.darkCanvas) Color.rgb(17, 18, 25) else Color.WHITE)
            webView.settings.textZoom = store.textZoom
            webView.settings.loadsImagesAutomatically = !store.reduceImages
            webView.settings.mediaPlaybackRequiresUserGesture = store.mediaRequiresGesture
            webView.settings.useWideViewPort = store.desktopMode
            webView.settings.loadWithOverviewMode = store.desktopMode
            webView.settings.userAgentString = if (store.desktopMode) desktopAgent() else defaultAgent
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, store.cookies)
            if (reload) {
                deferredWebReload.markPending()
                flushDeferredWebReload()
            }
        }
        updateStatus()
        refreshBackForward()
    }

    /** A settings toggle in the offline workspace must never silently fetch a webpage. */
    private fun flushDeferredWebReload() {
        if (!::webView.isInitialized || destroyed || rendererNeedsNavigation) return
        if (!deferredWebReload.consumeIfVisible(activityResumed, nativeHub != null)) return
        if (NativeWorkspaceNavigation.needsInitialLoad(webView.url)) {
            webView.loadUrl(NativeWorkspaceNavigation.safeDestination(lastUrl))
        } else {
            webView.reload()
        }
    }

    /** Save a manually entered link without loading Character.AI or exposing credentials. */
    private fun createOfflineBookmark(onSaved: () -> Unit) {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
            setBackgroundColor(Color.rgb(21, 22, 30))
        }
        val titleInput = EditText(this).apply {
            hint = getString(R.string.hub_bookmark_name_hint)
            setTextColor(TEXT); setHintTextColor(MUTED)
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(90))
        }
        val urlInput = EditText(this).apply {
            hint = getString(R.string.hub_bookmark_url_hint)
            setTextColor(TEXT); setHintTextColor(MUTED)
            isSingleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            filters = arrayOf(InputFilter.LengthFilter(4096))
        }
        fields.addView(titleInput)
        fields.addView(urlInput)
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.hub_bookmark_manual_add)
            .setView(fields)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.bookmark_add, null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val normalized = OfflineBookmarkEditor.normalize(
                titleInput.text.toString(), urlInput.text.toString())
            if (normalized == null || !store.addBookmark(normalized.first, normalized.second)) {
                urlInput.error = getString(R.string.hub_bookmark_invalid)
            } else {
                dialog.dismiss()
                onSaved()
                showToast(getString(R.string.bookmark_saved))
            }
        }
    }

    /** Independent native offline home. Does not request Character.AI APIs or load the site. */
    private fun showNativeHub() {
        voiceHandoff.invalidate()
        if (destroyed || nativeHub != null) return
        val panel = ScrollView(this).apply {
            setBackgroundColor(Color.rgb(17, 18, 25))
            isFillViewport = true
        }
        val layout = settingsColumn()
        panel.addView(layout)
        val heading = TextView(this).apply {
            text = getString(R.string.hub_title)
            textSize = 21f
            setTextColor(ACCENT)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(10))
        }
        layout.addView(heading)
        layout.addView(infoLabel(getString(R.string.hub_description)))
        // Resume the existing renderer; a reload would discard a live chat draft.
        addButton(layout, getString(R.string.hub_continue)) { closeNativeHub() }
        addButton(layout, getString(R.string.browser_mode_sign_in)) { confirmBrowserSessionMode() }
        // The manual editor works even before WebView's first navigation.
        var refreshNativeBookmarks: () -> Unit = {}
        addButton(layout, getString(R.string.hub_bookmark_manual_add)) {
            createOfflineBookmark { refreshNativeBookmarks() }
        }
        addButton(layout, getString(R.string.voice_title)) {
            startActivity(Intent(this, VoiceDraftActivity::class.java))
        }
        addButton(layout, getString(R.string.settings)) { showSettings() }
        layout.addView(sectionLabel(getString(R.string.hub_library)))
        val search = EditText(this).apply {
            hint = getString(R.string.hub_search_hint)
            setTextColor(TEXT)
            setHintTextColor(MUTED)
            isSingleLine = true
            filters = arrayOf(InputFilter.LengthFilter(80))
        }
        layout.addView(search)
        val orderLabels = intArrayOf(R.string.library_order_saved, R.string.library_order_newest, R.string.library_order_title)
        val sort = Button(this).apply {
            isAllCaps = false
            setTextColor(TEXT)
            setBackgroundResource(R.drawable.settings_action)
        }
        layout.addView(sort)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        layout.addView(list)
        var index = NativeLibrarySearch.Index(store.listBookmarks(), store.pinnedBookmarks())
        var rendered: List<Pair<String, String>>? = null
        var renderedPins = emptySet<String>()
        fun refresh(query: String) {
            val saved = index.filter(query, order = store.libraryOrder)
            val pins = saved.map { it.second }.filter(index::isPinned).toSet()
            if (saved == rendered && pins == renderedPins) return
            rendered = saved
            renderedPins = pins
            list.removeAllViews()
            if (saved.isEmpty()) {
                list.addView(infoLabel(getString(R.string.hub_empty)))
                return
            }
            for ((title, url) in saved) {
                val item = Button(this).apply {
                    text = if (url in pins) getString(R.string.library_pinned_title, title) else title
                    isAllCaps = false
                    textSize = 14f
                    setTextColor(TEXT)
                    setBackgroundResource(R.drawable.settings_action)
                    gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    minHeight = dp(52)
                    setOnClickListener { openFirstPartyPage(url) }
                    contentDescription = getString(R.string.hub_bookmark_options_hint, text)
                    setOnLongClickListener {
                        showNativeBookmarkActions(title, url) { refreshNativeBookmarks() }
                        true
                    }
                }
                list.addView(item, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(6)
                    })
            }
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refresh(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        fun updateSortLabel() {
            sort.text = getString(R.string.library_order_format, getString(orderLabels[store.libraryOrder.ordinal]))
        }
        updateSortLabel()
        sort.setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.library_order)
                .setSingleChoiceItems(orderLabels.map { getString(it) }.toTypedArray(), store.libraryOrder.ordinal) { dialog, choice ->
                    store.libraryOrder = NativeLibrarySearch.Order.entries[choice]
                    updateSortLabel()
                    refresh(search.text.toString())
                    dialog.dismiss()
                }.setNegativeButton(R.string.cancel, null).show()
        }
        refreshNativeBookmarks = {
            index = NativeLibrarySearch.Index(store.listBookmarks(), store.pinnedBookmarks())
            refresh(search.text.toString())
        }
        refresh("")
        addButton(layout, getString(R.string.close)) { closeNativeHub() }
        nativeHub = panel
        header.visibility = View.GONE
        updateFooterVisibility()
        // A hidden web page cannot keep a pending microphone/camera prompt alive.
        denyPendingMedia()
        // No WebView exists on cold native startup, so nothing Chromium-related is loaded.
        loginProtection.setVisible(false)
        filter.loginBypass = false
        if (::webView.isInitialized) webView.onPause()
        container.addView(panel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        panel.bringToFront()
    }

    /** First-party navigation always leaves the native workspace before loading WebView. */
    private fun openFirstPartyPage(rawUrl: String) {
        voiceHandoff.invalidate()
        val safeUrl = NativeWorkspaceNavigation.safeDestination(rawUrl)
        lastUrl = safeUrl
        observeLoginPage(safeUrl)
        deferredWebReload.consumedByNavigation()
        if (!ensureWebView()) {
            closeNativeHub(loadIfBlank = false)
            return
        }
        rendererNeedsNavigation = false
        closeNativeHub(loadIfBlank = false)
        webView.loadUrl(safeUrl)
    }

    private fun closeNativeHub(loadIfBlank: Boolean = true) {
        nativeHub?.let {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                ?.hideSoftInputFromWindow(it.windowToken, 0)
            container.removeView(it)
        }
        nativeHub = null
        header.visibility = if (store.compactChrome || fullscreenView != null) View.GONE else View.VISIBLE
        updateFooterVisibility()
        if (!destroyed && loadIfBlank && !::webView.isInitialized && !ensureWebView()) return
        if (!destroyed && ::webView.isInitialized) {
            // Closing the native launch screen must never reveal an uninitialized blank WebView.
            if (loadIfBlank && !rendererNeedsNavigation && NativeWorkspaceNavigation.needsInitialLoad(webView.url)) {
                deferredWebReload.consumedByNavigation()
                webView.loadUrl(NativeWorkspaceNavigation.safeDestination(lastUrl))
            }
            syncRendererLifecycle()
            flushDeferredWebReload()
        }
    }

    private fun syncRendererLifecycle() {
        val visible = !destroyed && ::webView.isInitialized &&
            NativeWorkspaceNavigation.shouldResumeRenderer(activityResumed, nativeHub != null)
        loginProtection.setVisible(visible)
        if (::filter.isInitialized) filter.loginBypass = loginProtection.bypass
        if (!::webView.isInitialized || destroyed) return
        if (visible) {
            observeLoginPage(webView.url)
            webView.onResume()
        } else {
            webView.onPause()
        }
    }

    private fun showNativeBookmarkActions(title: String, url: String, onChanged: () -> Unit) {
        val pinned = url in store.pinnedBookmarks()
        val options = arrayOf(getString(R.string.hub_bookmark_open),
            getString(R.string.hub_bookmark_browser), getString(R.string.hub_bookmark_rename),
            getString(R.string.hub_bookmark_delete),
            getString(if (pinned) R.string.library_unpin else R.string.library_pin))
        AlertDialog.Builder(this).setTitle(title).setItems(options) { _, selected ->
            when (selected) {
                0 -> openFirstPartyPage(url)
                1 -> openBrowserTab(url)
                2 -> {
                    val input = EditText(this).apply {
                        setText(title)
                        setTextColor(TEXT); setHintTextColor(MUTED)
                        isSingleLine = true
                        filters = arrayOf(InputFilter.LengthFilter(90))
                    }
                    AlertDialog.Builder(this).setTitle(R.string.hub_bookmark_rename)
                        .setView(input)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.apply) { _, _ ->
                            val normalized = OfflineBookmarkEditor.normalize(input.text.toString(), url)
                            if (normalized != null && store.addBookmark(normalized.first, normalized.second)) {
                                onChanged()
                                showToast(getString(R.string.bookmark_saved))
                            }
                        }.show()
                }
                3 -> AlertDialog.Builder(this).setTitle(R.string.hub_bookmark_delete)
                    .setMessage(R.string.hub_bookmark_confirm_delete)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        store.removeBookmark(url)
                        onChanged()
                        showToast(getString(R.string.bookmark_removed))
                    }.show()
                4 -> if (store.setBookmarkPinned(url, !pinned)) onChanged()
            }
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showBookmarks() {
        val url = if (::webView.isInitialized) UrlPolicy.sanitizeBookmark(webView.url) else null
        val saved = store.listBookmarks()
        val options = mutableListOf(getString(R.string.bookmark_add), getString(R.string.bookmark_remove))
        options.addAll(saved.map { "☆ ${it.first}" })
        AlertDialog.Builder(this).setTitle(getString(R.string.bookmarks))
            .setItems(options.toTypedArray()) { _, selected ->
                when (selected) {
                    0 -> { if (url == null) showToast(getString(R.string.hub_bookmark_invalid))
                        else if (store.addBookmark(webView.title ?: "Character.AI", url)) showToast(getString(R.string.bookmark_saved)) }
                    1 -> { if (url == null) showToast(getString(R.string.hub_bookmark_invalid))
                        else { store.removeBookmark(url); showToast(getString(R.string.bookmark_removed)) } }
                    else -> saved.getOrNull(selected - 2)?.second?.let(::openFirstPartyPage)
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showFilterRules() {
        val scroll = ScrollView(this)
        val layout = settingsColumn()
        scroll.addView(layout)
        layout.addView(infoLabel(getString(R.string.filter_instructions)))
        val custom = EditText(this).apply {
            setText(store.customRules); setTextColor(TEXT); setHintTextColor(MUTED)
            hint = "example.org"; minLines = 4; maxLines = 8
        }
        layout.addView(sectionLabel(getString(R.string.filter_block_domains))); layout.addView(custom)
        val exclusions = EditText(this).apply {
            setText(store.excludeRules); setTextColor(TEXT); setHintTextColor(MUTED)
            hint = "needed-cdn.example.com"; minLines = 3; maxLines = 6
        }
        layout.addView(sectionLabel(getString(R.string.filter_allow_domains))); layout.addView(exclusions)
        AlertDialog.Builder(this).setTitle(getString(R.string.filter_title, filter.ruleCount()))
            .setView(scroll).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.apply) { _, _ ->
                store.customRules = custom.text.toString()
                store.excludeRules = exclusions.text.toString()
                applySettings(reload = true)
            }.show()
    }

    private fun showProfiles() {
        val titles = arrayOf(
            getString(R.string.profile_balanced), getString(R.string.profile_compatibility),
            getString(R.string.profile_private), getString(R.string.profile_data_saver)
        )
        val profiles = BrowserProfile.entries
        AlertDialog.Builder(this).setTitle(R.string.profile_title)
            .setSingleChoiceItems(titles, -1) { dialog, position ->
                store.applyProfile(profiles[position])
                applySettings(reload = true)
                showToast(getString(R.string.profile_applied, titles[position]))
                dialog.dismiss()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showSettings() {
        val categories = intArrayOf(R.string.section_profiles, R.string.section_browser_session, R.string.section_privacy, R.string.section_appearance, R.string.section_data, R.string.section_about)
        AlertDialog.Builder(this).setTitle(R.string.settings)
            .setItems(categories.map { getString(it) }.toTypedArray()) { _, index ->
                showSettingsSection(categories[index])
            }.setNegativeButton(R.string.close, null).show()
    }

    private fun showSettingsSection(section: Int) {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(25, 28, 37)) }
        val layout = settingsColumn()
        scroll.addView(layout)
        if (section == R.string.section_profiles) {
            addButton(layout, getString(R.string.profile_title)) { showProfiles() }
            layout.addView(infoLabel(getString(R.string.profile_desc)))
            addButton(layout, getString(R.string.hub_open)) { showNativeHub() }
            addToggle(layout, getString(R.string.hub_on_launch), getString(R.string.hub_on_launch_desc), store.startInHub) {
                store.startInHub = it
            }
        }
        if (section == R.string.section_browser_session) {
            addButton(layout, getString(R.string.browser_mode_sign_in)) { confirmBrowserSessionMode() }
            addButton(layout, getString(R.string.browser_session_open)) {
                openBrowserTab(BrowserSessionPolicy.HOME)
            }
            layout.addView(infoLabel(getString(R.string.browser_session_desc)))
        }
        if (section == R.string.section_privacy) {
            addToggle(layout, getString(R.string.toggle_protect_screen),
                getString(R.string.toggle_protect_screen_desc), store.protectScreen) {
                store.protectScreen = it; applySettings(reload = false)
            }
            addToggle(layout, getString(R.string.toggle_filter), getString(R.string.toggle_filter_desc), store.blocking) {
                store.blocking = it; applySettings(reload = true)
            }
            addToggle(layout, getString(R.string.toggle_strict), getString(R.string.toggle_strict_desc), store.strictBlocking) {
                store.strictBlocking = it; applySettings(reload = true)
            }
            addToggle(layout, getString(R.string.toggle_cookies), getString(R.string.toggle_cookies_desc), store.cookies) {
                store.cookies = it; applySettings(reload = true)
            }
            addToggle(layout, getString(R.string.toggle_media), getString(R.string.toggle_media_desc), store.mediaAllowed) {
                store.mediaAllowed = it
            }
            addButton(layout, getString(R.string.login_compatibility_button)) { toggleLoginProtection() }
            layout.addView(infoLabel(getString(R.string.login_compatibility_info)))
            addButton(layout, getString(R.string.filter_edit)) { showFilterRules() }
            addButton(layout, getString(R.string.import_filter_list)) { chooseFilterList() }
            if (importedRules.size > 0) {
                addButton(layout, getString(R.string.export_filter_list)) { exportFilterList() }
            }
            layout.addView(infoLabel(getString(R.string.filter_import_info, importedRules.size)))
            if (importedRules.size > 0) {
                addButton(layout, getString(R.string.clear_imported_filter)) { confirmClearImportedList() }
            }
            addButton(layout, getString(R.string.diagnostics)) { showDiagnostics() }
            addButton(layout, getString(R.string.device_compat_title)) { showDeviceCompatibility() }
            addButton(layout, getString(R.string.privacy_stats)) { showPrivacyStats() }
        }
        if (section == R.string.section_appearance) {
            addToggle(layout, getString(R.string.toggle_compact), getString(R.string.toggle_compact_desc), store.compactChrome) {
                store.compactChrome = it; applySettings(reload = false)
            }
            addToggle(layout, getString(R.string.toggle_dark), getString(R.string.toggle_dark_desc), store.darkCanvas) {
                store.darkCanvas = it; applySettings(reload = false)
            }
            addToggle(layout, getString(R.string.toggle_images), getString(R.string.toggle_images_desc), store.reduceImages) {
                store.reduceImages = it; applySettings(reload = true)
            }
            addToggle(layout, getString(R.string.toggle_desktop), getString(R.string.toggle_desktop_desc), store.desktopMode) {
                store.desktopMode = it; applySettings(reload = true)
            }
            addToggle(layout, getString(R.string.toggle_gesture), getString(R.string.toggle_gesture_desc), store.mediaRequiresGesture) {
                store.mediaRequiresGesture = it; applySettings(reload = false)
            }
            addToggle(layout, getString(R.string.toggle_resume), getString(R.string.toggle_resume_desc), store.resumePage) {
                store.resumePage = it
                if (!it) store.lastVisited = ""
            }
            layout.addView(infoLabel(getString(R.string.text_scaling, store.textZoom)))
            val zoomValue = TextView(this).apply { text = "${store.textZoom}%"; setTextColor(ACCENT) }
            layout.addView(zoomValue)
            val zoomBar = SeekBar(this).apply {
                max = 100; this.progress = store.textZoom - 75
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        zoomValue.text = "${progress + 75}%"
                        if (fromUser) { store.textZoom = progress + 75; applySettings(reload = false) }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }
            layout.addView(zoomBar)
        }
        if (section == R.string.section_data) {
            addButton(layout, getString(R.string.export_settings)) { exportSettings() }
            addButton(layout, getString(R.string.import_settings)) { importSettings() }
            addButton(layout, getString(R.string.clear_cache)) { confirmClearCache() }
            addButton(layout, getString(R.string.clear_data)) { confirmClearSiteData() }
            addButton(layout, getString(R.string.voice_title)) {
                startActivity(Intent(this, VoiceDraftActivity::class.java))
            }
        }
        if (section == R.string.section_about) {
            layout.addView(infoLabel(getString(R.string.about_details, BuildConfig.VERSION_NAME,
                filter.blockedRequestCount.get())))
        }
        AlertDialog.Builder(this).setTitle(section).setView(scroll)
            .setNeutralButton(R.string.settings) { _, _ -> showSettings() }
            .setPositiveButton(R.string.close, null).show()
    }

    private fun settingsColumn(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(22))
        setBackgroundColor(Color.rgb(25,28,37))
    }
    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text; setTextColor(ACCENT); textSize = 12f
        typeface = Typeface.DEFAULT_BOLD; letterSpacing = .025f
        setPadding(0, dp(20), 0, dp(9))
    }
    private fun infoLabel(text: String): TextView = TextView(this).apply {
        this.text = text; setTextColor(MUTED); textSize = 13f
        setPadding(0, dp(6), 0, dp(8))
    }
    private fun addToggle(parent: LinearLayout, name: String, description: String, value: Boolean, changed: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply { text = name; setTextColor(TEXT); textSize = 15f; setPadding(0, dp(8), 0, 0) })
        texts.addView(infoLabel(description))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Switch(this).apply {
            isChecked = value
            contentDescription = "$name. $description"
            setOnCheckedChangeListener { _, enabled -> changed(enabled) }
        })
        parent.addView(row)
    }
    private fun addButton(parent: LinearLayout, title: String, callback: () -> Unit) {
        parent.addView(Button(this).apply {
            text = title; isAllCaps = false; setTextColor(TEXT)
            setBackgroundResource(R.drawable.settings_action)
            minHeight = dp(52)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnClickListener { callback() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun confirmClearCache() {
        if (!::webView.isInitialized) { showToast(getString(R.string.shell_no_page)); return }
        AlertDialog.Builder(this).setMessage(getString(R.string.cache_clear_message))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ -> webView.clearCache(true); showToast(getString(R.string.cache_cleared)) }
            .show()
    }
    private fun confirmClearSiteData() {
        if (!::webView.isInitialized) { showToast(getString(R.string.shell_no_page)); return }
        AlertDialog.Builder(this).setTitle(getString(R.string.data_clear_title))
            .setMessage(getString(R.string.data_clear_message))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                // Cookie removal is asynchronous. Do not reload before it completes.
                CookieManager.getInstance().removeAllCookies {
                    runOnUiThread {
                        CookieManager.getInstance().flush()
                        WebStorage.getInstance().deleteAllData()
                        webView.clearCache(true); webView.clearHistory(); webView.clearFormData()
                        store.lastVisited = ""
                        webView.loadUrl(HOME)
                        showToast(getString(R.string.data_cleared))
                    }
                }
            }.show()
    }
    private fun chooseFilterList() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/octet-stream"))
        }, IMPORT_FILTER_LIST)
    }

    /** Parse a user-selected file off the UI thread. The old list stays in effect until confirmed. */
    private fun importFilterList(uri: Uri) {
        Thread({
            val result = runCatching {
                val text = contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).use { reader ->
                        BoundedText.read(reader, RuleListParser.MAX_CHARACTERS)
                    }
                } ?: throw IllegalArgumentException("empty or oversized list")
                RuleListParser.parse(text) ?: throw IllegalArgumentException("unsupported rule list")
            }
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                result.onSuccess { parsed ->
                    AlertDialog.Builder(this)
                        .setTitle(R.string.filter_import_preview_title)
                        .setMessage(getString(R.string.filter_import_preview,
                            parsed.blocked.size, parsed.allowed.size, parsed.unsupported))
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.apply) { _, _ -> saveImportedRules(parsed) }
                        .show()
                }.onFailure { showToast(getString(R.string.filter_import_failure)) }
            }
        }, "manual-filter-parse").start()
    }

    /** Save only after explicit approval; AtomicFile preserves the old list if writing fails. */
    private fun saveImportedRules(parsed: RuleListParser.Rules) {
        Thread({
            val saved = runCatching { importedRuleStore.save(parsed) }.isSuccess
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                if (saved) {
                    importedRules = parsed
                    applySettings(reload = true)
                    showToast(getString(R.string.filter_import_success,
                        parsed.size, parsed.unsupported))
                } else showToast(getString(R.string.filter_import_failure))
            }
        }, "manual-filter-save").start()
    }

    private fun confirmClearImportedList() {
        AlertDialog.Builder(this).setMessage(R.string.filter_remove_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                importedRuleStore.clear()
                importedRules = RuleListParser.Rules()
                applySettings(reload = true)
                showToast(getString(R.string.filter_removed))
            }.show()
    }

    private fun exportSettings() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "character-companion-settings.json")
        }, EXPORT_SETTINGS)
    }
    private fun exportFilterList() {
        if (importedRules.size == 0) return
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "character-companion-filter-domains.txt")
        }, EXPORT_FILTER_LIST)
    }

    /** Only normalized, user-imported domain rules leave private storage by explicit action. */
    private fun writeFilterList(uri: Uri) {
        val snapshot = importedRules
        Thread({
            val saved = runCatching {
                val output = contentResolver.openOutputStream(uri, "w")
                    ?: throw java.io.IOException("Document provider returned no output stream")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(snapshot.canonicalText()) }
            }.isSuccess
            runOnUiThread {
                if (!destroyed) showToast(getString(
                    if (saved) R.string.filter_export_success else R.string.filter_export_failure))
            }
        }, "manual-filter-export").start()
    }

    private fun importSettings() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }, IMPORT_SETTINGS)
    }

    /** User-initiated SAF backup I/O must not block the UI thread on slow providers. */
    private fun writeSettingsBackup(uri: Uri) {
        // Serialize a bounded snapshot before background I/O so further UI changes stay independent.
        val snapshot = runCatching { store.exportJson() }.getOrNull()
        if (snapshot == null) { showToast(getString(R.string.export_failure)); return }
        Thread({
            val success = runCatching {
                val output = contentResolver.openOutputStream(uri, "w")
                    ?: throw java.io.IOException("Document provider returned no output stream")
                output.bufferedWriter(Charsets.UTF_8).use { it.write(snapshot) }
            }.isSuccess
            runOnUiThread {
                if (!destroyed) showToast(getString(if (success) R.string.export_success else R.string.export_failure))
            }
        }, "manual-backup-export").start()
    }

    private fun readSettingsBackup(uri: Uri) {
        Thread({
            val result = runCatching {
                val contents = contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                    BoundedText.read(it, 200_000)
                }
                contents != null && store.importJson(contents)
            }
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                if (result.getOrDefault(false)) {
                    applySettings(reload = true)
                    showToast(getString(R.string.import_success))
                } else {
                    showToast(getString(if (result.isFailure) R.string.import_failure else R.string.import_unsupported))
                }
            }
        }, "manual-backup-import").start()
    }

    private fun navigateBack() {
        when {
            nativeHub != null -> closeNativeHub()
            fullscreenView != null -> leaveFullscreen()
            ::webView.isInitialized && webView.canGoBack() -> webView.goBack()
            else -> finish()
        }
    }

    @Deprecated("Compatibility with Android framework navigation below API 33")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) navigateBack()
        else super.onBackPressed()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        // No WebView.saveState(): do not place browsing history or auth URLs in a Bundle.
        super.onSaveInstanceState(outState)
    }
    override fun onPause() {
        activityResumed = false
        super.onPause()
        syncRendererLifecycle()
        // CookieManager access itself may start WebView: keep cold native startup cold.
        if (::webView.isInitialized && !rendererDisposed) CookieManager.getInstance().flush()
    }
    override fun onResume() {
        super.onResume()
        activityResumed = true
        syncRendererLifecycle()
        flushDeferredWebReload()
        deliverVoiceDraft()
    }
    override fun onStop() {
        ForegroundUpdateUi.cancel(this)
        // Do not revoke while an Android permission dialog merely pauses the Activity.
        // Revoke a pending WebRTC request when the application actually becomes hidden.
        denyPendingMedia()
        super.onStop()
    }
    private fun currentVoicePage(): String? =
        if (!destroyed && ::webView.isInitialized && nativeHub == null && fullscreenView == null &&
            ::errorOverlay.isInitialized && errorOverlay.visibility != View.VISIBLE) webView.url else null

    private fun deliverVoiceDraft() {
        val page = currentVoicePage()
        voiceHandoff.deliver(this, if (page != null) webView else null, page)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && activityResumed) deliverVoiceDraft()
    }

    override fun onDestroy() {
        ForegroundUpdateUi.cancel(this)
        voiceHandoff.clear()
        destroyed = true
        loginProtection.setVisible(false)
        // The process-wide service-worker hook may retain the filter after Activity destruction.
        if (::filter.isInitialized) filter.loginBypass = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            modernBackCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            modernBackCallback = null
        }
        leaveFullscreen()
        closeNativeHub()
        denyPendingMedia()
        chooser?.onReceiveValue(null); chooser = null
        if (::webView.isInitialized && !rendererDisposed) {
            container.removeView(webView)
            webView.destroy()
            rendererDisposed = true
        }
        super.onDestroy()
    }

    @Deprecated("Legacy file chooser and Storage Access Framework results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            VoiceComposerHandoff.REQUEST -> {
                voiceHandoff.receive(resultCode, data)
                if (activityResumed) deliverVoiceDraft()
            }
            FILE_PICKER -> {
                chooser?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
                chooser = null
            }
            EXPORT_SETTINGS -> if (resultCode == RESULT_OK) data?.data?.let(::writeSettingsBackup)
            IMPORT_FILTER_LIST -> if (resultCode == RESULT_OK) data?.data?.let { uri ->
                importFilterList(uri)
            }
            EXPORT_FILTER_LIST -> if (resultCode == RESULT_OK) data?.data?.let { uri ->
                writeFilterList(uri)
            }
            IMPORT_SETTINGS -> if (resultCode == RESULT_OK) data?.data?.let(::readSettingsBackup)
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MEDIA_PERMISSION) grantPendingMedia()
    }
}

/** Standalone: the process-wide service-worker hook must not capture MainActivity. */
private fun emptyBlockedResponse(filter: FilterEngine, url: String): WebResourceResponse? {
    if (!filter.shouldBlock(url)) return null
    return WebResourceResponse("text/plain", "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0)))
}
