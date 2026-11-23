package dev.caicompanion.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.SystemClock
import android.text.Editable
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
    private lateinit var session: GeckoSession
    private lateinit var surface: GeckoView
    private lateinit var root: LinearLayout
    private lateinit var locationLabel: TextView
    private lateinit var backButton: Button
    private lateinit var forwardButton: Button
    private lateinit var privacyButton: Button
    private lateinit var actionsRow: LinearLayout
    private val recoveryBudget = RendererRecoveryBudget(2, 120_000L)
    private var needsRecovery = false
    private var recoveryDialog: AlertDialog? = null
    private var activeUrl: String = HOME
    private var canGoBack = false
    private var canGoForward = false
    private var permissionCallback: GeckoSession.PermissionDelegate.Callback? = null
    private var pendingMediaCallback: GeckoSession.PermissionDelegate.MediaCallback? = null
    private var started = false
    private var pendingFilePrompt: GeckoSession.PromptDelegate.FilePrompt? = null
    private var pendingFileResult: GeckoResult<GeckoSession.PromptDelegate.PromptResponse>? = null
    private var filePickerOpen = false
    private var filePickerOrigin: String? = null
    private var pendingDownload: WebResponse? = null
    private var downloadPickerOpen = false
    private var genericPromptDialog: AlertDialog? = null
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
        top.addView(button("☆") { saveCurrentBookmark() }.apply {
            contentDescription = getString(R.string.bookmark_add)
        }, LinearLayout.LayoutParams(dp(48), dp(54)))
        top.addView(button("☰") { showBookmarks() }.apply {
            contentDescription = getString(R.string.bookmarks)
        }, LinearLayout.LayoutParams(dp(48), dp(54)))
        top.addView(button("⚙") { showGeckoSettings() }.apply {
            contentDescription = getString(R.string.settings)
        }, LinearLayout.LayoutParams(dp(48), dp(54)))
        root.addView(top)
        val bottom = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        backButton = button(getString(R.string.back)) { navigateBack() }
        forwardButton = button(getString(R.string.forward)) { if (canGoForward) session.goForward() }
        bottom.addView(backButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        bottom.addView(forwardButton, LinearLayout.LayoutParams(0, dp(48), 1f))
        bottom.addView(button(getString(R.string.reload)) { session.reload() }, LinearLayout.LayoutParams(0, dp(48), 1f))
        bottom.addView(button(getString(R.string.home)) { session.loadUri(HOME) }, LinearLayout.LayoutParams(0, dp(48), 1f))
        root.addView(bottom)
        actionsRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val actions = actionsRow
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
        updateCompactChrome()
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
            session.setContentDelegate(contentDelegate)
            session.setPromptDelegate(promptDelegate)
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

    /** Gecko bookmark editor never accesses page DOM, cookies or chat text. */
    private fun saveCurrentBookmark() {
        val url = UrlPolicy.sanitizeBookmark(activeUrl)
        if (url == null) {
            Toast.makeText(this, R.string.hub_bookmark_invalid, Toast.LENGTH_SHORT).show()
            return
        }
        val title = EditText(this).apply {
            setSingleLine(true)
            setText("Character.AI")
            selectAll()
        }
        AlertDialog.Builder(this).setTitle(R.string.bookmark_add).setView(title)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val saved = settings.addBookmark(title.text.toString(), url)
                Toast.makeText(this,
                    if (saved) R.string.bookmark_saved else R.string.hub_bookmark_invalid,
                    Toast.LENGTH_SHORT).show()
            }.setNegativeButton(R.string.cancel, null).show()
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
        }
        val empty = TextView(this).apply {
            setText(R.string.hub_empty)
            setTextColor(fg)
            setPadding(0, dp(12), 0, dp(10))
        }
        val entries = mutableListOf<String>()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, entries)
        val list = ListView(this).apply { this.adapter = adapter }
        layout.addView(search)
        layout.addView(empty)
        layout.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(300)))
        var visible = emptyList<Pair<String, String>>()
        val refresh: () -> Unit = {
            visible = NativeLibrarySearch.filter(settings.listBookmarks(), search.text.toString())
            adapter.clear()
            adapter.addAll(visible.map { (title, _) -> title.take(90) })
            adapter.notifyDataSetChanged()
            empty.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
        }
        val dialog = AlertDialog.Builder(this).setTitle(R.string.bookmarks)
            .setView(layout).setPositiveButton(R.string.close, null).create()
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
                AlertDialog.Builder(this).setTitle(R.string.hub_bookmark_delete)
                    .setMessage(R.string.hub_bookmark_confirm_delete)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        settings.removeBookmark(bookmark.second)
                        refresh()
                        Toast.makeText(this, R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
                    }.setNegativeButton(R.string.cancel, null).show()
            }
            true
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = refresh()
        })
        refresh()
        dialog.show()
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
        val options = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        options.addView(settingCheckbox(R.string.toggle_filter, settings.blocking) {
            settings.blocking = it
            if (::session.isInitialized && session.isOpen)
                session.settings.setUseTrackingProtection(it)
            updatePrivacy()
        })
        options.addView(settingCheckbox(R.string.toggle_media, settings.mediaAllowed) {
            settings.mediaAllowed = it
            if (!it) {
                pendingMediaCallback?.reject(); pendingMediaCallback = null
                permissionCallback?.reject(); permissionCallback = null
            }
        })
        options.addView(settingCheckbox(R.string.toggle_protect_screen, settings.protectScreen) {
            settings.protectScreen = it
            if (it) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        })
        options.addView(settingCheckbox(R.string.toggle_resume, settings.resumePage) {
            settings.resumePage = it
            if (!it) settings.lastVisited = ""
            else UrlPolicy.sanitizeBookmark(activeUrl)?.let { safe -> settings.lastVisited = safe }
        })
        options.addView(settingCheckbox(R.string.toggle_compact, settings.compactChrome) {
            settings.compactChrome = it
            updateCompactChrome()
        })
        val scroll = ScrollView(this).apply { addView(options) }
        AlertDialog.Builder(this).setTitle(R.string.settings).setView(scroll)
            .setNeutralButton(R.string.gecko_legacy) { _, _ ->
                startActivity(Intent(this, MainActivity::class.java))
            }.setPositiveButton(R.string.close, null).show()
    }

    private fun updateCompactChrome() {
        if (::actionsRow.isInitialized)
            actionsRow.visibility = if (settings.compactChrome) View.GONE else View.VISIBLE
    }

    /** User-initiated recovery, never a timer or background monitor. Form contents may be lost. */
    private fun geckoExited() {
        needsRecovery = true
        // A destroyed renderer cannot satisfy old site permissions, pending file
        // prompts or downloads. Keep no callback into the failed session.
        runCatching { pendingMediaCallback?.reject() }
        pendingMediaCallback = null
        runCatching { permissionCallback?.reject() }
        permissionCallback = null
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
                startActivity(Intent(this, MainActivity::class.java)); finish()
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
            canGoBack = false
            canGoForward = false
            updateNavigation()
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
        override fun onCrash(session: GeckoSession) { geckoExited() }
        override fun onKill(session: GeckoSession) { geckoExited() }
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
        downloadPickerOpen = false
        try { pending?.body?.close() } catch (_: Exception) { }
    }

    private fun openSaveDocument(response: WebResponse, filename: String) {
        if (pendingDownload !== response || !started) { abandonDownload(); return }
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
        pendingDownload = null
        downloadPickerOpen = false
        if (response == null) return
        if (target == null || !GeckoTransferPolicy.isSelectedDocumentUri(target.toString()) ||
            !UrlPolicy.isTrustedMediaOrigin(activeUrl)) {
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
        if (requestCode == OPEN_DOCUMENT_CODE) {
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
        if (started && settings.mediaAllowed && UrlPolicy.isTrustedMediaOrigin(activeUrl) &&
            grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED })
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
        // Revoke unresolved prompts when hidden; do not auto-grant on return.
        started = false
        pendingMediaCallback?.reject(); pendingMediaCallback = null
        permissionCallback?.reject(); permissionCallback = null
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

    override fun onDestroy() {
        pendingMediaCallback?.reject(); pendingMediaCallback = null
        permissionCallback?.reject(); permissionCallback = null
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
