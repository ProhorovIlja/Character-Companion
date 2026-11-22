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
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.os.Environment
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

/** Native Android shell. Browsing is first-party; no private API, HTML scraping or JS credential bridge. */
class MainActivity : Activity() {
    companion object {
        private const val HOME = "https://character.ai/"
        private const val FILE_PICKER = 701
        private const val MEDIA_PERMISSION = 702
        private const val EXPORT_SETTINGS = 703
        private const val IMPORT_SETTINGS = 704
        private val TEXT = Color.rgb(235, 225, 211)
        private val MUTED = Color.rgb(177, 175, 189)
        private val ACCENT = Color.rgb(201, 169, 113)
    }

    private lateinit var webView: WebView
    private lateinit var store: SettingsStore
    private lateinit var filter: FilterEngine
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(false)
        store = SettingsStore(this)
        val base = assets.open("blocked-hosts.txt").bufferedReader().use { it.readLines() }
        val strictRules = assets.open("strict-hosts.txt").bufferedReader().use { it.readLines() }
        filter = FilterEngine(base, strictRules).also {
            it.enabled = store.blocking; it.strict = store.strictBlocking
            it.replace(store.customRules, store.excludeRules)
        }
        setContentView(R.layout.activity_main)
        container = findViewById(R.id.web_container)
        errorOverlay = findViewById(R.id.error_overlay)
        progress = findViewById(R.id.loading_progress)
        status = findViewById(R.id.status_badge)
        titleLabel = findViewById(R.id.page_title)
        footer = findViewById(R.id.chrome_footer)
        header = findViewById(R.id.chrome_header)
        webView = findViewById(R.id.web_view)
        defaultAgent = WebSettings.getDefaultUserAgent(this)
        installServiceWorkerFilter()
        configureWebView(webView)
        bindControls()
        applySettings(reload = false)
        // Android 15/16 enforce edge-to-edge. Keep native controls outside system bars.
        val root = findViewById<View>(R.id.root)
        root.setOnApplyWindowInsetsListener { target, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                target.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                target.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        root.requestApplyInsets()
        // Respect the IME by hiding footer while typing in the website.
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val visible = android.graphics.Rect().also { root.getWindowVisibleDisplayFrame(it) }
            val keyboardVisible = root.rootView.height - visible.bottom > 180 * resources.displayMetrics.density
            footer.visibility = if (keyboardVisible || fullscreenView != null) View.GONE else View.VISIBLE
        }
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            lastUrl = if (store.resumePage) store.lastVisited.ifBlank { HOME } else HOME
            webView.loadUrl(lastUrl)
        }
        else lastUrl = webView.url?.takeIf(UrlPolicy::isInternal) ?: HOME
    }

    private fun configureWebView(view: WebView) {
        view.setBackgroundColor(if (store.darkCanvas) Color.rgb(17, 18, 25) else Color.WHITE)
        view.settings.apply {
            javaScriptEnabled = true // Required by Character.AI
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = true // Platform document picker only
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
                if (UrlPolicy.isInternal(url)) return false
                // Never launch an external application from a background redirect.
                if (request.hasGesture()) confirmExternalNavigation(url)
                return true
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                if (request.isForMainFrame) return null
                return filterResponse(request.url.toString())
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                // WebRTC grants are scoped to the page that requested them, not its successor.
                denyPendingMedia()
                errorOverlay.visibility = View.GONE
                lastUrl = UrlPolicy.sanitizeBookmark(url) ?: HOME
                status.setText(R.string.status_loading)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                titleLabel.text = view?.title?.take(65)?.ifBlank { "character.ai" } ?: "character.ai"
                if (store.resumePage && UrlPolicy.safeBookmark(url)) store.lastVisited = url.orEmpty()
                updateStatus()
                CookieManager.getInstance().flush()
                refreshBackForward()
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && !destroyed) {
                    showError(error.description?.toString() ?: getString(R.string.error_loading))
                }
            }
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: android.net.http.SslError?) {
                handler.cancel() // Never ignore a certificate error.
                // A third-party image/ad TLS error must not replace a healthy main page.
                if (error != null && UrlPolicy.isInternal(error.url) &&
                    UrlPolicy.isInternal(view?.url)) showError(getString(R.string.error_tls))
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // Old WebView is unusable. Replace it; don't crash the whole application.
                if (destroyed) return true
                denyPendingMedia()
                leaveFullscreen()
                chooser?.onReceiveValue(null)
                chooser = null
                val index = container.indexOfChild(view)
                container.removeView(view)
                view.destroy()
                webView = WebView(this@MainActivity)
                container.addView(webView, index.coerceAtLeast(0), FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                configureWebView(webView)
                showToast(getString(R.string.browser_restarted))
                webView.loadUrl(lastUrl.takeIf(UrlPolicy::isInternal) ?: HOME)
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
                requestMediaPermission(request)
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
            if (!UrlPolicy.isHttps(url)) return@DownloadListener
            val safeUrl = url ?: return@DownloadListener
            val name = URLUtil.guessFileName(safeUrl, disposition, mime)
                .replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)
            val host = Uri.parse(safeUrl).host ?: return@DownloadListener
            AlertDialog.Builder(this)
                .setTitle(R.string.download_confirm_title)
                .setMessage(getString(R.string.download_confirm_message, name, host))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    enqueueDownload(safeUrl, agent, disposition, mime)
                }.show()
        })
    }

    private fun enqueueDownload(safeUrl: String, agent: String?, disposition: String?, mime: String?) {
            try {
                val uri = Uri.parse(safeUrl)
                val filename = URLUtil.guessFileName(safeUrl, disposition, mime)
                    .replace(Regex("[\\\\/:*?\"<>|]"), "_").take(100)
                val request = DownloadManager.Request(uri)
                    .setTitle(filename).setMimeType(mime ?: "application/octet-stream")
                    .setDescription("Character Companion")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
                request.addRequestHeader("User-Agent", agent ?: defaultAgent)
                // Prevent session cookies from leaking to third-party download hosts.
                if (UrlPolicy.isTrustedMediaOrigin(safeUrl)) {
                    CookieManager.getInstance().getCookie(safeUrl)?.let { request.addRequestHeader("Cookie", it) }
                }
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                showToast(getString(R.string.download_requested))
            } catch (_: Exception) { showToast(getString(R.string.error_download)) }
    }

    private fun enterFullscreen(customView: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenView != null || destroyed) {
            callback.onCustomViewHidden()
            return
        }
        fullscreenView = customView
        fullscreenCallback = callback
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
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
        footer.visibility = View.VISIBLE
    }

    private fun desktopAgent(): String = defaultAgent.replace(Regex("\\([^)]*\\)"), "(X11; Linux x86_64)")
        .replace(" Mobile ", " ")

    private fun filterResponse(url: String): WebResourceResponse? {
        if (!filter.shouldBlock(url)) return null
        return WebResourceResponse("text/plain", "UTF-8", 200, "OK", mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(ByteArray(0)))
    }

    private fun installServiceWorkerFilter() {
        // Service workers do not necessarily pass through WebViewClient interceptors.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)) {
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(object : ServiceWorkerClientCompat() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? =
                    filterResponse(request.url.toString())
            })
        }
    }

    private fun requestMediaPermission(request: PermissionRequest) {
        val resources = request.resources
        val validResources = resources.isNotEmpty() && resources.all {
            it == PermissionRequest.RESOURCE_AUDIO_CAPTURE || it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
        }
        if (!store.mediaAllowed || !validResources || !UrlPolicy.isTrustedMediaOrigin(request.origin.toString())) {
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
            .setTitle("Character.AI · permission")
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
        if (permissionOk && store.mediaAllowed &&
            UrlPolicy.isTrustedMediaOrigin(request.origin.toString()) &&
            UrlPolicy.isTrustedMediaOrigin(webView.url))
            request.grant(pendingMediaResources) else request.deny()
        pendingMedia = null
        pendingMediaResources = emptyArray()
    }

    private fun bindControls() {
        findViewById<View>(R.id.back).setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        findViewById<View>(R.id.forward).setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        findViewById<View>(R.id.reload).setOnClickListener { webView.reload() }
        findViewById<View>(R.id.home).setOnClickListener { webView.loadUrl(HOME) }
        findViewById<View>(R.id.open_browser).setOnClickListener {
            openExternal(UrlPolicy.safeShareUrl(webView.url) ?: HOME)
        }
        findViewById<View>(R.id.share).setOnClickListener { sharePage() }
        findViewById<View>(R.id.share).setOnLongClickListener { copyPageLink(); true }
        findViewById<View>(R.id.reload).setOnLongClickListener {
            webView.clearCache(true); webView.reload(); showToast(getString(R.string.cache_refreshed)); true
        }
        findViewById<View>(R.id.status_badge).setOnClickListener { showDiagnostics() }
        findViewById<View>(R.id.bookmarks).setOnClickListener { showBookmarks() }
        findViewById<View>(R.id.filter).setOnClickListener {
            store.blocking = !store.blocking
            applySettings(reload = true)
            showToast(if (store.blocking) getString(R.string.shield_enabled) else getString(R.string.shield_disabled))
        }
        findViewById<View>(R.id.filter).setOnLongClickListener { showFilterRules(); true }
        findViewById<View>(R.id.settings).setOnClickListener { showSettings() }
        findViewById<View>(R.id.error_retry).setOnClickListener { errorOverlay.visibility = View.GONE; webView.loadUrl(lastUrl) }
    }

    private fun confirmExternalNavigation(url: String) {
        if (!UrlPolicy.isHttps(url)) return
        val host = Uri.parse(url).host ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.external_link_title)
            .setMessage(getString(R.string.external_link_message, host))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.open_browser) { _, _ -> openExternal(url) }
            .show()
    }

    private fun openExternal(url: String) {
        if (!UrlPolicy.isHttps(url)) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            startActivity(intent)
        } catch (_: ActivityNotFoundException) { showToast(getString(R.string.error_browser)) }
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
        AlertDialog.Builder(this).setTitle(R.string.diagnostics).setMessage(text)
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
        status.text = if (store.blocking && blocked > 0) "◈ $blocked" else if (store.blocking) getString(R.string.shield_on) else getString(R.string.shield_off)
        status.contentDescription = if (store.blocking) "$blocked blocked requests this session" else "Filter disabled"
        findViewById<TextView>(R.id.filter).setTextColor(if (store.blocking) ACCENT else MUTED)
    }
    private fun refreshBackForward() {
        findViewById<View>(R.id.back).alpha = if (webView.canGoBack()) 1f else 0.35f
        findViewById<View>(R.id.forward).alpha = if (webView.canGoForward()) 1f else 0.35f
    }
    private fun showError(message: String) {
        errorOverlay.visibility = View.VISIBLE
        findViewById<TextView>(R.id.error_text).text = message.take(250)
        progress.visibility = View.GONE
    }
    private fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    private fun applySettings(reload: Boolean) {
        if (!store.mediaAllowed) denyPendingMedia()
        filter.enabled = store.blocking
        filter.strict = store.strictBlocking
        filter.replace(store.customRules, store.excludeRules)
        header.visibility = if (store.compactChrome || fullscreenView != null) View.GONE else View.VISIBLE
        if (::webView.isInitialized) {
            webView.setBackgroundColor(if (store.darkCanvas) Color.rgb(17, 18, 25) else Color.WHITE)
            webView.settings.textZoom = store.textZoom
            webView.settings.loadsImagesAutomatically = !store.reduceImages
            webView.settings.mediaPlaybackRequiresUserGesture = store.mediaRequiresGesture
            webView.settings.useWideViewPort = store.desktopMode
            webView.settings.loadWithOverviewMode = store.desktopMode
            webView.settings.userAgentString = if (store.desktopMode) desktopAgent() else defaultAgent
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, store.cookies)
            if (reload) webView.reload()
        }
        updateStatus()
        refreshBackForward()
    }

    private fun showBookmarks() {
        val url = UrlPolicy.sanitizeBookmark(webView.url) ?: HOME
        val saved = store.listBookmarks()
        val options = mutableListOf(getString(R.string.bookmark_add), getString(R.string.bookmark_remove))
        options += saved.map { "☆ ${it.first}" }
        AlertDialog.Builder(this).setTitle(getString(R.string.bookmarks))
            .setItems(options.toTypedArray()) { _, selected ->
                when (selected) {
                    0 -> { if (store.addBookmark(webView.title ?: "Character.AI", url)) showToast(getString(R.string.bookmark_saved)) }
                    1 -> { store.removeBookmark(url); showToast(getString(R.string.bookmark_removed)) }
                    else -> saved.getOrNull(selected - 2)?.second?.let { webView.loadUrl(it) }
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
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(21,22,30)) }
        val layout = settingsColumn()
        scroll.addView(layout)
        layout.addView(sectionLabel(getString(R.string.section_profiles)))
        addButton(layout, getString(R.string.profile_title)) { showProfiles() }
        layout.addView(infoLabel(getString(R.string.profile_desc)))
        layout.addView(sectionLabel(getString(R.string.section_privacy)))
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
        addButton(layout, getString(R.string.filter_edit)) { showFilterRules() }
        addButton(layout, getString(R.string.diagnostics)) { showDiagnostics() }
        layout.addView(sectionLabel(getString(R.string.section_appearance)))
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
            max = 100; progress = store.textZoom - 75
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
        layout.addView(sectionLabel(getString(R.string.section_data)))
        addButton(layout, getString(R.string.export_settings)) { exportSettings() }
        addButton(layout, getString(R.string.import_settings)) { importSettings() }
        addButton(layout, getString(R.string.clear_cache)) { confirmClearCache() }
        addButton(layout, getString(R.string.clear_data)) { confirmClearSiteData() }
        layout.addView(sectionLabel(getString(R.string.section_about)))
        layout.addView(infoLabel("Character Companion v0.4.0 · Android only\nUnofficial client. No Character.AI affiliation, API, bypass of premium features, chat interception or background tasks.\n\nFilter intercepted: ${filter.blockedRequestCount.get()} requests this session."))
        AlertDialog.Builder(this).setView(scroll).setPositiveButton("Done", null).show()
    }

    private fun settingsColumn(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(18), dp(20), dp(22))
        setBackgroundColor(Color.rgb(22,23,32))
    }
    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text; setTextColor(ACCENT); textSize = 12f
        typeface = Typeface.DEFAULT_BOLD; letterSpacing = .12f
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
            setOnCheckedChangeListener { _, enabled -> changed(enabled) }
        })
        parent.addView(row)
    }
    private fun addButton(parent: LinearLayout, title: String, callback: () -> Unit) {
        parent.addView(Button(this).apply {
            text = title; isAllCaps = false; setTextColor(TEXT); setOnClickListener { callback() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun confirmClearCache() {
        AlertDialog.Builder(this).setMessage(getString(R.string.cache_clear_message))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ -> webView.clearCache(true); showToast(getString(R.string.cache_cleared)) }
            .show()
    }
    private fun confirmClearSiteData() {
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
    private fun exportSettings() {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "character-companion-settings.json")
        }, EXPORT_SETTINGS)
    }
    private fun importSettings() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }, IMPORT_SETTINGS)
    }

    @Deprecated("Compatibility with Android framework navigation")
    override fun onBackPressed() {
        if (fullscreenView != null) leaveFullscreen()
        else if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }
    override fun onPause() {
        super.onPause()
        webView.onPause()
        CookieManager.getInstance().flush()
    }
    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onDestroy() {
        destroyed = true
        leaveFullscreen()
        denyPendingMedia()
        chooser?.onReceiveValue(null); chooser = null
        container.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Legacy file chooser and Storage Access Framework results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            FILE_PICKER -> {
                chooser?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data))
                chooser = null
            }
            EXPORT_SETTINGS -> if (resultCode == RESULT_OK) data?.data?.let { uri ->
                try {
                    contentResolver.openOutputStream(uri, "w")?.bufferedWriter()?.use { it.write(store.exportJson()) }
                    showToast(getString(R.string.export_success))
                } catch (_: Exception) { showToast(getString(R.string.export_failure)) }
            }
            IMPORT_SETTINGS -> if (resultCode == RESULT_OK) data?.data?.let { uri ->
                try {
                    val contents = contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                        BoundedText.read(it, 200_000)
                    }
                    if (contents != null && store.importJson(contents)) {
                        applySettings(reload = true); showToast(getString(R.string.import_success))
                    } else showToast(getString(R.string.import_unsupported))
                } catch (_: Exception) { showToast(getString(R.string.import_failure)) }
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MEDIA_PERMISSION) grantPendingMedia()
    }
}
