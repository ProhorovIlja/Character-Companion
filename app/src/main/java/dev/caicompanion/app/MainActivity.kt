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
    private var destroyed = false
    private var lastErrorMainFrame = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WebView.setWebContentsDebuggingEnabled(false)
        store = SettingsStore(this)
        val base = assets.open("blocked-hosts.txt").bufferedReader().use { it.readLines() }
        filter = FilterEngine(base).also { it.enabled = store.blocking; it.replace(store.customRules, store.excludeRules) }
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
        // Respect the IME by hiding footer while typing in the website.
        val root = findViewById<View>(R.id.root)
        root.viewTreeObserver.addOnGlobalLayoutListener {
            val visible = android.graphics.Rect().also { root.getWindowVisibleDisplayFrame(it) }
            val keyboardVisible = root.rootView.height - visible.bottom > 180 * resources.displayMetrics.density
            footer.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
        }
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) webView.loadUrl(HOME)
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
                openExternal(url)
                return true
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                if (request.isForMainFrame) return null
                return filterResponse(request.url.toString())
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                lastErrorMainFrame = false
                errorOverlay.visibility = View.GONE
                if (UrlPolicy.isInternal(url)) lastUrl = url!!
                status.setText(R.string.status_loading)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                titleLabel.text = view?.title?.take(65)?.ifBlank { "character.ai" } ?: "character.ai"
                updateStatus()
                CookieManager.getInstance().flush()
                refreshBackForward()
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame && !destroyed) {
                    lastErrorMainFrame = true
                    showError(error.description?.toString() ?: getString(R.string.error_loading))
                }
            }
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler, error: android.net.http.SslError?) {
                handler.cancel() // Never ignore a certificate error.
                showError("TLS certificate verification failed")
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // Old WebView is unusable. Replace it; don't crash the whole application.
                if (destroyed) return true
                pendingMedia?.deny()
                pendingMedia = null
                chooser?.onReceiveValue(null)
                chooser = null
                val index = container.indexOfChild(view)
                container.removeView(view)
                view.destroy()
                webView = WebView(this@MainActivity)
                container.addView(webView, index.coerceAtLeast(0), FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                configureWebView(webView)
                showToast("Browser renderer restarted")
                webView.loadUrl(lastUrl.takeIf(UrlPolicy::isInternal) ?: HOME)
                return true
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress in 0..99) View.VISIBLE else View.GONE
                refreshBackForward()
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                titleLabel.text = title?.take(65)?.ifBlank { "character.ai" } ?: "character.ai"
            }
            override fun onPermissionRequest(request: PermissionRequest) = runOnUiThread {
                requestMediaPermission(request)
            }
            override fun onPermissionRequestCanceled(request: PermissionRequest) = runOnUiThread {
                if (pendingMedia === request) pendingMedia = null
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
                showToast("Download requested")
            } catch (_: Exception) { showToast(getString(R.string.error_download)) }
        })
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
        if (permissionOk && store.mediaAllowed && UrlPolicy.isTrustedMediaOrigin(request.origin.toString()))
            request.grant(pendingMediaResources) else request.deny()
        pendingMedia = null
        pendingMediaResources = emptyArray()
    }

    private fun bindControls() {
        findViewById<View>(R.id.back).setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        findViewById<View>(R.id.forward).setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        findViewById<View>(R.id.reload).setOnClickListener { webView.reload() }
        findViewById<View>(R.id.home).setOnClickListener { webView.loadUrl(HOME) }
        findViewById<View>(R.id.open_browser).setOnClickListener { openExternal(webView.url ?: HOME) }
        findViewById<View>(R.id.share).setOnClickListener { sharePage() }
        findViewById<View>(R.id.bookmarks).setOnClickListener { showBookmarks() }
        findViewById<View>(R.id.filter).setOnClickListener {
            store.blocking = !store.blocking
            applySettings(reload = true)
            showToast(if (store.blocking) "Privacy shield enabled" else "Privacy shield disabled")
        }
        findViewById<View>(R.id.filter).setOnLongClickListener { showFilterRules(); true }
        findViewById<View>(R.id.settings).setOnClickListener { showSettings() }
        findViewById<View>(R.id.error_retry).setOnClickListener { errorOverlay.visibility = View.GONE; webView.loadUrl(lastUrl) }
    }

    private fun openExternal(url: String) {
        if (!UrlPolicy.isHttps(url)) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            startActivity(intent)
        } catch (_: ActivityNotFoundException) { showToast(getString(R.string.error_browser)) }
    }
    private fun sharePage() {
        val url = webView.url?.takeIf(UrlPolicy::isHttps) ?: HOME
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url)
        }, getString(R.string.share)))
    }
    private fun updateStatus() {
        val blocked = filter.blockedRequestCount.get()
        status.text = if (store.blocking && blocked > 0) "◈ $blocked" else if (store.blocking) "◈ ON" else "○ OFF"
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
        filter.enabled = store.blocking
        filter.replace(store.customRules, store.excludeRules)
        header.visibility = if (store.compactChrome) View.GONE else View.VISIBLE
        if (::webView.isInitialized) {
            webView.setBackgroundColor(if (store.darkCanvas) Color.rgb(17, 18, 25) else Color.WHITE)
            webView.settings.textZoom = store.textZoom
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
        val url = webView.url?.takeIf(UrlPolicy::safeBookmark) ?: HOME
        val saved = store.listBookmarks()
        val options = mutableListOf("+ Save current page", "− Remove current page")
        options += saved.map { "☆ ${it.first}" }
        AlertDialog.Builder(this).setTitle(getString(R.string.bookmarks))
            .setItems(options.toTypedArray()) { _, selected ->
                when (selected) {
                    0 -> { if (store.addBookmark(webView.title ?: "Character.AI", url)) showToast("Bookmark saved") }
                    1 -> { store.removeBookmark(url); showToast("Bookmark removed") }
                    else -> saved.getOrNull(selected - 2)?.second?.let { webView.loadUrl(it) }
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showFilterRules() {
        val scroll = ScrollView(this)
        val layout = settingsColumn()
        scroll.addView(layout)
        layout.addView(infoLabel("Rules: one domain per line. No URLs, wildcards or regex. Exact host and subdomains only."))
        val custom = EditText(this).apply {
            setText(store.customRules); setTextColor(TEXT); setHintTextColor(MUTED)
            hint = "example.org"; minLines = 4; maxLines = 8
        }
        layout.addView(sectionLabel("Block domains")); layout.addView(custom)
        val exclusions = EditText(this).apply {
            setText(store.excludeRules); setTextColor(TEXT); setHintTextColor(MUTED)
            hint = "needed-cdn.example.com"; minLines = 3; maxLines = 6
        }
        layout.addView(sectionLabel("Allow domains")); layout.addView(exclusions)
        AlertDialog.Builder(this).setTitle("Filter rules · ${filter.ruleCount()} domains")
            .setView(scroll).setNegativeButton(R.string.cancel, null)
            .setPositiveButton("Apply") { _, _ ->
                store.customRules = custom.text.toString()
                store.excludeRules = exclusions.text.toString()
                applySettings(reload = true)
            }.show()
    }

    private fun showSettings() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(21,22,30)) }
        val layout = settingsColumn()
        scroll.addView(layout)
        layout.addView(sectionLabel("PRIVACY & PROTECTION"))
        addToggle(layout, "Block known ad and tracking domains", "Local list + custom rules; not a subscription bypass", store.blocking) {
            store.blocking = it; applySettings(reload = true)
        }
        addToggle(layout, "Allow third-party cookies", "May help sign-in; reduces tracking protection", store.cookies) {
            store.cookies = it; applySettings(reload = true)
        }
        addToggle(layout, "Allow microphone / camera requests", "Each request additionally needs your approval", store.mediaAllowed) {
            store.mediaAllowed = it
        }
        addButton(layout, "Edit blocked and allowed domains") { showFilterRules() }
        layout.addView(sectionLabel("APPEARANCE & PERFORMANCE"))
        addToggle(layout, "Compact navigation", "Hide upper navigation toolbar", store.compactChrome) {
            store.compactChrome = it; applySettings(reload = false)
        }
        addToggle(layout, "Dark loading background", "Changes the browser canvas, not the site's theme", store.darkCanvas) {
            store.darkCanvas = it; applySettings(reload = false)
        }
        addToggle(layout, "Desktop site mode", "May break responsive web features", store.desktopMode) {
            store.desktopMode = it; applySettings(reload = true)
        }
        addToggle(layout, "Require gesture to play media", "Prevents some automatic playback", store.mediaRequiresGesture) {
            store.mediaRequiresGesture = it; applySettings(reload = false)
        }
        layout.addView(infoLabel("Text scaling: ${store.textZoom}% (75%–175%)"))
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
        layout.addView(sectionLabel("DATA & BACKUPS"))
        addButton(layout, "Export settings and bookmarks (JSON)") { exportSettings() }
        addButton(layout, "Import settings and bookmarks (JSON)") { importSettings() }
        addButton(layout, "Clear WebView cache") { confirmClearCache() }
        addButton(layout, "Sign out / delete local site data") { confirmClearSiteData() }
        layout.addView(sectionLabel("ABOUT"))
        layout.addView(infoLabel("Character Companion v0.2.0 · Android only\nUnofficial client. No Character.AI affiliation, API, bypass of premium features, chat interception or background tasks.\n\nFilter intercepted: ${filter.blockedRequestCount.get()} requests this session."))
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
        AlertDialog.Builder(this).setMessage("Delete only the WebView's HTTP cache?")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ -> webView.clearCache(true); showToast("Cache cleared") }
            .show()
    }
    private fun confirmClearSiteData() {
        AlertDialog.Builder(this).setTitle("Delete local site data?")
            .setMessage("Cookies, local storage, form data and browsing history will be deleted. Remote Character.AI chats are not deleted.")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton("Delete") { _, _ ->
                // Cookie removal is asynchronous. Do not reload before it completes.
                CookieManager.getInstance().removeAllCookies {
                    runOnUiThread {
                        CookieManager.getInstance().flush()
                        WebStorage.getInstance().deleteAllData()
                        webView.clearCache(true); webView.clearHistory(); webView.clearFormData()
                        webView.loadUrl(HOME)
                        showToast("Local site data cleared")
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
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
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
        pendingMedia?.deny(); pendingMedia = null
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
                    showToast("Settings exported")
                } catch (_: Exception) { showToast("Export failed") }
            }
            IMPORT_SETTINGS -> if (resultCode == RESULT_OK) data?.data?.let { uri ->
                try {
                    val contents = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(200_001) }
                    if (contents != null && store.importJson(contents)) {
                        applySettings(reload = true); showToast("Settings imported")
                    } else showToast("Unsupported settings file")
                } catch (_: Exception) { showToast("Import failed") }
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MEDIA_PERMISSION) grantPendingMedia()
    }
}
