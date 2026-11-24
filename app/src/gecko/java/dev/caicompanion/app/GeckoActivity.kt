package dev.caicompanion.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.SystemClock
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse

/** ARM64 Gecko preview; does not use Android WebView or transfer its cookies. */
class GeckoActivity : Activity() {
    companion object {
        private const val HOME = "https://character.ai/"
        private const val MEDIA_REQUEST_CODE = 817
        private const val OPEN_DOCUMENT_CODE = 818
        private const val SAVE_DOCUMENT_CODE = 819
        @Volatile private var engine: GeckoRuntime? = null
        private fun engine(context: Context): GeckoRuntime = synchronized(this) {
            engine ?: GeckoRuntime.create(context.applicationContext).also { engine = it }
        }
    }

    private lateinit var settings: SettingsStore
    private val loginProtection = LoginCompatibilitySession()
    private val voiceHandoff = VoiceComposerHandoff()
    private lateinit var session: GeckoSession
    private lateinit var surface: GeckoView
    private lateinit var root: LinearLayout
    private lateinit var locationLabel: TextView
    private lateinit var backButton: Button
    private lateinit var privacyButton: Button
    private lateinit var chromeHeader: LinearLayout
    private lateinit var actionsRow: LinearLayout
    private var keyboardVisible = false
    private val recoveryBudget = RendererRecoveryBudget(2, 120_000L)
    private var needsRecovery = false
    private var recoveryDialog: AlertDialog? = null
    private var activeUrl: String = HOME
    private var canGoBack = false
    private var canGoForward = false
    private var permissionCallback: GeckoSession.PermissionDelegate.Callback? = null
    private var androidPermissionPage: String? = null
    private var mediaPromptPage: String? = null
    private var pendingMediaCallback: GeckoSession.PermissionDelegate.MediaCallback? = null
    private var started = false
    private var pendingFilePrompt: GeckoSession.PromptDelegate.FilePrompt? = null
    private var pendingFileResult: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = null
    private var filePickerOpen = false
    private var filePickerOrigin: String? = null
    private var pendingDownload: WebResponse? = null
    private var downloadPage: String? = null
    private var downloadPickerOpen = false
    private var genericPromptDialog: AlertDialog? = null
    private var backCallback: OnBackInvokedCallback? = null
    private val fg = Color.rgb(228, 231, 240)
    private val accent = Color.rgb(168, 184, 255)
    private val settingsBackup = SettingsBackup(this)
    private val bg = Color.rgb(17, 19, 25)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        if (settings.browserSessionPreferred) {
            val shared = sharedLink(intent)
            if (intent?.action == Intent.ACTION_SEND) setIntent(Intent(Intent.ACTION_MAIN))
            if (BrowserSessionActivity.enter(this, shared ?: HOME, openNow = shared != null)) return
        }
        if (!settings.browserSessionPreferred && settings.preferWebView) {
            val shared = sharedLink(intent)
            if (intent?.action == Intent.ACTION_SEND) setIntent(Intent(Intent.ACTION_MAIN))
            if (switchToWebView(shared)) return
        }
        if (settings.protectScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                    keyboardVisible = insets.isVisible(WindowInsets.Type.ime())
                    updateCompactChrome()
                }
                insets
            }
        }
        setContentView(root)
        root.requestApplyInsets()
        chromeHeader = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(25, 28, 37))
            setPadding(0, 0, dp(8), 0)
        }
        backButton = Button(this).apply {
            contentDescription = getString(R.string.back)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_shell_back, 0, 0, 0)
            setPadding(dp(13), 0, 0, 0)
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { navigateBack() }
        }
        chromeHeader.addView(backButton, LinearLayout.LayoutParams(dp(48), dp(52)))
        locationLabel = TextView(this).apply {
            text = "Character.AI"
            setTextColor(fg); textSize = 15f
            isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), 0, dp(8), 0)
        }
        chromeHeader.addView(locationLabel, LinearLayout.LayoutParams(0, dp(52), 1f))
        privacyButton = button(getString(R.string.filter)) {
            settings.blocking = !settings.blocking
            refreshTrackingProtection()
        }.apply {
            setBackgroundResource(android.R.drawable.list_selector_background)
            setPadding(dp(4), 0, dp(4), 0)
        }
        privacyButton.setOnLongClickListener { toggleLoginProtection(); true }
        chromeHeader.addView(privacyButton, LinearLayout.LayoutParams(dp(80), dp(48)))
        root.addView(chromeHeader)
        surface = GeckoView(this)
        root.addView(surface, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        actionsRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(25, 28, 37))
        }
        actionsRow.addView(navAction(R.string.home, R.drawable.ic_shell_home) {
            if (sessionReady()) session.loadUri(HOME)
        }, LinearLayout.LayoutParams(0, dp(56), 1f))
        actionsRow.addView(navAction(R.string.shell_saved, R.drawable.ic_shell_saved) { showBookmarks() },
            LinearLayout.LayoutParams(0, dp(56), 1f))
        actionsRow.addView(navAction(R.string.shell_voice, R.drawable.ic_shell_mic) {
            voiceHandoff.launch(this, if (sessionReady()) activeUrl else null)
        }, LinearLayout.LayoutParams(0, dp(56), 1f))
        val more = navAction(R.string.shell_more, R.drawable.ic_shell_more) {}
        more.setOnClickListener { showPageMenu(more) }
        actionsRow.addView(more, LinearLayout.LayoutParams(0, dp(56), 1f))
        root.addView(actionsRow)
        updateCompactChrome()
        updatePrivacy()
        updateNavigation()
        try {
            session = GeckoSession()
            session.settings.setUseTrackingProtection(settings.blocking)
            session.setNavigationDelegate(navigationDelegate)
            session.setProgressDelegate(progressDelegate)
            session.setContentDelegate(contentDelegate)
            session.setPromptDelegate(promptDelegate)
            session.setPermissionDelegate(permissionDelegate)
            session.open(engine(applicationContext))
            surface.setSession(session)
            val shared = sharedLink(intent)
            if (intent?.action == Intent.ACTION_SEND) setIntent(Intent(Intent.ACTION_MAIN))
            activeUrl = shared ?: if (settings.resumePage) settings.lastVisited.ifBlank { HOME } else HOME
            val destination = NativeWorkspaceNavigation.safeDestination(activeUrl)
            observeLoginPage(destination)
            session.loadUri(destination)
        } catch (_: RuntimeException) {
            AlertDialog.Builder(this).setTitle(R.string.error_title)
                .setMessage(R.string.gecko_error)
                .setPositiveButton(R.string.gecko_legacy) { _, _ ->
                    switchToWebView(activeUrl)
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

    private fun sessionReady(): Boolean = ::session.isInitialized && session.isOpen && !needsRecovery

    private fun navAction(label: Int, icon: Int, run: () -> Unit): TextView = TextView(this).apply {
        setText(label); textSize = 11f; setTextColor(fg); gravity = Gravity.CENTER
        setPadding(dp(4), dp(7), dp(4), dp(6))
        setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0)
        compoundDrawablePadding = dp(3)
        setBackgroundResource(android.R.drawable.list_selector_background)
        isFocusable = true; contentDescription = getString(label)
        setOnClickListener { run() }
    }

    private fun showPageMenu(anchor: View) {
        val ready = sessionReady()
        ShellMenu.show(this, anchor, listOf(
            ShellMenu.Action(R.string.settings) { showGeckoSettings() },
            ShellMenu.Action(R.string.release_center) { ReleaseCenter.show(this) },
            ShellMenu.Action(R.string.section_data) { settingsBackup.show() },
            ShellMenu.Action(R.string.gecko_legacy) { confirmWebViewSwitch() },
            ShellMenu.Action(R.string.device_compat_title) { DeviceRuntimeProbe.show(this, "gecko") },
            ShellMenu.Action(R.string.forward, ready && canGoForward) { session.goForward() },
            ShellMenu.Action(R.string.reload, ready) { session.reload() },
            ShellMenu.Action(R.string.bookmark_add, ready) { saveCurrentBookmark() },
            ShellMenu.Action(R.string.share, ready) { shareSafeUrl() },
            ShellMenu.Action(if (settings.blocking) R.string.shell_disable_shield else R.string.shell_enable_shield) {
                settings.blocking = !settings.blocking; refreshTrackingProtection()
            },
            ShellMenu.Action(R.string.login_compatibility_button, ready) { toggleLoginProtection() },
            ShellMenu.Action(R.string.gecko_media) { configureMedia() },
            ShellMenu.Action(R.string.browser_mode_sign_in) { confirmBrowserSessionMode() },
            ShellMenu.Action(R.string.open_browser, ready) { confirmBrowser(activeUrl) }
        ))
    }

    /** Gecko bookmark editor never accesses page DOM, cookies or chat text. */
    private fun saveCurrentBookmark() {
        val url = UrlPolicy.sanitizeBookmark(activeUrl)
        if (url == null) {
            Toast.makeText(this, R.string.hub_bookmark_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        editLibraryBookmark("Character.AI", url) {}
    }

    /** Same validated local editor for add, save and rename; no network request. */
    private fun editLibraryBookmark(initialTitle: String, fixedUrl: String?, onSaved: () -> Unit) {
        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val title = EditText(this).apply {
            setSingleLine(true)
            hint = getString(R.string.hub_bookmark_name_hint)
            filters = arrayOf(InputFilter.LengthFilter(90))
            setText(initialTitle)
        }
        val link = EditText(this).apply {
            setSingleLine(true)
            hint = getString(R.string.hub_bookmark_url_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            filters = arrayOf(InputFilter.LengthFilter(4096))
        }
        fields.addView(title)
        if (fixedUrl == null) fields.addView(link)
        val editor = AlertDialog.Builder(this)
            .setTitle(if (fixedUrl == null) R.string.hub_bookmark_manual_add else R.string.bookmark_add)
            .setView(fields).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.apply, null).create()
        editor.show()
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val url = fixedUrl ?: link.text.toString()
            if (settings.addBookmark(title.text.toString(), url)) {
                editor.dismiss()
                onSaved()
                Toast.makeText(this, R.string.bookmark_saved, Toast.LENGTH_SHORT).show()
            } else {
                (if (fixedUrl == null) link else title).error = getString(
                    if (OfflineBookmarkEditor.normalize(title.text.toString(), url) == null)
                        R.string.hub_bookmark_invalid else R.string.library_full_pinned)
            }
        }
    }

    /** Native library: search/selection/deletion work without network requests. */
    private fun showBookmarks() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(6))
        }
        val search = EditText(this).apply {
            setSingleLine(true)
            hint = getString(R.string.hub_search_hint)
            filters = arrayOf(InputFilter.LengthFilter(80))
        }
        val empty = TextView(this).apply {
            setText(R.string.hub_empty)
            setTextColor(fg)
            setPadding(0, dp(12), 0, dp(10))
        }
        val entries = mutableListOf<String>()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, entries)
        val list = ListView(this).apply { this.adapter = adapter }
        val orderLabels = intArrayOf(R.string.library_order_saved, R.string.library_order_newest, R.string.library_order_title)
        val sort = Button(this).apply { isAllCaps = false }
        layout.addView(search)
        layout.addView(sort)
        val favorites = android.widget.CheckBox(this).apply {
            setText(R.string.library_favorites_only)
            setTextColor(fg)
        }
        layout.addView(favorites)
        layout.addView(empty)
        layout.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(300)))
        var visible = emptyList<Pair<String, String>>()
        var index = NativeLibrarySearch.Index(settings.listBookmarks(), settings.pinnedBookmarks())
        var visiblePins = emptySet<String>()
        val searchOnly: () -> Unit = {
            val next = index.filter(search.text.toString(), order = settings.libraryOrder, favoritesOnly = favorites.isChecked)
            val pins = next.map { it.second }.filter(index::isPinned).toSet()
            if (next != visible || pins != visiblePins) {
                visible = next
                visiblePins = pins
                adapter.setNotifyOnChange(false)
                adapter.clear()
                adapter.addAll(visible.map { (title, url) ->
                    if (url in pins) getString(R.string.library_pinned_title, title) else title
                })
                adapter.notifyDataSetChanged()
            }
            empty.setText(if (favorites.isChecked) R.string.library_favorites_empty else R.string.hub_empty)
            empty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        }
        fun updateSortLabel() {
            sort.text = getString(R.string.library_order_format, getString(orderLabels[settings.libraryOrder.ordinal]))
        }
        updateSortLabel()
        sort.setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.library_order)
                .setSingleChoiceItems(orderLabels.map { getString(it) }.toTypedArray(), settings.libraryOrder.ordinal) { dialog, choice ->
                    settings.libraryOrder = NativeLibrarySearch.Order.entries[choice]
                    updateSortLabel()
                    searchOnly()
                    dialog.dismiss()
                }.setNegativeButton(R.string.cancel, null).show()
        }
        favorites.setOnCheckedChangeListener { _, _ -> searchOnly() }
        val refresh: () -> Unit = {
            index = NativeLibrarySearch.Index(settings.listBookmarks(), settings.pinnedBookmarks())
            searchOnly()
        }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.bookmarks)
            .setView(layout).setPositiveButton(R.string.close, null)
            .setNeutralButton(R.string.bookmark_add, null).create()
        list.setOnItemClickListener { _, _, position, _ ->
            val url = visible.getOrNull(position)?.second?.let(UrlPolicy::sanitizeBookmark)
            if (url != null && ::session.isInitialized && session.isOpen && !needsRecovery) {
                session.loadUri(url)
                dialog.dismiss()
            } else {
                dialog.dismiss()
                if (needsRecovery) showRecoveryPrompt()
            }
        }
        list.setOnItemLongClickListener { _, _, position, _ ->
            val bookmark = visible.getOrNull(position)
            if (bookmark != null) {
                val pinned = index.isPinned(bookmark.second)
                val options = arrayOf(getString(R.string.hub_bookmark_rename),
                    getString(R.string.hub_bookmark_browser), getString(R.string.hub_bookmark_delete),
                    getString(if (pinned) R.string.library_unpin else R.string.library_pin))
                AlertDialog.Builder(this).setTitle(bookmark.first).setItems(options) { _, choice ->
                    when (choice) {
                        0 -> editLibraryBookmark(bookmark.first, bookmark.second, refresh)
                        1 -> confirmBrowser(bookmark.second)
                        2 -> AlertDialog.Builder(this).setTitle(R.string.hub_bookmark_delete)
                            .setMessage(R.string.hub_bookmark_confirm_delete)
                            .setPositiveButton(R.string.delete) { _, _ ->
                                settings.removeBookmark(bookmark.second)
                                refresh()
                                Toast.makeText(this, R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
                            }.setNegativeButton(R.string.cancel, null).show()
                        3 -> if (settings.setBookmarkPinned(bookmark.second, !pinned)) refresh()
                    }
                }.setNegativeButton(R.string.cancel, null).show()
            }
            true
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = searchOnly()
        })
        searchOnly()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            editLibraryBookmark("", null, refresh)
        }
    }

    private fun settingCheckbox(label: Int, selected: Boolean, change: (Boolean) -> Unit): CheckBox =
        CheckBox(this).apply {
            text = getString(label)
            isChecked = selected
            setTextColor(fg)
            textSize = 14f
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnCheckedChangeListener { _, value -> change(value) }
        }

    /** Only options actually supported by Gecko are shown; WebView-only settings are omitted. */
    private fun showGeckoSettings() {
        val categories = intArrayOf(R.string.section_browser_session, R.string.section_privacy, R.string.section_appearance)
        AlertDialog.Builder(this).setTitle(R.string.settings)
            .setItems(categories.map { getString(it) }.toTypedArray()) { _, selected ->
                showGeckoSettingsSection(categories[selected])
            }.setNeutralButton(R.string.gecko_legacy) { _, _ ->
                confirmWebViewSwitch()
            }.setPositiveButton(R.string.close, null).show()
    }

    private fun showGeckoSettingsSection(section: Int) {
        val options = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        if (section == R.string.section_browser_session) {
            options.addView(button(getString(R.string.browser_mode_sign_in)) {
                confirmBrowserSessionMode()
            })
        }
        if (section == R.string.section_privacy) {
            options.addView(settingCheckbox(R.string.gecko_tracking_protection, settings.blocking) {
                settings.blocking = it
                refreshTrackingProtection()
            })
            options.addView(TextView(this).apply {
                text = getString(R.string.gecko_tracking_explanation)
                setTextColor(fg)
                textSize = 13f
                setPadding(dp(8), dp(0), dp(8), dp(10))
            })
            options.addView(button(getString(R.string.login_compatibility_button)) {
                toggleLoginProtection()
            })
            options.addView(TextView(this).apply {
                text = getString(R.string.login_compatibility_info)
                setTextColor(fg)
                setPadding(dp(8), dp(6), dp(8), dp(10))
            })
            options.addView(settingCheckbox(R.string.toggle_media, settings.mediaAllowed) {
                settings.mediaAllowed = it
                if (!it) revokeMediaPermissions()
            })
            options.addView(settingCheckbox(R.string.toggle_protect_screen, settings.protectScreen) {
                settings.protectScreen = it
                if (it) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            })
        }
        if (section == R.string.section_appearance) {
            options.addView(settingCheckbox(R.string.toggle_resume, settings.resumePage) {
                settings.resumePage = it
                if (!it) settings.lastVisited = ""
                else UrlPolicy.sanitizeBookmark(activeUrl)?.let { safe -> settings.lastVisited = safe }
            })
            options.addView(settingCheckbox(R.string.toggle_compact, settings.compactChrome) {
                settings.compactChrome = it
                updateCompactChrome()
            })
        }
        val scroll = ScrollView(this).apply { addView(options) }
        AlertDialog.Builder(this).setTitle(section).setView(scroll)
            .setNeutralButton(R.string.settings) { _, _ -> showGeckoSettings() }
            .setPositiveButton(R.string.close, null).show()
    }

    private fun revokeMediaPermissions() {
        runCatching { pendingMediaCallback?.reject() }
        pendingMediaCallback = null
        runCatching { permissionCallback?.reject() }
        permissionCallback = null
        androidPermissionPage = null
        mediaPromptPage = null
    }

    private fun updateCompactChrome() {
        if (::chromeHeader.isInitialized)
            chromeHeader.visibility = if (settings.compactChrome) View.GONE else View.VISIBLE
        if (::actionsRow.isInitialized)
            actionsRow.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
    }

    private fun onRendererExit(failed: GeckoSession) {
        if (!::session.isInitialized || failed !== session || needsRecovery || isFinishing || isDestroyed) return
        geckoExited()
    }

    /** User-initiated recovery, never a timer or background monitor. Form contents may be lost. */
    private fun geckoExited() {
        voiceHandoff.invalidate()
        needsRecovery = true
        loginProtection.reset()
        updatePrivacy()
        // A destroyed renderer cannot satisfy old site permissions, pending file
        // prompts or downloads. Keep no callback into the failed session.
        revokeMediaPermissions()
        runCatching { finishFilePrompt(emptyList()) }
        abandonDownload()
        if (!isFinishing && !isDestroyed && started) showRecoveryPrompt()
    }

    private fun showRecoveryPrompt() {
        if (!needsRecovery || isFinishing || isDestroyed || recoveryDialog?.isShowing == true) return
        val canRetry = recoveryBudget.recentRestartCount(SystemClock.elapsedRealtime()) < 2
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.gecko_recovery_title)
            .setMessage(if (canRetry) R.string.gecko_recovery_message else R.string.gecko_recovery_limit)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.gecko_legacy) { _, _ ->
                switchToWebView(activeUrl)
            }
        if (canRetry) dialog.setPositiveButton(R.string.retry) { _, _ -> recoverGecko() }
        recoveryDialog = dialog.create().also { it.show() }
    }

    private fun recoverGecko() {
        if (!needsRecovery || !::session.isInitialized ||
            !recoveryBudget.allowAutomaticRestart(SystemClock.elapsedRealtime())) {
            showRecoveryPrompt()
            return
        }
        val safeUrl = UrlPolicy.sanitizeBookmark(activeUrl) ?: HOME
        try {
            surface.releaseSession()
            if (session.isOpen) session.close()
            session.open(engine(applicationContext))
            surface.setSession(session)
            needsRecovery = false
            loginProtection.setVisible(started && hasWindowFocus())
            canGoBack = false
            canGoForward = false
            updateNavigation()
            observeLoginPage(safeUrl)
            session.loadUri(safeUrl)
        } catch (_: RuntimeException) {
            needsRecovery = true
            showRecoveryPrompt()
        }
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
        if (safe != null && !needsRecovery && ::session.isInitialized && session.isOpen)
            session.loadUri(safe)
    }

    /** SAF selection: Gecko sees only URIs explicitly selected by the user, not device storage. */
    private val promptDelegate = object : GeckoSession.PromptDelegate {
        override fun onAlertPrompt(
            session: GeckoSession, prompt: GeckoSession.PromptDelegate.AlertPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            if (!started || genericPromptDialog?.isShowing == true)
                return GeckoResult.fromValue(prompt.dismiss())
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            genericPromptDialog = AlertDialog.Builder(this@GeckoActivity)
                .setTitle(prompt.title.orEmpty().take(100).ifBlank { getString(R.string.app_name) })
                .setMessage(prompt.message.orEmpty().take(1200))
                .setPositiveButton(android.R.string.ok) { _, _ -> result.complete(prompt.dismiss()) }
                .setOnCancelListener { if (!prompt.isComplete) result.complete(prompt.dismiss()) }
                .setOnDismissListener { if (!prompt.isComplete) result.complete(prompt.dismiss()) }
                .create().also { it.show() }
            return result
        }

        override fun onButtonPrompt(
            session: GeckoSession, prompt: GeckoSession.PromptDelegate.ButtonPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            if (!started || genericPromptDialog?.isShowing == true)
                return GeckoResult.fromValue(prompt.dismiss())
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            genericPromptDialog = AlertDialog.Builder(this@GeckoActivity)
                .setTitle(prompt.title.orEmpty().take(100).ifBlank { getString(R.string.app_name) })
                .setMessage(prompt.message.orEmpty().take(1200))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    result.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE))
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    result.complete(prompt.confirm(GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE))
                }
                .setOnCancelListener { if (!prompt.isComplete) result.complete(prompt.dismiss()) }
                .setOnDismissListener { if (!prompt.isComplete) result.complete(prompt.dismiss()) }
                .create().also { it.show() }
            return result
        }

        override fun onTextPrompt(
            session: GeckoSession, prompt: GeckoSession.PromptDelegate.TextPrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            if (!started || genericPromptDialog?.isShowing == true)
                return GeckoResult.fromValue(prompt.dismiss())
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val field = EditText(this@GeckoActivity).apply {
                setSingleLine(true)
                setText(prompt.defaultValue.orEmpty().take(4096))
                setSelectAllOnFocus(true)
            }
            genericPromptDialog = AlertDialog.Builder(this@GeckoActivity)
                .setTitle(prompt.title.orEmpty().take(100).ifBlank { getString(R.string.app_name) })
                .setMessage(prompt.message.orEmpty().take(600))
                .setView(field)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    result.complete(prompt.confirm(field.text.toString().take(4096)))
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> result.complete(prompt.dismiss()) }
                .setOnCancelListener { if (!prompt.isComplete) result.complete(prompt.dismiss()) }
                .setOnDismissListener { if (!prompt.isComplete) result.complete(prompt.dismiss()) }
                .create().also { it.show() }
            return result
        }

        override fun onChoicePrompt(
            session: GeckoSession, prompt: GeckoSession.PromptDelegate.ChoicePrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            if (!started || genericPromptDialog?.isShowing == true)
                return GeckoResult.fromValue(prompt.dismiss())
            val choices = prompt.choices.flatMap { it.items?.toList() ?: listOf(it) }
                .filter { !it.separator && !it.disabled }.take(100)
            if (choices.isEmpty()) return GeckoResult.fromValue(prompt.dismiss())
            val names = choices.map { it.label.take(150) }.toTypedArray()
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            val builder = AlertDialog.Builder(this@GeckoActivity)
                .setTitle(prompt.title.orEmpty().take(100).ifBlank { getString(R.string.app_name) })
                .setOnCancelListener { if (!prompt.isComplete) result.complete(prompt.dismiss()) }
                .setOnDismissListener { if (!prompt.isComplete) result.complete(prompt.dismiss()) }
            if (prompt.type == GeckoSession.PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
                val selected = choices.map { it.selected }.toBooleanArray()
                builder.setMultiChoiceItems(names, selected) { _, index, checked -> selected[index] = checked }
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        result.complete(prompt.confirm(choices.filterIndexed { index, _ ->
                            selected[index]
                        }.map { it.id }.toTypedArray()))
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> result.complete(prompt.dismiss()) }
            } else {
                builder.setItems(names) { _, index -> result.complete(prompt.confirm(choices[index].id)) }
            }
            genericPromptDialog = builder.create().also { it.show() }
            return result
        }

        override fun onFilePrompt(
            session: GeckoSession, prompt: GeckoSession.PromptDelegate.FilePrompt
        ): GeckoResult<GeckoSession.PromptDelegate.PromptResponse> {
            if (!started || !UrlPolicy.isTrustedMediaOrigin(activeUrl) ||
                pendingFileResult != null || pendingDownload != null ||
                prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.FOLDER) {
                return GeckoResult.fromValue(prompt.dismiss())
            }
            val result = GeckoResult<GeckoSession.PromptDelegate.PromptResponse>()
            pendingFilePrompt = prompt
            pendingFileResult = result
            filePickerOrigin = activeUrl
            val mimes = GeckoTransferPolicy.acceptedMimeTypes(prompt.mimeTypes)
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (mimes.size == 1) mimes[0] else "*/*"
                if (mimes.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, mimes.toTypedArray())
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                    prompt.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE)
            }
            try {
                filePickerOpen = true
                @Suppress("DEPRECATION")
                startActivityForResult(intent, OPEN_DOCUMENT_CODE)
            } catch (_: ActivityNotFoundException) {
                finishFilePrompt(emptyList())
            } catch (_: SecurityException) {
                finishFilePrompt(emptyList())
            }
            return result
        }
    }

    private fun finishFilePrompt(selected: List<Uri>) {
        val result = pendingFileResult ?: return
        val prompt = pendingFilePrompt
        pendingFileResult = null
        pendingFilePrompt = null
        filePickerOpen = false
        val sameSite = filePickerOrigin == activeUrl &&
            UrlPolicy.isTrustedMediaOrigin(activeUrl)
        filePickerOrigin = null
        val maxSelected = if (prompt?.type == GeckoSession.PromptDelegate.FilePrompt.Type.MULTIPLE)
            GeckoTransferPolicy.MAX_SELECTED_FILES else 1
        val safe = selected.take(maxSelected).filter {
            GeckoTransferPolicy.isSelectedDocumentUri(it.toString())
        }
        if (prompt == null) result.completeExceptionally(
            IllegalStateException("File prompt disappeared"))
        else result.complete(if (sameSite && safe.isNotEmpty())
            prompt.confirm(applicationContext, safe.toTypedArray()) else prompt.dismiss())
    }

    private val contentDelegate = object : GeckoSession.ContentDelegate {
        override fun onCrash(session: GeckoSession) { onRendererExit(session) }
        override fun onKill(session: GeckoSession) { onRendererExit(session) }
        override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
            val stream = response.body
            if (stream == null || !started || pendingDownload != null ||
                pendingFileResult != null || !UrlPolicy.isTrustedMediaOrigin(activeUrl) ||
                !GeckoTransferPolicy.isDownloadSourceAllowed(
                    response.uri, response.isSecure, response.statusCode)) {
                try { stream?.close() } catch (_: Exception) { }
                return
            }
            response.setReadTimeoutMillis(30_000L)
            pendingDownload = response
            downloadPage = activeUrl
            val name = GeckoTransferPolicy.filename(response.uri)
            AlertDialog.Builder(this@GeckoActivity)
                .setTitle(R.string.download_confirm_title)
                .setMessage(getString(R.string.gecko_download_confirm, name))
                .setPositiveButton(R.string.confirm) { _, _ -> openSaveDocument(response, name) }
                .setNegativeButton(R.string.cancel) { _, _ -> abandonDownload() }
                .setOnCancelListener { abandonDownload() }
                .show()
        }
    }

    private fun abandonDownload() {
        val pending = pendingDownload
        pendingDownload = null
        downloadPage = null
        downloadPickerOpen = false
        try { pending?.body?.close() } catch (_: Exception) { }
    }

    private fun openSaveDocument(response: WebResponse, filename: String) {
        if (pendingDownload !== response || !started ||
            !GeckoTransferOriginPolicy.isSamePage(downloadPage, activeUrl)) {
            abandonDownload(); return
        }
        val contentType = response.headers.entries.firstOrNull {
            it.key.equals("content-type", ignoreCase = true)
        }?.value
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = GeckoTransferPolicy.mimeType(contentType)
            putExtra(Intent.EXTRA_TITLE, filename)
        }
        try {
            downloadPickerOpen = true
            @Suppress("DEPRECATION")
            startActivityForResult(intent, SAVE_DOCUMENT_CODE)
        } catch (_: ActivityNotFoundException) { abandonDownload() }
        catch (_: SecurityException) { abandonDownload() }
    }

    private fun finishDownload(target: Uri?) {
        val response = pendingDownload
        val initialPage = downloadPage
        pendingDownload = null
        downloadPage = null
        downloadPickerOpen = false
        if (response == null) return
        if (target == null || !GeckoTransferPolicy.isSelectedDocumentUri(target.toString()) ||
            !GeckoTransferOriginPolicy.isSamePage(initialPage, activeUrl)) {
            try { response.body?.close() } catch (_: Exception) { }
            return
        }
        val resolver = applicationContext.contentResolver
        val input = response.body ?: return
        // One-off work initiated by the user's Save selection; never a scheduled/background job.
        Thread({
            val success = try {
                input.use { source ->
                    val dest = resolver.openOutputStream(target, "w")
                        ?: throw java.io.IOException("Cannot open selected document")
                    dest.use { GeckoTransferPolicy.copy(source, it) }
                }
                true
            } catch (_: Exception) {
                // The target was created by ACTION_CREATE_DOCUMENT; remove partial output.
                try { resolver.delete(target, null, null) } catch (_: Exception) { }
                false
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) Toast.makeText(this@GeckoActivity,
                    if (success) R.string.gecko_download_saved else R.string.error_download,
                    Toast.LENGTH_LONG).show()
            }
        }, "companion-manual-document-save").apply { isDaemon = true; start() }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (settingsBackup.receive(requestCode, resultCode, data)) return
        if (requestCode == VoiceComposerHandoff.REQUEST) {
            voiceHandoff.receive(resultCode, data)
            if (started) deliverVoiceDraft()
        } else if (requestCode == OPEN_DOCUMENT_CODE) {
            val uris = mutableListOf<Uri>()
            if (resultCode == RESULT_OK) {
                data?.data?.let(uris::add)
                data?.clipData?.let { items ->
                    for (i in 0 until minOf(items.itemCount, GeckoTransferPolicy.MAX_SELECTED_FILES))
                        items.getItemAt(i).uri?.let(uris::add)
                }
            }
            finishFilePrompt(uris.distinct())
        } else if (requestCode == SAVE_DOCUMENT_CODE) {
            finishDownload(if (resultCode == RESULT_OK) data?.data else null)
        }
    }

    private val navigationDelegate = object : GeckoSession.NavigationDelegate {
        override fun onLoadRequest(
            session: GeckoSession,
            request: GeckoSession.NavigationDelegate.LoadRequest
        ): GeckoResult<AllowOrDeny>? {
            val newWindow = request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW
            return when (GeckoNavigationPolicy.decide(request.uri, request.hasUserGesture, newWindow)) {
                GeckoNavigationPolicy.Decision.ALLOW -> {
                    observeLoginPage(request.uri)
                    null
                }
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
            // A file chooser or download belongs to the exact page that started it.
            // Cancel immediately on navigation, even if a later redirect returns
            // to the same URL before the Android document picker finishes.
            if (pendingFileResult != null &&
                !GeckoTransferOriginPolicy.isSamePage(filePickerOrigin, url))
                finishFilePrompt(emptyList())
            if (pendingDownload != null &&
                !GeckoTransferOriginPolicy.isSamePage(downloadPage, url))
                abandonDownload()
            // A page navigation must not complete a permission granted to the
            // previously visible origin. The request will have to be repeated.
            if (permissionCallback != null &&
                !UrlPolicy.sameTrustedMediaOrigin(androidPermissionPage, url))
                revokeMediaPermissions()
            if (pendingMediaCallback != null &&
                !UrlPolicy.sameTrustedMediaOrigin(mediaPromptPage, url))
                revokeMediaPermissions()
            voiceHandoff.observe(url)
            activeUrl = url ?: HOME
            observeLoginPage(url)
            // Only store public content paths, never credentials, tokens or fragments.
            UrlPolicy.sanitizeBookmark(url)?.let { if (settings.resumePage) settings.lastVisited = it }
            val safe = UrlPolicy.safeShareUrl(url)
            locationLabel.text = if (safe != null) "Character.AI" else getString(R.string.gecko_preview_title)
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
            voiceHandoff.invalidate()
            // A fresh top-level load invalidates pending document-picker state,
            // including reloads that keep the same URL.
            if (pendingFileResult != null) finishFilePrompt(emptyList())
            if (pendingDownload != null) abandonDownload()
            observeLoginPage(url)
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
                androidPermissionPage = activeUrl
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
                !UrlPolicy.sameTrustedMediaOrigin(uri, activeUrl) || (!hasCamera && !hasMic) ||
                (hasMic && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) ||
                (hasCamera && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)) {
                callback.reject(); return
            }
            pendingMediaCallback = callback
            mediaPromptPage = activeUrl
            fun finishMedia(accepted: Boolean) {
                if (pendingMediaCallback !== callback) return // onStop may already deny it
                pendingMediaCallback = null
                val expected = mediaPromptPage
                mediaPromptPage = null
                if (accepted && started && settings.mediaAllowed &&
                    UrlPolicy.sameTrustedMediaOrigin(uri, expected) &&
                    UrlPolicy.sameTrustedMediaOrigin(expected, activeUrl))
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
        val expected = androidPermissionPage
        androidPermissionPage = null
        if (started && settings.mediaAllowed &&
            UrlPolicy.sameTrustedMediaOrigin(expected, activeUrl) &&
            grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED })
            callback.grant()
        else callback.reject()
    }

    private fun updateNavigation() {
        if (!::backButton.isInitialized) return
        backButton.isEnabled = true
        backButton.alpha = if (canGoBack) 1f else 0.4f
    }

    private fun refreshTrackingProtection() {
        if (::session.isInitialized && session.isOpen)
            session.settings.setUseTrackingProtection(settings.blocking && !loginProtection.bypass)
        updatePrivacy()
    }

    private fun observeLoginPage(url: String?) {
        loginProtection.observePage(url)
        refreshTrackingProtection()
    }

    private fun toggleLoginProtection() {
        if (!sessionReady() || !started || LoginCompatibilityPolicy.pageKey(activeUrl) == null) {
            Toast.makeText(this, R.string.login_compatibility_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        loginProtection.toggleManual(activeUrl)
        refreshTrackingProtection()
        Toast.makeText(this, if (loginProtection.bypass) R.string.login_compatibility_on
            else R.string.login_compatibility_off, Toast.LENGTH_SHORT).show()
        if (::session.isInitialized && session.isOpen) session.reload()
    }

    private fun updatePrivacy() {
        if (::privacyButton.isInitialized)
            privacyButton.text = when {
                !settings.blocking -> getString(R.string.shield_off)
                loginProtection.bypass -> getString(R.string.shield_login)
                else -> getString(R.string.shield_on)
            }
        if (::privacyButton.isInitialized) privacyButton.contentDescription =
            if (settings.blocking && loginProtection.bypass) getString(R.string.login_compatibility_on)
            else getString(if (settings.blocking) R.string.shield_enabled else R.string.shield_disabled)
    }

    private fun configureMedia() {
        AlertDialog.Builder(this)
            .setTitle(R.string.gecko_media)
            .setMessage(if (settings.mediaAllowed) R.string.gecko_media_on else R.string.gecko_media_off)
            .setPositiveButton(if (settings.mediaAllowed) R.string.disabled else R.string.enabled) { _, _ ->
                settings.mediaAllowed = !settings.mediaAllowed
                if (!settings.mediaAllowed) revokeMediaPermissions()
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

    private fun confirmWebViewSwitch() {
        AlertDialog.Builder(this).setTitle(R.string.gecko_legacy)
            .setMessage(R.string.engine_switch_warning)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ -> switchToWebView(activeUrl) }.show()
    }

    private fun switchToWebView(page: String?): Boolean {
        if (isFinishing || isDestroyed) return false
        val previous = settings.preferWebView
        settings.preferWebView = true
        return try {
            startActivity(Intent(this, MainActivity::class.java).apply {
                // Ordinary startup preserves the native hub and resume-page preference.
                if (page != null) {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, NativeWorkspaceNavigation.safeDestination(page))
                }
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
            finish()
            true
        } catch (_: RuntimeException) {
            settings.preferWebView = previous
            Toast.makeText(this, R.string.engine_switch_failed, Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun confirmBrowserSessionMode() {
        if (isFinishing || isDestroyed) return
        AlertDialog.Builder(this).setTitle(R.string.browser_mode_title)
            .setMessage(R.string.browser_mode_explanation)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.browser_mode_continue) { _, _ ->
                BrowserSessionActivity.enter(this, BrowserSessionPolicy.HOME, openNow = true)
            }.show()
    }

    private fun confirmBrowser(url: String) {
        if (UrlPolicy.isIdentityUrl(url)) {
            confirmBrowserSessionMode()
            return
        }
        // Even on explicit handoff never forward OAuth code/state/token fragments
        // to a different browser with an unrelated cookie jar.
        val target = ExternalBrowserPolicy.target(url) ?: return
        val sensitive = target != url
        AlertDialog.Builder(this).setTitle(R.string.external_link_title)
            .setMessage(if (sensitive) getString(R.string.gecko_external_sensitive)
                else getString(R.string.external_link_message, Uri.parse(target).host ?: "?"))
            .setPositiveButton(R.string.open_browser) { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target))
                        .addCategory(Intent.CATEGORY_BROWSABLE))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this, R.string.error_browser, Toast.LENGTH_SHORT).show()
                } catch (_: SecurityException) {
                    Toast.makeText(this, R.string.error_browser, Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun navigateBack() {
        if (needsRecovery) { showRecoveryPrompt(); return }
        if (::session.isInitialized && session.isOpen && canGoBack) session.goBack()
        else finish()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() { navigateBack() }

    override fun onStart() {
        super.onStart()
        started = true
        if (needsRecovery) showRecoveryPrompt()
    }

    override fun onStop() {
        ForegroundUpdateUi.cancel(this)
        // Revoke unresolved prompts when hidden; do not auto-grant on return.
        started = false
        revokeMediaPermissions()
        // The system SAF picker temporarily stops this Activity: preserve only that
        // specific user-initiated prompt until its onActivityResult callback arrives.
        if (!filePickerOpen) finishFilePrompt(emptyList())
        if (!downloadPickerOpen) abandonDownload()
        genericPromptDialog?.dismiss()
        genericPromptDialog = null
        recoveryDialog?.dismiss()
        recoveryDialog = null
        super.onStop()
    }

    private fun deliverVoiceDraft() {
        val page = if (sessionReady()) activeUrl else null
        voiceHandoff.deliver(this, if (page != null && ::surface.isInitialized) surface else null, page)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && started) deliverVoiceDraft()
    }

    override fun onResume() {
        super.onResume()
        loginProtection.setVisible(sessionReady())
        if (sessionReady()) observeLoginPage(activeUrl)
        deliverVoiceDraft()
    }

    override fun onPause() {
        loginProtection.setVisible(false)
        if (::settings.isInitialized) refreshTrackingProtection()
        super.onPause()
    }

    override fun onDestroy() {
        settingsBackup.close()
        ForegroundUpdateUi.cancel(this)
        loginProtection.setVisible(false)
        voiceHandoff.clear()
        revokeMediaPermissions()
        finishFilePrompt(emptyList())
        abandonDownload()
        genericPromptDialog?.dismiss()
        genericPromptDialog = null
        recoveryDialog?.dismiss()
        recoveryDialog = null
        if (Build.VERSION.SDK_INT >= 33) backCallback?.let {
            onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
        }
        if (::surface.isInitialized) surface.releaseSession()
        if (::session.isInitialized && session.isOpen) session.close()
        super.onDestroy()
    }
}
