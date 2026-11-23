package dev.caicompanion.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/** ARM64 Gecko preview; does not use Android WebView or transfer its cookies. */
class GeckoActivity : Activity() {
    companion object {
        private const val HOME = "https://character.ai/"
        private const val MEDIA_REQUEST_CODE = 817
        @Volatile private var engine: GeckoRuntime? = null
        private fun engine(context: Context): GeckoRuntime = synchronized(this) {
            engine ?: GeckoRuntime.create(context.applicationContext).also { engine = it }
        }
    }

    private lateinit var settings: SettingsStore
    private lateinit var session: GeckoSession
    private lateinit var surface: GeckoView
    private lateinit var root: LinearLayout
    private lateinit var locationLabel: TextView
    private lateinit var backButton: Button
    private lateinit var forwardButton: Button
    private lateinit var privacyButton: Button
    private var activeUrl: String = HOME
    private var canGoBack = false
    private var canGoForward = false
    private var permissionCallback: GeckoSession.PermissionDelegate.Callback? = null
    private var pendingMediaCallback: GeckoSession.PermissionDelegate.MediaCallback? = null
    private var started = false
    private var backCallback: OnBackInvokedCallback? = null
    private val fg = Color.rgb(235, 225, 211)
    private val accent = Color.rgb(201, 169, 113)
    private val bg = Color.rgb(17, 18, 25)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        if (settings.protectScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                }
                insets
            }
        }
        setContentView(root)
        root.requestApplyInsets()
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        locationLabel = TextView(this).apply {
            text = getString(R.string.gecko_preview_title)
            setTextColor(accent)
            textSize = 15f
            maxLines = 2
            setPadding(dp(14), dp(10), dp(6), dp(8))
        }
        top.addView(locationLabel, LinearLayout.LayoutParams(0, dp(54), 1f))
        top.addView(button(getString(R.string.gecko_legacy)) {
            startActivity(Intent(this, MainActivity::class.java))
        })
        root.addView(top)
        val bottom = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        backButton = button(getString(R.string.back)) { navigateBack() }
        forwardButton = button(getString(R.string.forward)) { if (canGoForward) session.goForward() }
        bottom.addView(backButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        bottom.addView(forwardButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        bottom.addView(button(getString(R.string.reload)) { session.reload() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        bottom.addView(button(getString(R.string.home)) { session.loadUri(HOME) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(bottom)
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        privacyButton = button(getString(R.string.filter)) {
            settings.blocking = !settings.blocking
            session.settings.setUseTrackingProtection(settings.blocking)
            updatePrivacy()
        }
        actions.addView(privacyButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(button(getString(R.string.share)) { shareSafeUrl() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(button(getString(R.string.gecko_media)) { configureMedia() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(button(getString(R.string.open_browser)) {
            confirmBrowser(activeUrl)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(actions)
        surface = GeckoView(this)
        root.addView(surface, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        updatePrivacy()
        updateNavigation()
        try {
            session = GeckoSession()
            session.settings.setUseTrackingProtection(settings.blocking)
            session.setNavigationDelegate(navigationDelegate)
            session.setProgressDelegate(progressDelegate)
            session.setContentDelegate(object : GeckoSession.ContentDelegate {})
            session.setPermissionDelegate(permissionDelegate)
            session.open(engine(applicationContext))
            surface.setSession(session)
            val shared = sharedLink(intent)
            if (intent?.action == Intent.ACTION_SEND) setIntent(Intent(Intent.ACTION_MAIN))
            activeUrl = shared ?: if (settings.resumePage) settings.lastVisited.ifBlank { HOME } else HOME
            session.loadUri(NativeWorkspaceNavigation.safeDestination(activeUrl))
        } catch (_: RuntimeException) {
            AlertDialog.Builder(this).setTitle(R.string.error_title)
                .setMessage(R.string.gecko_error)
                .setPositiveButton(R.string.gecko_legacy) { _, _ ->
                    startActivity(Intent(this, MainActivity::class.java)); finish()
                }
                .setNegativeButton(R.string.cancel) { _, _ -> finish() }
                .show()
        }
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = OnBackInvokedCallback { navigateBack() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback!!)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        setTextColor(fg)
        setBackgroundResource(R.drawable.settings_action)
        setOnClickListener { action() }
    }

    private fun sharedLink(value: Intent?): String? {
        if (value?.action != Intent.ACTION_SEND || value.type?.substringBefore(';') != "text/plain") return null
        val supplied = runCatching { value.getStringExtra(Intent.EXTRA_TEXT) }.getOrNull()
        return IncomingLinkParser.safeSharedLink(supplied)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val safe = sharedLink(intent)
        if (intent.action == Intent.ACTION_SEND) setIntent(Intent(Intent.ACTION_MAIN))
        if (safe != null && ::session.isInitialized && session.isOpen) session.loadUri(safe)
    }

    private val navigationDelegate = object : GeckoSession.NavigationDelegate {
        override fun onLoadRequest(
            session: GeckoSession,
            request: GeckoSession.NavigationDelegate.LoadRequest
        ): GeckoResult<AllowOrDeny>? {
            val newWindow = request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW
            return when (GeckoNavigationPolicy.decide(request.uri, request.hasUserGesture, newWindow)) {
                GeckoNavigationPolicy.Decision.ALLOW -> null
                GeckoNavigationPolicy.Decision.BLOCK -> GeckoResult.fromValue(AllowOrDeny.DENY)
                GeckoNavigationPolicy.Decision.CONFIRM_EXTERNAL -> {
                    confirmBrowser(request.uri)
                    GeckoResult.fromValue(AllowOrDeny.DENY)
                }
            }
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? = null

        override fun onLocationChange(
            session: GeckoSession, url: String?,
            perms: List<GeckoSession.PermissionDelegate.ContentPermission>, hasUserGesture: Boolean
        ) {
            activeUrl = url ?: HOME
            // Only store public content paths, never credentials, tokens or fragments.
            UrlPolicy.sanitizeBookmark(url)?.let { if (settings.resumePage) settings.lastVisited = it }
            val safe = UrlPolicy.safeShareUrl(url)
            locationLabel.text = safe?.let { "Character.AI · " + it.substringAfter("character.ai/").take(48) }
                ?: getString(R.string.gecko_preview_title)
        }

        override fun onCanGoBack(session: GeckoSession, value: Boolean) {
            canGoBack = value; updateNavigation()
        }

        override fun onCanGoForward(session: GeckoSession, value: Boolean) {
            canGoForward = value; updateNavigation()
        }
    }

    private val progressDelegate = object : GeckoSession.ProgressDelegate {
        override fun onPageStart(session: GeckoSession, url: String) {
            locationLabel.alpha = 0.6f
        }
        override fun onPageStop(session: GeckoSession, success: Boolean) {
            locationLabel.alpha = 1f
            if (!success) Toast.makeText(this@GeckoActivity, R.string.error_loading, Toast.LENGTH_SHORT).show()
        }
    }

    private val permissionDelegate = object : GeckoSession.PermissionDelegate {
        override fun onAndroidPermissionsRequest(
            session: GeckoSession, permissions: Array<String>?,
            callback: GeckoSession.PermissionDelegate.Callback
        ) {
            val requested = permissions.orEmpty()
            // Android 17 local-network/location/notifications are never silently granted.
            if (!started || !settings.mediaAllowed || !UrlPolicy.isTrustedMediaOrigin(activeUrl) || requested.isEmpty() ||
                requested.any { it != Manifest.permission.RECORD_AUDIO && it != Manifest.permission.CAMERA } ||
                permissionCallback != null) {
                callback.reject(); return
            }
            val absent = requested.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            if (absent.isEmpty()) callback.grant()
            else {
                permissionCallback = callback
                requestPermissions(absent.toTypedArray(), MEDIA_REQUEST_CODE)
            }
        }

        override fun onMediaPermissionRequest(
            session: GeckoSession, uri: String,
            video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
            audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
            callback: GeckoSession.PermissionDelegate.MediaCallback
        ) {
            val hasCamera = !video.isNullOrEmpty()
            val hasMic = !audio.isNullOrEmpty()
            if (!started || !settings.mediaAllowed || pendingMediaCallback != null ||
                !UrlPolicy.isTrustedMediaOrigin(uri) ||
                !UrlPolicy.isTrustedMediaOrigin(activeUrl) || (!hasCamera && !hasMic) ||
                (hasMic && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) ||
                (hasCamera && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
                callback.reject(); return
            }
            pendingMediaCallback = callback
            fun finishMedia(accepted: Boolean) {
                if (pendingMediaCallback !== callback) return // onStop may already deny it
                pendingMediaCallback = null
                if (accepted && started && settings.mediaAllowed && UrlPolicy.isTrustedMediaOrigin(activeUrl))
                    callback.grant(video?.firstOrNull(), audio?.firstOrNull())
                else callback.reject()
            }
            AlertDialog.Builder(this@GeckoActivity)
                .setTitle(R.string.gecko_media)
                .setMessage(when {
                    hasMic && hasCamera -> R.string.media_reason
                    hasMic -> R.string.microphone_reason
                    else -> R.string.camera_reason
                })
                .setPositiveButton(R.string.confirm) { _, _ -> finishMedia(true) }
                .setNegativeButton(R.string.cancel) { _, _ -> finishMedia(false) }
                .setOnCancelListener { finishMedia(false) }
                .show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != MEDIA_REQUEST_CODE) return
        val callback = permissionCallback ?: return
        permissionCallback = null
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED })
            callback.grant()
        else callback.reject()
    }

    private fun updateNavigation() {
        if (!::backButton.isInitialized) return
        backButton.isEnabled = canGoBack
        forwardButton.isEnabled = canGoForward
        backButton.alpha = if (canGoBack) 1f else 0.4f
        forwardButton.alpha = if (canGoForward) 1f else 0.4f
    }

    private fun updatePrivacy() {
        if (::privacyButton.isInitialized)
            privacyButton.text = getString(R.string.filter) + if (settings.blocking) " ✓" else " ×"
    }

    private fun configureMedia() {
        AlertDialog.Builder(this)
            .setTitle(R.string.gecko_media)
            .setMessage(if (settings.mediaAllowed) R.string.gecko_media_on else R.string.gecko_media_off)
            .setPositiveButton(if (settings.mediaAllowed) R.string.disabled else R.string.enabled) { _, _ ->
                settings.mediaAllowed = !settings.mediaAllowed
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun shareSafeUrl() {
        val link = UrlPolicy.safeShareUrl(activeUrl) ?: run {
            Toast.makeText(this, R.string.link_unavailable, Toast.LENGTH_SHORT).show(); return
        }
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, link) }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    private fun confirmBrowser(url: String) {
        if (!UrlPolicy.isHttps(url)) return
        AlertDialog.Builder(this).setTitle(R.string.external_link_title)
            .setMessage(getString(R.string.external_link_message, android.net.Uri.parse(url).host ?: "?"))
            .setPositiveButton(R.string.open_browser) { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.error_browser, Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun navigateBack() {
        if (::session.isInitialized && session.isOpen && canGoBack) session.goBack()
        else finish()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() { navigateBack() }

    override fun onStart() {
        super.onStart()
        started = true
    }

    override fun onStop() {
        // Revoke unresolved prompts when hidden; do not auto-grant on return.
        started = false
        pendingMediaCallback?.reject(); pendingMediaCallback = null
        permissionCallback?.reject(); permissionCallback = null
        super.onStop()
    }

    override fun onDestroy() {
        pendingMediaCallback?.reject(); pendingMediaCallback = null
        permissionCallback?.reject(); permissionCallback = null
        if (Build.VERSION.SDK_INT >= 33) backCallback?.let {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
        }
        if (::surface.isInitialized) surface.releaseSession()
        if (::session.isInitialized && session.isOpen) session.close()
        super.onDestroy()
    }
}
