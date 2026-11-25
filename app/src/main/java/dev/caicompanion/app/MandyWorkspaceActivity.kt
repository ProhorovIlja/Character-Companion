package dev.caicompanion.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ImageButton
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.GradientDrawable
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Experimental embedded chat; no HTTP endpoints or automatic model loading. */
class MandyWorkspaceActivity : Activity() {
    companion object {
        // Serialize explicitly requested disk operations across activity recreation.
        private val disk = Executors.newSingleThreadExecutor()
        private const val PICK_TEXT_DOCUMENT = 7412
        // A blocked external provider must never occupy the workspace disk executor.
        private val textReaderOccupied = AtomicBoolean(false)
    }
    private data class Snapshot(val profiles: List<NativeCharacterLibrary.Profile>,
                                val ticket: NativeChatWorkspace.Ticket?,
                                val draft: NativeChatWorkspace.State,
                                val rows: List<NativeConversation.Event>,
                                val models: List<NativeModelLibrary.Model>,
                                val memory: NativeCharacterMemory.State,
                                val speechModels: List<NativeModelLibrary.Model> = emptyList())
    private lateinit var library: NativeCharacterLibrary
    private lateinit var workspace: NativeChatWorkspace
    private lateinit var memory: NativeCharacterMemory
    private lateinit var account: String
    private lateinit var panel: LinearLayout
    private lateinit var messages: LinearLayout
    private lateinit var historyScroll: ScrollView
    private lateinit var status: TextView
    private var current: Snapshot? = null
    // Explicitly approved model for this exact selection; never persisted or reused across characters.
    private var approvedModel: Pair<NativeChatWorkspace.Ticket, String>? = null
    private var editor: EditText? = null
    private var busy = false
    private val stopped = AtomicBoolean(false)
    private val resident = ResidentModel()
    private lateinit var cancelGeneration: Button
    private val dictation = NativeDictation()
    private var speechToken: UUID? = null
    private var recognizer: SpeechRecognizer? = null
    private var offlineSpeech: OfflineSpeechSession? = null
    private var speechStates: Map<View, Boolean> = emptyMap()
    private lateinit var speechControls: LinearLayout
    private lateinit var cancelSpeechButton: Button
    private var normalActions: LinearLayout? = null
    private lateinit var stopSpeech: Button
    private lateinit var sendSpeech: Button
    private var resumed = false
    private var playbackDialog: AlertDialog? = null
    private data class TextPick(val snapshot: Snapshot, val draft: String, val start: Int, val end: Int)
    private var textPick: TextPick? = null
    private var queuedTextPick: Pair<TextPick, android.net.Uri>? = null
    private data class TextRead(val cancelled: AtomicBoolean, val states: Map<View, Boolean>)
    private var textRead: TextRead? = null
    /** Sample system memory only during explicitly requested local inference. */
    private fun admitModel(model: NativeModelLibrary.Model, contextTokens: Int) {
        val manager = applicationContext.getSystemService(android.app.ActivityManager::class.java)
            ?: throw ModelAdmission.Denied(ModelAdmission.Decision(ModelAdmission.Reason.INVALID_METRICS, 0, 0))
        val info = android.app.ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        ModelAdmission.requireAllowed(model.bytes, ModelAdmission.Memory(info.availMem, info.totalMem, info.threshold, info.lowMemory), contextTokens)
    }
    private fun admissionMessage(error: ModelAdmission.Denied): String {
        val decision = error.decision
        return when (decision.reason) {
            ModelAdmission.Reason.INVALID_METRICS -> "System memory information unavailable"
            ModelAdmission.Reason.LOW_MEMORY -> "Android reports low memory. Close other apps or select a smaller model."
            else -> "Conservative memory check refused model. Available " + decision.available / ModelAdmission.MIB +
                " MiB; estimated minimum " + decision.required / ModelAdmission.MIB + " MiB."
        }
    }
    private fun performancePrefs() = getSharedPreferences("mandy_performance_v1", MODE_PRIVATE)
    private fun performancePreset() = InferencePreset.fromKey(runCatching {
        performancePrefs().getString("preset", null)
    }.getOrNull())
    private fun choosePerformancePreset() {
        val presets = InferencePreset.entries
        val labels = presets.map { it.label + " · " + it.contextTokens + " context / " +
            it.outputTokens + " output tokens · " + it.threads + " CPU threads · " + it.contextUnits + " text units" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Local inference preset (experimental)")
            .setSingleChoiceItems(labels, presets.indexOf(performancePreset())) { dialog, index ->
                dialog.dismiss()
                saveAnd { check(performancePrefs().edit().putString("preset", presets[index].key).commit()) }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun modelLibrary() = NativeModelLibrary(applicationContext.noBackupFilesDir.toPath().resolve("mandy-models-v1"))
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun alive() = !isFinishing && !isDestroyed
    private fun appearancePrefs() = getSharedPreferences("mandy_appearance_v1", MODE_PRIVATE)
    private fun amoled() = (appearancePrefs().all["amoled"] as? Boolean) ?: true
    private data class ColorRole(val key: String, val title: String, val attr: Int)
    private val colorRoles = listOf(
        ColorRole("canvas", "Chat background", R.attr.shellBackground),
        ColorRole("composer", "Composer surface", R.attr.shellPanel),
        ColorRole("bubble", "Your message bubble", R.attr.shellAction),
        ColorRole("text", "Message text", R.attr.shellText),
        ColorRole("muted", "Secondary text", R.attr.shellMuted),
        ColorRole("accent", "Send button", R.attr.shellAccent),
        ColorRole("outline", "Composer border", R.attr.shellOutline),
        ColorRole("icons", "Action icons", R.attr.shellChrome)
    )
    private var customColors: Map<String, Int> = emptyMap()
    private fun contrast(a: Int, b: Int) = androidx.core.graphics.ColorUtils.calculateContrast(a, b)
    private fun paletteError(p: Map<String, Int>): String? {
        if (colorRoles.any { it.key !in p }) return "All color roles are required."
        val surfaces = listOf("canvas", "composer", "bubble")
        for (surface in surfaces) {
            if (contrast(p.getValue("text"), p.getValue(surface)) < 4.5)
                return "Message text needs 4.5:1 contrast against " + surface + "."
        }
        for (surface in listOf("canvas", "composer")) {
            if (contrast(p.getValue("muted"), p.getValue(surface)) < 4.5)
                return "Secondary text needs 4.5:1 contrast against " + surface + "."
            if (contrast(p.getValue("icons"), p.getValue(surface)) < 3.0)
                return "Action icons need 3:1 contrast against " + surface + "."
        }
        if (contrast(p.getValue("accent"), p.getValue("composer")) < 3.0)
            return "Send button needs 3:1 contrast against composer."
        return null
    }
    private fun savedPalette(): Map<String, Int> {
        val prefs = appearancePrefs()
        val p = colorRoles.mapNotNull { role ->
            val value = prefs.all["color_" + role.key] as? String
            if (value != null && value.matches(Regex("#[0-9a-fA-F]{6}")))
                role.key to android.graphics.Color.parseColor(value) else null
        }.toMap()
        return if (paletteError(p) == null) p else emptyMap()
    }
    private fun chatColor(attr: Int): Int {
        if (attr == R.attr.shellOnAccent && customColors.isNotEmpty()) {
            val accent = customColors.getValue("accent")
            return if (contrast(android.graphics.Color.BLACK, accent) >= contrast(android.graphics.Color.WHITE, accent))
                android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        val role = colorRoles.firstOrNull { it.attr == attr }
        return role?.let { customColors[it.key] } ?: ShellAppearance.color(this, attr)
    }
    private fun iconColor() = customColors["icons"] ?: ShellAppearance.color(this, R.attr.shellText)
    private fun colorHex(color: Int) = String.format(java.util.Locale.ROOT, "#%06X", color and 0xffffff)
    @Suppress("DEPRECATION")
    private fun applySystemBars() {
        // The root paints the same opaque canvas under the system bars; its existing
        // insets listener keeps interactive controls out of bars, cutouts and the IME.
        val canvasColor = chatColor(R.attr.shellBackground)
        val darkIcons = contrast(android.graphics.Color.BLACK, canvasColor) >=
            contrast(android.graphics.Color.WHITE, canvasColor)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT < 35) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        // minSdk 29. Retain OS protection if accessibility/device policy overrides it.
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = darkIcons
            isAppearanceLightNavigationBars = darkIcons
        }
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(canvasColor))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(if (amoled()) R.style.AppTheme_MandyAmoled else R.style.AppTheme_MandyGraphite)
        super.onCreate(savedInstanceState)
        if (appearancePrefs().all["custom"] == true) customColors = savedPalette()
        applySystemBars()
        if (SettingsStore(this).protectScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(chatColor(R.attr.shellBackground))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        status = chatLabel("Opening local workspace…", 12f).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener { AlertDialog.Builder(this@MandyWorkspaceActivity).setTitle("Workspace status").setMessage(text).setPositiveButton("Close", null).show() }
        }
        cancelGeneration = Button(this).apply {
            text = "Stop generation"; isAllCaps = false; visibility = View.GONE
            setOnClickListener {
                if (textRead != null) cancelTextRead()
                else { stopped.set(true); isEnabled = false; status.text = "Stopping…" }
            }
        }
        speechControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = View.GONE }
        stopSpeech = Button(this).apply {
            text = "Stop · keep draft"; isAllCaps = false
            setOnClickListener { finishSpeech(false) }
        }
        sendSpeech = Button(this).apply {
            text = "Stop · send"; isAllCaps = false
            setOnClickListener { finishSpeech(true) }
        }
        speechControls.addView(stopSpeech, LinearLayout.LayoutParams(0, -2, 1f))
        speechControls.addView(sendSpeech, LinearLayout.LayoutParams(0, -2, 1f))
        cancelSpeechButton = Button(this).apply {
            text = "Cancel"; isAllCaps = false
            contentDescription = "Cancel dictation"
            setOnClickListener { cancelSpeech("Dictation cancelled") }
        }
        speechControls.addView(cancelSpeechButton, LinearLayout.LayoutParams(0, -2, 1f))
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        root.addView(panel, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(status)
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        work {
            val prefs = applicationContext.getSharedPreferences("mandy_local_identity_v1", MODE_PRIVATE)
            val storedAccount = prefs.getString("account", null)
            val storedDevice = prefs.getString("device", null)
            val device: String
            if (storedAccount == null && storedDevice == null) {
                account = UUID.randomUUID().toString()
                device = UUID.randomUUID().toString()
                check(prefs.edit().putString("account", account).putString("device", device).commit())
            } else {
                account = requireNotNull(storedAccount)
                device = requireNotNull(storedDevice)
            }
            val base = applicationContext.noBackupFilesDir.toPath()
            library = NativeCharacterLibrary(base.resolve("mandy-characters-v1"), account)
            memory = NativeCharacterMemory(base.resolve("mandy-memory-v1"), account)
            workspace = NativeChatWorkspace(base.resolve("mandy-workspace-v1"), account, device)
        }
    }
    private fun snapshot(): Snapshot {
        val profiles = library.list()
        val ticket = workspace.selected()
        val models = if (BuildConfig.MANDY_EMBEDDED_PREVIEW) modelLibrary().list() else emptyList()
        val speechModels = if (BuildConfig.MANDY_WHISPER_PREVIEW) AndroidModelImport.library(applicationContext, speech = true).list() else emptyList()
        if (ticket == null) return Snapshot(profiles, null, NativeChatWorkspace.State(), emptyList(), models, NativeCharacterMemory.State(), speechModels)
        library.resolve(ticket.key)
        val rows = AndroidConversationStore(applicationContext, account).use {
            it.page(ticket.key.character, ticket.key.chat, 50).asReversed()
        }
        return Snapshot(profiles, ticket, workspace.state(ticket.key), rows, models, memory.get(ticket.key.character), speechModels)
    }
    private fun suspendInputs(view: View, states: MutableMap<View, Boolean>) {
        states[view] = view.isEnabled
        view.isEnabled = false
        if (view is ViewGroup) for (i in 0 until view.childCount) suspendInputs(view.getChildAt(i), states)
    }
    private fun work(afterSuccess: (Snapshot) -> Unit = {}, action: () -> Unit) {
        if (busy || !alive()) return
        busy = true
        val states = mutableMapOf<View, Boolean>()
        suspendInputs(panel, states)
        status.text = "Opening local data…"
        disk.execute {
            val result = runCatching { action(); snapshot() }
            runOnUiThread {
                if (!alive()) return@runOnUiThread
                busy = false
                result.onSuccess {
                    if (approvedModel?.first != it.ticket || it.models.none { model -> model.sha256 == approvedModel?.second }) approvedModel = null
                    current = it
                    render(it)
                    status.text = if (BuildConfig.MANDY_EMBEDDED_PREVIEW) "Local workspace · experimental inference" else "Local workspace • embedded build unavailable"
                    afterSuccess(it)
                }.onFailure {
                    // Preserve the visible editor; do not reset or overwrite damaged files.
                    status.text = "Local operation failed. Saved data has not been reset."
                    states.forEach { (view, enabled) -> view.isEnabled = enabled }
                }
            }
        }
    }
    private fun label(value: String, size: Float = 16f) = TextView(this).apply {
        text = value; textSize = size
        setTextColor(ShellAppearance.color(this@MandyWorkspaceActivity, R.attr.shellText))
        setPadding(dp(8), dp(8), dp(8), dp(8))
        isSaveEnabled = false
    }
    private fun chatLabel(value: String, size: Float = 16f) = label(value, size).apply { setTextColor(chatColor(R.attr.shellText)) }
    private fun input(hintText: String, limit: Int, value: String = "", single: Boolean = false) = EditText(this).apply {
        hint = hintText; setSingleLine(single); setText(value)
        filters = arrayOf(InputFilter.LengthFilter(limit))
        imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        isSaveEnabled = false
        if (!single) { minLines = 3; maxLines = 8 }
    }
    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; minHeight = dp(48)
        setOnClickListener { if (!busy && alive()) action() }
    }
    // Original vector geometry, no bitmap bundle or third-party icon dependency.
    private class ChatGlyph(private val kind: String, color: Int) : Drawable() {
        private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val path = android.graphics.Path()
        override fun draw(canvas: Canvas) {
            val scale = minOf(bounds.width(), bounds.height()) / 24f
            if (scale <= 0f) return
            val save = canvas.save()
            canvas.translate(bounds.exactCenterX() - 12f * scale, bounds.exactCenterY() - 12f * scale)
            canvas.scale(scale, scale)
            fun l(x: Float, y: Float, xx: Float, yy: Float) = canvas.drawLine(x, y, xx, yy, ink)
            when (kind) {
                "menu" -> { l(4f,6f,20f,6f); l(4f,12f,20f,12f); l(4f,18f,15f,18f) }
                "call" -> {
                    path.reset(); path.moveTo(4f,4f); path.lineTo(8f,3f); path.lineTo(11f,8f)
                    path.lineTo(8f,11f); path.quadTo(10f,15f,14f,17f); path.lineTo(17f,14f)
                    path.lineTo(21f,17f); path.lineTo(20f,21f); path.cubicTo(10f,21f,3f,14f,4f,4f)
                    path.close(); canvas.drawPath(path,ink)
                }
                "settings" -> {
                    l(4f,6f,6f,6f); l(10f,6f,20f,6f); l(4f,12f,14f,12f); l(18f,12f,20f,12f)
                    l(4f,18f,8f,18f); l(12f,18f,20f,18f)
                    canvas.drawCircle(8f,6f,2f,ink); canvas.drawCircle(16f,12f,2f,ink); canvas.drawCircle(10f,18f,2f,ink)
                }
                "attach" -> {
                    path.reset(); path.moveTo(8f,13f); path.lineTo(14f,7f); path.cubicTo(17f,4f,21f,8f,18f,11f)
                    path.lineTo(10f,19f); path.cubicTo(5f,24f,-1f,17f,4f,12f); path.lineTo(13f,3f)
                    canvas.drawPath(path,ink)
                }
                "mic" -> {
                    canvas.drawRoundRect(9f,3f,15f,14f,3f,3f,ink)
                    canvas.drawArc(5f,7f,19f,18f,0f,180f,false,ink)
                    l(12f,18f,12f,21f); l(8f,21f,16f,21f)
                }
                "copy" -> { canvas.drawRoundRect(8f,8f,21f,21f,2f,2f,ink); l(3f,16f,3f,3f); l(3f,3f,16f,3f) }
                "speaker" -> { l(3f,9f,7f,9f); l(7f,9f,12f,5f); l(12f,5f,12f,19f); l(12f,19f,7f,15f); l(7f,15f,3f,15f); l(3f,15f,3f,9f); canvas.drawArc(11f,7f,21f,17f,-60f,120f,false,ink) }
                "send" -> { l(12f,20f,12f,4f); l(5f,11f,12f,4f); l(12f,4f,19f,11f) }
                "retry" -> { canvas.drawArc(4f,4f,20f,20f,25f,300f,false,ink); l(20f,3f,20f,9f); l(14f,9f,20f,9f) }
            }
            canvas.restoreToCount(save)
        }
        override fun setAlpha(alpha: Int) { ink.alpha = alpha; invalidateSelf() }
        override fun setColorFilter(colorFilter: ColorFilter?) { ink.colorFilter = colorFilter; invalidateSelf() }
        @Suppress("DEPRECATION")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        override fun getIntrinsicWidth(): Int = 24
        override fun getIntrinsicHeight(): Int = 24
    }
    private fun chatAction(kind: String, description: String, available: Boolean = true, action: () -> Unit) =
        ImageButton(this).apply {
            val primary = kind == "send" || kind == "retry"
            val color = if (primary && available) chatColor(R.attr.shellOnAccent) else iconColor()
            val mask = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(android.graphics.Color.WHITE) }
            val fill = if (primary && available) GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(chatColor(R.attr.shellAccent))
            } else null
            background = RippleDrawable(ColorStateList.valueOf((color and 0x00ffffff) or 0x22000000), fill, mask)
            setImageDrawable(ChatGlyph(kind, color))
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            minimumWidth = dp(48); minimumHeight = dp(48)
            contentDescription = description
            tooltipText = description
            isEnabled = available
            alpha = if (available) 1f else .42f
            setOnClickListener { if (available && !busy && alive()) action() }
        }
    private fun saveAnd(action: () -> Unit) {
        val ticket = current?.ticket
        val text = editor?.text?.toString()
        work {
            if (ticket != null && text != null) workspace.edit(ticket, text)
            action()
        }
    }
    private fun roundedSurface(fill: Int, radius: Int, outlined: Boolean = false) = GradientDrawable().apply {
        setColor(chatColor(fill))
        cornerRadius = dp(radius).toFloat()
        if (outlined) setStroke(dp(1), chatColor(R.attr.shellOutline))
    }
    // One composer action region; no extra recording or generation toolbar below the chat.
    private fun composerMode(recording: Boolean = false, generating: Boolean = false) {
        normalActions?.visibility = if (recording || generating) View.GONE else View.VISIBLE
        speechControls.visibility = if (recording) View.VISIBLE else View.GONE
        cancelGeneration.visibility = if (generating) View.VISIBLE else View.GONE
        if (recording) { speechControls.isEnabled = true; cancelSpeechButton.isEnabled = true }
    }
    private fun render(s: Snapshot) {
        (cancelGeneration.parent as? ViewGroup)?.removeView(cancelGeneration)
        (speechControls.parent as? ViewGroup)?.removeView(speechControls)
        panel.removeAllViews()
        normalActions = null
        editor = null
        val ticket = s.ticket
        val profile = ticket?.let { t -> s.profiles.first { it.id == t.key.character } }
        val chat = ticket?.let { t -> profile?.chats?.first { it.id == t.key.chat } }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val libraryButton = chatAction("menu", "Characters and conversations") {
            if (s.profiles.isEmpty()) editProfile(null) else choose(s)
        }
        header.addView(libraryButton)
        header.addView(chatLabel(profile?.name ?: "No character", 18f).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        // Visible capability placeholders, never request permission or simulate a call.
        header.addView(chatAction("call", "Voice call not available in this build", false) {})
        header.addView(chatAction("settings", "Settings") { showWorkspaceSettings(s) })
        panel.addView(header)
        if (chat != null) panel.addView(chatLabel(chat.title, 14f))
        historyScroll = ScrollView(this).apply { isFillViewport = true }
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        historyScroll.addView(messages)
        panel.addView(historyScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (ticket == null || profile == null) {
            messages.addView(chatLabel("Create or select a character to begin."))
            messages.addView(button("Create character") { editProfile(null) })
            return
        }
        if (s.rows.isNotEmpty()) messages.addView(button("Earlier messages") { browseHistory(s, profile.name) })
        if (s.rows.isEmpty()) messages.addView(chatLabel("No messages yet."))
        else s.rows.forEach {
            val author = if (it.role == "user") "You" else profile.name
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(12)) }
            if (it.role == "user") row.background = roundedSurface(R.attr.shellAction, 20)
            row.addView(chatLabel(author, 13f).apply { setPadding(0, 0, 0, dp(4)); typeface = android.graphics.Typeface.DEFAULT_BOLD })
            row.addView(chatLabel(it.text).apply { setPadding(0, 0, 0, 0); setTextIsSelectable(true); setLineSpacing(dp(3).toFloat(), 1f) })
            if (it.role == "character") {
                val reply = it.text
                row.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(chatAction("copy", "Copy reply") {
                        if (resumed && current === s) {
                            runCatching {
                                val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                                    ?: error("Clipboard unavailable")
                                val clip = android.content.ClipData.newPlainText("Mandy reply", reply)
                                clip.description.extras = android.os.PersistableBundle().apply {
                                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                                }
                                clipboard.setPrimaryClip(clip)
                            }.onSuccess { status.text = "Reply copied" }
                                .onFailure { status.text = "Clipboard unavailable" }
                        }
                    })
                    addView(chatAction("speaker", "Read reply aloud") { chooseReadAloud(s, reply) })
                })
            }
            messages.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                if (it.role == "user") marginStart = dp(36) else marginEnd = dp(24)
                topMargin = dp(4); bottomMargin = dp(4)
            })
        }
        if (s.draft.pending != null) {
            messages.addView(chatLabel("Pending message • " + s.draft.pending.text))
            messages.addView(button("Recover saved reply") {
                val draft = editor?.text?.toString() ?: return@button
                if (!resumed || busy || !alive() || current !== s) return@button
                var recovered = false
                work(afterSuccess = {
                    status.text = if (recovered) "Saved reply recovered · no model used"
                        else "No completed reply found · pending message retained"
                }) {
                    workspace.edit(ticket, draft)
                    AndroidConversationStore(applicationContext, account).use { history ->
                        recovered = workspace.recoverCommitted(ticket, s.draft.pending.id, history) != null
                    }
                }
            })
            messages.addView(button("Discard pending retry") {
                AlertDialog.Builder(this).setMessage("Discard retry state? Already committed messages remain in history.")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("Discard") { _, _ -> saveAnd { workspace.discardPending(ticket, s.draft.pending.id) } }.show()
            })
        }
        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedSurface(R.attr.shellPanel, 24, outlined = true)
            setPadding(dp(8), dp(6), dp(8), dp(4))
        }
        val field = input("Message " + profile.name, 16000, s.draft.draft).apply {
            minLines = 1; maxLines = 4
            background = null
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setTextColor(chatColor(R.attr.shellText))
            setHintTextColor(chatColor(R.attr.shellMuted))
        }
        editor = field
        composer.addView(field)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        actions.addView(chatAction("attach", "Import text document", s.draft.pending == null) { pickTextDocument(s) })
        actions.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        actions.addView(chatAction("mic", "Dictate", s.draft.pending == null) {
            if (BuildConfig.MANDY_WHISPER_PREVIEW && s.speechModels.isNotEmpty()) {
                AlertDialog.Builder(this).setTitle("Dictation")
                    .setItems(arrayOf("On-device speech recognizer", "Local Whisper")) { _, i ->
                        if (i == 0) chooseSpeechLanguage(s) else chooseOfflineSpeech(s)
                    }.setNegativeButton(android.R.string.cancel, null).show()
            } else chooseSpeechLanguage(s)
        })
        val canSend = BuildConfig.MANDY_EMBEDDED_PREVIEW && s.models.isNotEmpty()
        val sendAction = chatAction(if (s.draft.pending == null) "send" else "retry",
            if (!canSend) "Send unavailable: local model required" else if (s.draft.pending == null) "Send message" else "Retry pending message",
            canSend) { chooseModel(s) }
        fun updateSendAction() {
            val enabled = canSend && !busy && (s.draft.pending != null || field.text.isNotBlank())
            sendAction.isEnabled = enabled
            sendAction.alpha = if (enabled) 1f else .42f
        }
        field.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) { updateSendAction() }
            override fun afterTextChanged(text: android.text.Editable?) = Unit
        })
        updateSendAction()
        actions.addView(sendAction)
        normalActions = actions
        composer.addView(actions)
        composer.addView(cancelGeneration, LinearLayout.LayoutParams(-1, -2))
        composer.addView(speechControls, LinearLayout.LayoutParams(-1, -2))
        composerMode()
        panel.addView(composer)
        if (!BuildConfig.MANDY_EMBEDDED_PREVIEW || s.models.isEmpty())
            panel.addView(chatLabel("Local model required for Send", 12f))
        // Scroll only when displaying a fresh snapshot, not on every token.
        val scroll = historyScroll
        scroll.post { if (alive() && historyScroll === scroll) scroll.scrollTo(0, (messages.height - scroll.height).coerceAtLeast(0)) }
    }

    private data class NamedPalette(val name: String, val colors: Map<String, Int>)
    private val savedThemePrefix = "saved_theme_v1_"
    private fun validThemeName(name: String): Boolean = name.length in 1..80 && name == name.trim() &&
        name.none { Character.isISOControl(it) || it == '\uFFFE' || it == '\uFFFF' } &&
        runCatching { NativeConversation.validateText(name) }.isSuccess
    private fun activeThemeName(): String =
        (appearancePrefs().all["custom_name"] as? String)?.takeIf(::validThemeName) ?: "Custom"
    private fun namedPalettes(): List<NamedPalette> = appearancePrefs().all.entries
        .filter { it.key.startsWith(savedThemePrefix) }
        .take(16)
        .mapNotNull { (key, value) ->
            (value as? String)?.let(::decodePalette)?.takeIf { key == savedThemePrefix + it.name }
        }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
    private fun applyAppearance(custom: Map<String, Int>? = null, black: Boolean = amoled(),
                                name: String = "Custom", saveNamed: Boolean = false) {
        if (busy || !alive()) return
        val ticket = current?.ticket
        val draft = editor?.text?.toString()
        work(afterSuccess = { if (alive()) recreate() }) {
            if (ticket != null && draft != null) workspace.edit(ticket, draft)
            val edit = appearancePrefs().edit().putBoolean("amoled", black).putBoolean("custom", custom != null)
            if (custom != null) {
                require(validThemeName(name) && paletteError(custom) == null)
                custom.forEach { (key, color) -> edit.putString("color_" + key, colorHex(color)) }
                edit.putString("custom_name", name)
                if (saveNamed) {
                    val key = savedThemePrefix + name
                    val keys = appearancePrefs().all.keys.filter { it.startsWith(savedThemePrefix) }
                    require(key in keys || keys.size < 16) { "Theme collection is full" }
                    edit.putString(key, encodePalette(custom, name))
                }
            }
            check(edit.commit())
        }
    }
    private fun encodePalette(p: Map<String, Int>, name: String): String {
        require(validThemeName(name) && paletteError(p) == null)
        val colors = org.json.JSONObject()
        colorRoles.forEach { colors.put(it.key, colorHex(p.getValue(it.key))) }
        return org.json.JSONObject().put("schema", 1).put("name", name).put("colors", colors).toString(2)
    }
    private fun decodePalette(raw: String): NamedPalette? = runCatching {
        require(raw.length in 1..2048)
        val allowed = colorRoles.map { it.key }.toSet()
        val p = linkedMapOf<String, Int>()
        val seen = mutableSetOf<String>()
        var name = ""
        android.util.JsonReader(java.io.StringReader(raw)).use { reader ->
            reader.isLenient = false
            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                require(seen.add(key))
                when (key) {
                    "schema" -> {
                        require(reader.peek() == android.util.JsonToken.NUMBER)
                        require(reader.nextString() == "1")
                    }
                    "name" -> {
                        require(reader.peek() == android.util.JsonToken.STRING)
                        name = reader.nextString()
                        require(validThemeName(name))
                    }
                    "colors" -> {
                        reader.beginObject()
                        while (reader.hasNext()) {
                            val role = reader.nextName()
                            require(role in allowed && role !in p)
                            require(reader.peek() == android.util.JsonToken.STRING)
                            val value = reader.nextString()
                            require(value.matches(Regex("#[0-9a-fA-F]{6}")))
                            p[role] = android.graphics.Color.parseColor(value)
                        }
                        reader.endObject()
                    }
                    else -> error("Unknown theme field")
                }
            }
            reader.endObject()
            require(reader.peek() == android.util.JsonToken.END_DOCUMENT)
        }
        require(seen == setOf("schema", "name", "colors") && p.keys == allowed)
        require(paletteError(p) == null)
        NamedPalette(name, p.toMap())
    }.getOrNull()
    /** Edit one staged color; no preference or draft writes until the parent Apply action. */
    private fun chooseRoleColor(role: ColorRole, field: EditText, fallback: Int) {
        val raw = field.text.toString().trim()
        val initial = if (raw.matches(Regex("#[0-9a-fA-F]{6}"))) android.graphics.Color.parseColor(raw) else fallback
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val preview = TextView(this).apply {
            gravity = android.view.Gravity.CENTER
            textSize = 18f
            minHeight = dp(64)
        }
        body.addView(preview, LinearLayout.LayoutParams(-1, -2))
        val channels = intArrayOf(android.graphics.Color.red(initial),
            android.graphics.Color.green(initial), android.graphics.Color.blue(initial))
        val names = arrayOf("Red", "Green", "Blue")
        fun selectedColor() = android.graphics.Color.rgb(channels[0], channels[1], channels[2])
        fun refresh() {
            val color = selectedColor()
            preview.text = colorHex(color)
            preview.setTextColor(if (contrast(android.graphics.Color.BLACK, color) >=
                contrast(android.graphics.Color.WHITE, color)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            preview.background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(color)
                setStroke(dp(1), ShellAppearance.color(this@MandyWorkspaceActivity, R.attr.shellOutline))
            }
        }
        for (index in channels.indices) {
            val channelLabel = label(names[index] + " · " + channels[index], 14f)
            body.addView(channelLabel)
            body.addView(android.widget.SeekBar(this).apply {
                max = 255
                progress = channels[index]
                minHeight = dp(48)
                contentDescription = names[index] + " channel"
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: android.widget.SeekBar?, value: Int, fromUser: Boolean) {
                        channels[index] = value
                        channelLabel.text = names[index] + " · " + value
                        refresh()
                    }
                    override fun onStartTrackingTouch(bar: android.widget.SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: android.widget.SeekBar?) = Unit
                })
            }, LinearLayout.LayoutParams(-1, -2))
        }
        refresh()
        AlertDialog.Builder(this).setTitle(role.title)
            .setView(ScrollView(this).apply { addView(body) })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Use color") { _, _ -> field.setText(colorHex(selectedColor())) }
            .show()
    }
    private fun editPalette(initial: NamedPalette? = null) {
        val saved = initial?.colors ?: savedPalette()
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val nameField = input("Theme name", 80, initial?.name ?: activeThemeName(), single = true)
        fields.addView(label("Theme name", 14f))
        fields.addView(nameField)
        val editors = colorRoles.associateWith { role ->
            val base = if (role.key == "icons") iconColor() else chatColor(role.attr)
            val field = input(role.title, 7, colorHex(saved[role.key] ?: base), single = true)
            fields.addView(label(role.title, 14f))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val swatch = View(this).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                contentDescription = "Choose " + role.title
                isFocusable = true
                setOnClickListener { chooseRoleColor(role, field, base) }
            }
            fun refreshSwatch() {
                val value = field.text.toString().trim()
                val valid = value.matches(Regex("#[0-9a-fA-F]{6}"))
                swatch.background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(if (valid) android.graphics.Color.parseColor(value) else android.graphics.Color.TRANSPARENT)
                    setStroke(dp(1), ShellAppearance.color(this@MandyWorkspaceActivity, R.attr.shellOutline))
                }
                swatch.alpha = if (valid) 1f else .35f
            }
            field.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) { refreshSwatch() }
                override fun afterTextChanged(text: android.text.Editable?) = Unit
            })
            row.addView(swatch, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(12) })
            row.addView(field, LinearLayout.LayoutParams(0, -2, 1f))
            fields.addView(row)
            refreshSwatch()
            field
        }
        val warning = label("Tap a color swatch or enter #RRGGBB. Apply saves the named theme to your local collection (16 maximum).", 13f)
        fields.addView(warning)
        fun candidate(): Map<String, Int>? {
            val p = mutableMapOf<String, Int>()
            for ((role, field) in editors) {
                val value = field.text.toString().trim()
                if (!value.matches(Regex("#[0-9a-fA-F]{6}"))) {
                    field.error = "Enter #RRGGBB"; return null
                }
                p[role.key] = android.graphics.Color.parseColor(value)
            }
            val error = paletteError(p)
            if (error != null) { warning.text = error; return null }
            return p
        }
        fields.addView(button("Copy theme JSON") {
            val p = candidate()
            val name = nameField.text.toString().trim()
            if (!validThemeName(name)) { nameField.error = "Enter a valid theme name"; return@button }
            if (p != null) {
                val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                if (clipboard != null) {
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Mandy theme", encodePalette(p, name)))
                    warning.text = "Theme copied. No chat or account data included."
                }
            }
        })
        fields.addView(button("Import theme JSON") {
            val source = input("Paste theme JSON", 2048)
            val importer = AlertDialog.Builder(this).setTitle("Import theme").setView(source)
                .setNegativeButton("Cancel", null).setPositiveButton("Load fields", null).create()
            importer.show()
            importer.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p = decodePalette(source.text.toString())
                if (p == null) { source.error = "Invalid theme or insufficient contrast"; return@setOnClickListener }
                nameField.setText(p.name)
                editors.forEach { (role, field) -> field.setText(colorHex(p.colors.getValue(role.key))) }
                warning.text = "Theme loaded into fields. Apply to save."
                importer.dismiss()
            }
        })
        val dialog = AlertDialog.Builder(this).setTitle("Custom colors")
            .setView(ScrollView(this).apply { addView(fields) })
            .setNegativeButton("Cancel", null).setNeutralButton("Reset fields", null)
            .setPositiveButton("Apply", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            editors.forEach { (role, field) ->
                val base = ShellAppearance.color(this, if (role.key == "icons") R.attr.shellText else role.attr)
                field.setText(colorHex(base))
            }
            warning.text = "Preset colors restored to fields. Apply to save."
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val p = candidate() ?: return@setOnClickListener
            val name = nameField.text.toString().trim()
            if (!validThemeName(name)) { nameField.error = "Enter a valid theme name"; return@setOnClickListener }
            val key = savedThemePrefix + name
            val prefs = appearancePrefs().all
            if (key !in prefs && prefs.keys.count { it.startsWith(savedThemePrefix) } >= 16) {
                warning.text = "Theme collection is full. Delete a saved theme or use an existing name."
                return@setOnClickListener
            }
            fun save() {
                dialog.dismiss()
                applyAppearance(p.toMap(), name = name, saveNamed = true)
            }
            if (key in prefs && (prefs[key] as? String)?.let(::decodePalette)?.colors != p) {
                AlertDialog.Builder(this).setTitle("Replace saved theme?")
                    .setMessage(name).setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("Replace") { _, _ -> save() }.show()
            } else save()
        }
    }
    private fun chooseSavedPalette() {
        val themes = namedPalettes()
        if (themes.isEmpty()) { status.text = "No saved themes yet. Use Custom colors to create one."; return }
        AlertDialog.Builder(this).setTitle("Saved themes")
            .setItems(themes.map { it.name }.toTypedArray()) { _, index ->
                val theme = themes[index]
                AlertDialog.Builder(this).setTitle(theme.name)
                    .setItems(arrayOf("Apply", "Edit or copy", "Delete from library")) { _, action ->
                        when (action) {
                            0 -> applyAppearance(theme.colors, name = theme.name)
                            1 -> editPalette(theme)
                            2 -> AlertDialog.Builder(this).setTitle("Delete saved theme?")
                                .setMessage("The current appearance is not changed.")
                                .setNegativeButton(android.R.string.cancel, null)
                                .setPositiveButton("Delete") { _, _ ->
                                    saveAnd { check(appearancePrefs().edit().remove(savedThemePrefix + theme.name).commit()) }
                                }.show()
                        }
                    }.setNegativeButton(android.R.string.cancel, null).show()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun chooseAppearance() {
        val active = if (customColors.isNotEmpty()) 2 else if (amoled()) 1 else 0
        AlertDialog.Builder(this).setTitle("Appearance")
            .setSingleChoiceItems(arrayOf("Graphite dark", "Material AMOLED", "Custom colors", "Saved themes"), active) { dialog, index ->
                dialog.dismiss()
                if (index == 2) editPalette()
                else if (index == 3) chooseSavedPalette()
                else if (index != active) applyAppearance(black = index == 1)
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun showWorkspaceSettings(s: Snapshot) {
        val options = mutableListOf<Pair<String, () -> Unit>>()
        options += ("Appearance · " + if (customColors.isNotEmpty()) activeThemeName() else if (amoled()) "Material AMOLED" else "Graphite dark") to { chooseAppearance() }
        options += "AI models" to {
            if (editor != null && editor?.text?.toString() != current?.draft?.draft)
                status.text = "Save the current draft before opening models."
            else startActivity(Intent(this, MandyModelActivity::class.java))
        }
        if (BuildConfig.MANDY_EMBEDDED_PREVIEW) {
            options += ("Performance · " + performancePreset().label) to { choosePerformancePreset() }
            options += "Release loaded model" to { saveAnd { resident.close() } }
            options += "Refresh model inventory" to { saveAnd {} }
            if (approvedModel != null) options += "Forget approved chat model" to {
                approvedModel = null
                status.text = "Model approval cleared. Choose a model on the next Send."
            }
        }
        options += "Create character" to { editProfile(null) }
        val profile = s.ticket?.let { t -> s.profiles.first { it.id == t.key.character } }
        if (profile != null) {
            options += "Edit character" to { editProfile(profile) }
            options += "Memory & relationship" to { editMemory(profile, s.memory) }
            options += "New conversation" to { createChat(profile) }
            options += "Rename conversation" to { renameChat(s, profile) }
            options += "Save draft" to { saveAnd {} }
            if (BuildConfig.MANDY_EMBEDDED_PREVIEW && s.models.isNotEmpty() &&
                s.rows.lastOrNull()?.role == "character" && s.draft.pending == null) {
                options += "Review relationship from dialogue" to { chooseAssessmentModel(s) }
                if (s.memory.memories.size < NativeCharacterMemory.MAX_MEMORIES)
                    options += "Review memory from dialogue" to { chooseAssessmentModel(s, forMemory = true) }
            }
        }
        AlertDialog.Builder(this).setTitle("Mandy AI settings")
            .setItems(options.map { it.first }.toTypedArray()) { _, i -> if (!busy && alive()) options[i].second() }
            .setNegativeButton(android.R.string.cancel, null).show()
    }
    /** Explicit, bounded history pages. Does not rebuild the main composer or change its draft. */
    private fun browseHistory(s: Snapshot, characterName: String) {
        val ticket = s.ticket ?: return
        if (!resumed || busy || !alive() || current?.ticket != ticket) return
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(8), dp(12), dp(8)) }
        val message = label("Opening history…", 14f)
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(rows) }
        val older = Button(this).apply { text = "Older messages"; isAllCaps = false; isEnabled = false }
        val latest = Button(this).apply { text = "Latest messages"; isAllCaps = false; isEnabled = false }
        container.addView(message)
        container.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)))
        container.addView(older)
        container.addView(latest)
        val dialog = AlertDialog.Builder(this).setTitle("Conversation history")
            .setView(container).setNegativeButton("Close", null).create()
        var next: Pair<Long, String>? = null
        var loading = false
        fun load(before: Pair<Long, String>?) {
            if (loading || !dialog.isShowing || !resumed || current?.ticket != ticket) return
            loading = true; older.isEnabled = false; latest.isEnabled = false
            message.text = "Opening history…"
            disk.execute {
                val result = runCatching {
                    require(workspace.visible(ticket))
                    AndroidConversationStore(applicationContext, account).use {
                        it.page(ticket.key.character, ticket.key.chat, 31, before)
                    }
                }
                runOnUiThread {
                    if (!alive() || !dialog.isShowing) return@runOnUiThread
                    if (!resumed || current?.ticket != ticket) { dialog.dismiss(); return@runOnUiThread }
                    loading = false
                    latest.isEnabled = true
                    result.onSuccess { page ->
                        val shown = page.take(30)
                        next = if (page.size > 30) shown.last().let { it.clock to it.id } else null
                        rows.removeAllViews()
                        shown.asReversed().forEach {
                            val author = if (it.role == "user") "You" else characterName
                            rows.addView(label(author + "\n" + it.text).apply { setTextIsSelectable(true) })
                        }
                        scroll.scrollTo(0, 0)
                        message.text = if (shown.isEmpty()) "No messages on this page" else
                            if (next == null) "Oldest messages reached" else "Showing up to 30 messages"
                        older.isEnabled = next != null
                    }.onFailure {
                        message.text = "History page unavailable. Existing data preserved."
                        older.isEnabled = next != null
                    }
                }
            }
        }
        older.setOnClickListener { next?.let { load(it) } }
        latest.setOnClickListener { load(null) }
        dialog.show()
        load(null)
    }

    @Suppress("DEPRECATION")
    private fun pickTextDocument(s: Snapshot) {
        val field = editor ?: return
        if (!resumed || busy || !alive() || textPick != null || queuedTextPick != null || current !== s || s.ticket == null || s.draft.pending != null) return
        if (textReaderOccupied.get()) { status.text = "Previous document provider is still closing. Try again when it returns."; return }
        val draft = field.text.toString()
        val a = field.selectionStart
        val b = field.selectionEnd
        val start = if (a < 0 || b < 0) draft.length else minOf(a, b)
        val end = if (a < 0 || b < 0) draft.length else maxOf(a, b)
        textPick = TextPick(s, draft, start, end)
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, PICK_TEXT_DOCUMENT)
        } catch (_: Exception) {
            textPick = null
            status.text = "Document picker unavailable"
        }
    }

    @Deprecated("Activity result retained for the native Activity and scoped document picker")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_TEXT_DOCUMENT) return
        val pick = textPick ?: return
        textPick = null
        if (resultCode != RESULT_OK || busy || !alive() || current !== pick.snapshot ||
            editor?.text?.toString() != pick.draft) return
        val uri = data?.data ?: return
        if (uri.scheme != "content" || (data.clipData?.itemCount ?: 0) > 1) {
            status.text = "Select one readable text document"
            return
        }
        // Android can deliver the picker result before onResume.
        queuedTextPick = pick to uri
        if (resumed) startQueuedTextRead()
    }

    private fun startQueuedTextRead() {
        val (pick, uri) = queuedTextPick ?: return
        queuedTextPick = null
        if (!resumed || busy || !alive() || current !== pick.snapshot || editor?.text?.toString() != pick.draft) return
        if (!textReaderOccupied.compareAndSet(false, true)) {
            status.text = "Previous document provider is still closing"
            return
        }
        val resolver = applicationContext.contentResolver
        val states = mutableMapOf<View, Boolean>()
        suspendInputs(panel, states)
        val read = TextRead(AtomicBoolean(false), states)
        textRead = read
        busy = true
        composerMode(generating = true)
        cancelGeneration.text = "Cancel import"
        cancelGeneration.isEnabled = true
        status.text = "Reading selected text document…"
        try {
            Thread({
                val result = try {
                    runCatching {
                        if (read.cancelled.get()) throw java.io.InterruptedIOException()
                        resolver.openInputStream(uri)?.use { stream ->
                            NativeTextAttachment.read(stream, read.cancelled::get)
                        } ?: error("Document unavailable")
                    }
                } finally { textReaderOccupied.set(false) }
                runOnUiThread {
                    if (!alive() || textRead !== read) return@runOnUiThread
                    finishTextRead(read)
                    if (read.cancelled.get() || !resumed || current !== pick.snapshot || editor?.text?.toString() != pick.draft) {
                        status.text = "Document import cancelled; draft preserved"
                        return@runOnUiThread
                    }
                    val text = result.getOrNull()
                    if (text == null) {
                        status.text = "Cannot import document. Use UTF-8 plain text (6,000 characters maximum)."
                        return@runOnUiThread
                    }
                    reviewTextDocument(pick, text)
                }
            }, "mandy-document-reader").start()
        } catch (_: Exception) {
            textReaderOccupied.set(false)
            finishTextRead(read)
            status.text = "Document reader unavailable; draft preserved"
        }
    }

    private fun finishTextRead(read: TextRead) {
        if (textRead !== read) return
        textRead = null
        busy = false
        composerMode()
        cancelGeneration.text = "Stop generation"
        read.states.forEach { (view, enabled) -> view.isEnabled = enabled }
    }

    private fun cancelTextRead() {
        val read = textRead ?: return
        read.cancelled.set(true)
        finishTextRead(read)
        status.text = "Document import cancelled; draft preserved"
    }

    private fun reviewTextDocument(pick: TextPick, text: String) {
        val change = runCatching { NativeTextAttachment.insert(pick.draft, pick.start, pick.end, text) }.getOrNull()
        if (change == null) { status.text = "Document does not fit the draft; original draft preserved"; return }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        content.addView(label("Review the selected document. Insert adds quoted text to your draft and saves it locally. It does not send a message.", 14f))
        content.addView(label(text, 14f).apply { setTextIsSelectable(true) })
        status.text = "Text document ready for review"
        AlertDialog.Builder(this).setTitle("Import text document")
            .setView(ScrollView(this).apply { addView(content) })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Insert into draft") { _, _ ->
                val field = editor
                val ticket = pick.snapshot.ticket
                if (field != null && ticket != null && resumed && alive() && !busy &&
                    current === pick.snapshot && field.text.toString() == pick.draft) {
                    field.setText(change.text)
                    field.setSelection(change.caret)
                    work(afterSuccess = {
                        editor?.setSelection(change.caret.coerceIn(0, editor?.length() ?: 0))
                        status.text = "Document text saved to draft"
                    }) { workspace.edit(ticket, change.text) }
                }
            }.show()
    }

    /** Inspect a single local metadata page only when the user opens the selector. */
    private fun chooseTextModel(s: Snapshot, title: String, onSelect: (NativeModelLibrary.Model) -> Unit) {
        val originalDraft = editor?.text?.toString()
        fun valid() = resumed && alive() && !busy && current === s &&
            editor?.text?.toString() == originalDraft && s.ticket != null
        fun showPage(offset: Int) {
            if (!valid()) return
            if (s.models.isEmpty()) { status.text = "No imported text models"; return }
            busy = true
            val states = mutableMapOf<View, Boolean>()
            suspendInputs(panel, states)
            val previousStatus = status.text
            status.text = "Reading local model metadata…"
            disk.execute {
                val result = runCatching { ModelPickerPage.read(s.models, offset) }
                runOnUiThread {
                    if (!alive()) return@runOnUiThread
                    busy = false
                    states.forEach { (view, enabled) -> view.isEnabled = enabled }
                    status.text = previousStatus
                    if (!valid()) return@runOnUiThread
                    val page = result.getOrNull()
                    if (page == null || page.entries.isEmpty()) {
                        status.text = "Model list unavailable; no model selected"
                        return@runOnUiThread
                    }
                    val dialog = AlertDialog.Builder(this)
                        .setTitle(title + " · " + (page.offset + 1) + "–" + (page.offset + page.entries.size) + " of " + page.total)
                        .setItems(page.entries.map { it.label }.toTypedArray()) { _, index ->
                            if (valid()) onSelect(page.entries[index].model)
                        }.setNegativeButton(android.R.string.cancel, null)
                    if (page.hasPrevious) dialog.setNeutralButton("Previous") { _, _ ->
                        showPage(page.offset - ModelPickerPage.PAGE_SIZE)
                    }
                    if (page.hasNext) dialog.setPositiveButton("Next") { _, _ ->
                        showPage(page.offset + ModelPickerPage.PAGE_SIZE)
                    }
                    dialog.show()
                }
            }
        }
        showPage(0)
    }

    private fun chooseModel(s: Snapshot, mode: String = "text") {
        val ticket = s.ticket ?: return
        val draft = editor?.text?.toString() ?: return
        if (s.draft.pending == null && draft.isBlank()) { editor?.error = "Enter a message"; return }
        if (!resumed || busy || !alive() || current?.ticket != ticket || current !== s) return
        val preset = performancePreset()
        // Check the selected client text budget before model approval or pending-state creation.
        // Existing pending replies retain their retry/recovery path.
        if (s.draft.pending == null) {
            val profile = s.profiles.firstOrNull { it.id == ticket.key.character } ?: return
            val available = TurnBudget.availableForMessage(profile.identity.length, preset.contextUnits)
            if (draft.length > available) {
                editor?.error = "Message exceeds the current context allowance (" + available + " UTF-16 units). Shorten the draft or choose Longer context in Settings."
                status.text = "Draft preserved; no message submitted"
                return
            }
        }
        val approved = approvedModel?.takeIf { it.first == ticket && s.models.any { model -> model.sha256 == it.second } }
        if (approved != null) {
            generate(ticket, draft, approved.second, s.draft.pending != null, mode, preset)
            return
        }
        chooseTextModel(s, "Choose a trusted local model") { model ->
            AlertDialog.Builder(this).setTitle("Experimental inference")
                .setMessage("Approve this model for the current conversation selection? Only use models from trusted publishers. The model is verified by digest before use; no network fallback. Clear approval in Settings.\nPreset: " + preset.label + "\nSHA-256: " + model.sha256)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Approve and send") { _, _ ->
                    if (resumed && !busy && alive() && current === s && current?.ticket == ticket && editor?.text?.toString() == draft) {
                        approvedModel = ticket to model.sha256
                        generate(ticket, draft, model.sha256, s.draft.pending != null, mode, preset)
                    }
                }.show()
        }
    }
    private fun generate(ticket: NativeChatWorkspace.Ticket, draft: String, sha: String, retry: Boolean, mode: String, preset: InferencePreset) {
        if (!resumed || busy || !alive() || !BuildConfig.MANDY_EMBEDDED_PREVIEW || current?.ticket != ticket) return
        busy = true
        stopped.set(false)
        val states = mutableMapOf<View, Boolean>()
        suspendInputs(panel, states)
        val preview = chatLabel("Verifying and loading model…")
        messages.addView(preview)
        val previewScroll = historyScroll
        previewScroll.post { if (alive() && historyScroll === previewScroll && busy) previewScroll.scrollTo(0, (messages.height - previewScroll.height).coerceAtLeast(0)) }
        composerMode(generating = true)
        cancelGeneration.isEnabled = true
        status.text = "Experimental generation"
        disk.execute {
            var saved = false
            val text = StringBuilder()
            var lastUpdate = 0L
            // One queued preview at most; slow UI drops intermediate snapshots, not durable text.
            val previewQueued = AtomicBoolean(false)
            var metrics: GenerationMetrics? = null
            var preparation: ModelPreparation? = null
            val result = runCatching {
                workspace.edit(ticket, draft)
                saved = true
                if (!retry) workspace.prepare(ticket, mode, library.resolve(ticket.key).identity, preset.contextUnits)
                AndroidConversationStore(applicationContext, account).use { history ->
                    EmbeddedChatTurn.sendWithMemory(workspace, ticket, library, history, modelLibrary(), sha, memory, stopped::get, admit = { admitModel(it, preset.contextTokens) }, preset = preset, resident = resident, onMetrics = { metrics = it },
                        onPreparation = { preparation = it }) { origin, part ->
                        text.append(part)
                        val now = System.nanoTime()
                        if (now - lastUpdate >= 100_000_000L && previewQueued.compareAndSet(false, true)) {
                            lastUpdate = now
                            val visibleText = text.toString()
                            runOnUiThread {
                                try {
                                    if (alive() && busy && current?.ticket == origin && !stopped.get() && preview.parent === messages) {
                                        val nearBottom = !previewScroll.canScrollVertically(1)
                                        preview.text = visibleText
                                        if (nearBottom) previewScroll.post {
                                            if (alive() && busy && current?.ticket == origin && preview.parent === messages && !stopped.get())
                                                previewScroll.scrollTo(0, (messages.height - previewScroll.height).coerceAtLeast(0))
                                        }
                                    }
                                } finally { previewQueued.set(false) }
                            }
                        }
                    }
                }
            }
            val refreshed = if (saved) runCatching { snapshot() }.getOrNull() else null
            runOnUiThread {
                if (!alive()) return@runOnUiThread
                busy = false
                composerMode()
                if (refreshed != null) {
                    current = refreshed
                    render(refreshed)
                } else {
                    messages.removeView(preview)
                    if (!saved) states.forEach { (view, enabled) -> view.isEnabled = enabled }
                }
                status.text = when {
                    saved && refreshed == null -> "Reload workspace before continuing. Saved data was not reset."
                    result.isSuccess -> metrics?.let { m ->
                        "Reply saved · " + m.promptTokens + " input / " + m.outputTokens + " output tokens · " +
                            m.retainedMessages + " messages · " + m.fittingAttempts + " fitting attempts\n" +
                            "Fit " + m.fitNanos / 1_000_000 + " ms · context " + m.contextNanos / 1_000_000 +
                            " ms · prefill " + m.prefillNanos / 1_000_000 + " ms · generation " + m.generationNanos / 1_000_000 + " ms" +
                            (preparation?.let { prep ->
                                "\n" + (if (prep.reused) "Resident model" else "Model loaded") +
                                    " · verify " + prep.verifyNanos / 1_000_000 + " ms · admission " +
                                    prep.admissionNanos / 1_000_000 + " ms · load " + prep.loadNanos / 1_000_000 + " ms"
                            } ?: "")
                    } ?: "Reply saved"
                    stopped.get() -> "Stopped • check saved draft or pending message"
                    result.exceptionOrNull() is ModelAdmission.Denied -> admissionMessage(result.exceptionOrNull() as ModelAdmission.Denied) + " • pending message retained"
                    result.exceptionOrNull() is java.io.IOException &&
                        result.exceptionOrNull()?.message == "Unsupported local model chat template" ->
                        "Model chat template is unsupported • choose another trusted model and retry"
                    else -> "Generation failed • check saved draft or retry state"
                }
            }
        }
    }
    private fun chooseReadAloud(s: Snapshot, text: String) {
        if (!resumed || busy || !alive() || current !== s || playbackDialog != null) return
        AlertDialog.Builder(this).setTitle("Read reply aloud")
            .setMessage("Send this reply to your installed Android speech engine? Use a trusted engine. Mandy selects a voice advertised as offline and installed, but cannot verify another app's network behavior.")
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton("Russian") { _, _ -> readAloud(s, text, "ru") }
            .setPositiveButton("English") { _, _ -> readAloud(s, text, "en") }
            .show()
    }

    /** One foreground playback session. No microphone, auto-play, download or background service. */
    private fun readAloud(s: Snapshot, text: String, language: String) {
        if (!resumed || busy || !alive() || current !== s || playbackDialog != null) return
        val chunks = runCatching {
            SpokenText.chunks(text, minOf(4000, android.speech.tts.TextToSpeech.getMaxSpeechInputLength()))
        }.getOrElse { status.text = "Reply cannot be read aloud"; return }
        val main = android.os.Handler(android.os.Looper.getMainLooper())
        val dialog = AlertDialog.Builder(this).setTitle("Read aloud")
            .setMessage("Opening installed speech engine…")
            .setNegativeButton("Stop", null).create()
        playbackDialog = dialog
        var engine: android.speech.tts.TextToSpeech? = null
        val session = UUID.randomUUID().toString()
        var index = 0
        fun active() = playbackDialog === dialog && dialog.isShowing && resumed && alive() && current === s && !busy
        fun finish(message: String) {
            if (playbackDialog !== dialog) return
            status.text = message
            dialog.dismiss()
        }
        fun next() {
            if (!active()) return
            if (index >= chunks.size) { finish("Read aloud completed"); return }
            dialog.setMessage("Reading part " + (index + 1) + " of " + chunks.size)
            val result = runCatching {
                engine?.speak(chunks[index], android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, session + ":" + index)
            }.getOrNull()
            if (result != android.speech.tts.TextToSpeech.SUCCESS) finish("Speech playback failed")
        }
        dialog.setOnDismissListener {
            if (playbackDialog === dialog) playbackDialog = null
            val previous = engine
            engine = null
            runCatching { previous?.stop() }
            runCatching { previous?.shutdown() }
        }
        dialog.show()
        try {
            engine = android.speech.tts.TextToSpeech(this) { initialized ->
                // Post even for a synchronous initialization callback so engine is assigned.
                main.post {
                    if (!active()) return@post
                    val tts = engine ?: return@post
                    if (initialized != android.speech.tts.TextToSpeech.SUCCESS) {
                        finish("Installed speech engine unavailable"); return@post
                    }
                    val configured = runCatching {
                        val voice = tts.voices.orEmpty().filter {
                            it.locale.language == language && !it.isNetworkConnectionRequired &&
                                android.speech.tts.TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty()
                        }.sortedWith(compareByDescending<android.speech.tts.Voice> { it.quality }
                            .thenBy { it.latency }.thenBy { it.name }).firstOrNull()
                        voice != null && tts.setVoice(voice) == android.speech.tts.TextToSpeech.SUCCESS &&
                            tts.voice?.let { it.name == voice.name && !it.isNetworkConnectionRequired } == true
                    }.getOrDefault(false)
                    if (!configured) { finish("No installed offline-capable " + language.uppercase(java.util.Locale.ROOT) + " voice"); return@post }
                    val listener = object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) {
                            main.post {
                                if (active() && utteranceId == session + ":" + index) { index++; next() }
                            }
                        }
                        @Suppress("DEPRECATION")
                        override fun onError(utteranceId: String?) {
                            main.post {
                                if (active() && utteranceId == session + ":" + index) finish("Speech playback failed")
                            }
                        }
                    }
                    if (runCatching { tts.setOnUtteranceProgressListener(listener) }.getOrNull() != android.speech.tts.TextToSpeech.SUCCESS) {
                        finish("Speech playback unavailable"); return@post
                    }
                    next()
                }
            }
        } catch (_: Exception) { finish("Speech engine unavailable") }
    }

    private fun chooseSpeechLanguage(s: Snapshot) {
        if (!resumed || busy || current?.ticket != s.ticket || s.ticket == null) return
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            status.text = "On-device recognition unavailable. No remote recognizer will be used."
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Grant microphone permission, then tap Dictate again."
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 940)
            return
        }
        val languages = MandyLanguage.entries
        val names = languages.map { it.displayName }
        AlertDialog.Builder(this).setTitle("On-device dictation language").setItems(names.toTypedArray()) { _, index ->
            beginSpeech(s, languages[index])
        }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun chooseOfflineSpeech(s: Snapshot) {
        if (!resumed || busy || !BuildConfig.MANDY_WHISPER_PREVIEW || current?.ticket != s.ticket || s.ticket == null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Grant microphone permission, then tap Dictate again."
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 940)
            return
        }
        val labels = s.speechModels.map { it.sha256 + "\nBytes: " + it.bytes }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Trusted offline speech model").setItems(labels) { _, index ->
            val model = s.speechModels[index]
            AlertDialog.Builder(this).setTitle("Experimental offline dictation")
                .setMessage("Use only a trusted multilingual model. The microphone and recognition remain local. Speech detection is not validated. Stop keeps an editable transcript; Stop and send explicitly requests a chat response, so use it only if you accept transcription errors. The loaded chat model will be released before recording.\nSHA-256: " + model.sha256)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Choose language") { _, _ ->
                    val languages = MandyLanguage.entries
                    AlertDialog.Builder(this).setTitle("Offline dictation language")
                        .setItems(languages.map { it.displayName }.toTypedArray()) { _, selected ->
                            beginSpeech(s, languages[selected], model)
                        }.setNegativeButton(android.R.string.cancel, null).show()
                }.show()
        }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun beginSpeech(s: Snapshot, language: MandyLanguage, offline: NativeModelLibrary.Model? = null) {
        val ticket = s.ticket ?: return
        val field = editor ?: return
        if (!resumed || busy || !alive() || current?.ticket != ticket ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (offline != null && (!BuildConfig.MANDY_WHISPER_PREVIEW || offline !in s.speechModels)) return
        if (offline == null && (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this))) {
            status.text = "On-device recognition unavailable"
            return
        }
        val draft = field.text.toString()
        val start = field.selectionStart.takeIf { it >= 0 } ?: draft.length
        val end = field.selectionEnd.takeIf { it >= 0 } ?: start
        val canSend = BuildConfig.MANDY_EMBEDDED_PREVIEW && s.draft.pending == null && s.models.isNotEmpty()
        val token = runCatching { dictation.begin(ticket, draft, start, end, canSend) }.getOrElse {
            status.text = "Draft is invalid for dictation"; return
        }
        speechToken = token
        busy = true
        val states = mutableMapOf<View, Boolean>()
        suspendInputs(panel, states)
        speechStates = states
        composerMode(recording = true)
        stopSpeech.isEnabled = true
        sendSpeech.visibility = if (canSend) View.VISIBLE else View.GONE
        sendSpeech.isEnabled = canSend
        status.text = "Opening on-device microphone…"
        if (offline != null) {
            val session = OfflineSpeechSession()
            offlineSpeech = session
            val threads = performancePreset().threads
            disk.execute {
                val result = runCatching {
                    session.run(capture = { stop, cancelled ->
                        require(workspace.visible(ticket))
                        resident.close()
                        // Conservative existing admission estimate; actual ASR memory needs measurement.
                        admitModel(offline, 4096)
                        runOnUiThread {
                            if (alive() && resumed && speechToken == token && stopSpeech.isEnabled)
                                status.text = "Recording locally…"
                        }
                        AndroidPcmCapture.record(applicationContext, stopRequested = stop, cancelled = cancelled).samples
                    }, onTranscribing = {
                        runOnUiThread {
                            if (alive() && speechToken == token) {
                                stopSpeech.isEnabled = false
                                sendSpeech.isEnabled = false
                                status.text = "Transcribing locally…"
                            }
                        }
                    }) { pcm, cancelled ->
                        admitModel(offline, 4096)
                        EmbeddedWhisper.transcribe(offline.path, offline.sha256, pcm, language, threads, cancelled)
                    }
                }
                runOnUiThread {
                    if (!alive() || speechToken != token) return@runOnUiThread
                    if (!resumed) { cancelSpeech("Dictation cancelled"); return@runOnUiThread }
                    result.onSuccess { completeSpeech(token, ticket, listOf(it)) }
                        .onFailure { error ->
                            cancelSpeech(if (error is ModelAdmission.Denied) admissionMessage(error) + " · draft preserved"
                                else "Offline transcription failed or cancelled · draft preserved")
                        }
                }
            }
            return
        }
        try {
            val engine = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            recognizer = engine
            engine.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { if (speechToken == token && resumed && stopSpeech.isEnabled) status.text = "Listening on device…" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (speechToken == token) {
                        stopSpeech.isEnabled = false; sendSpeech.isEnabled = false
                        status.text = "Transcribing…"
                    }
                }
                override fun onError(error: Int) {
                    if (speechToken == token) cancelSpeech("Recognition failed or language unavailable · draft preserved")
                }
                override fun onResults(results: Bundle?) {
                    if (speechToken != token) return
                    if (!resumed || !alive()) { cancelSpeech("Dictation cancelled"); return }
                    completeSpeech(token, ticket, results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
            }
            engine.startListening(intent)
        } catch (_: Exception) {
            cancelSpeech("On-device recognition could not start · draft preserved")
        }
    }

    private fun completeSpeech(token: UUID, ticket: NativeChatWorkspace.Ticket, candidates: List<String>?) {
        if (speechToken != token || !alive() || !resumed) return
        val result = dictation.complete(token, current?.ticket, editor?.text?.toString(),
            candidates)
        releaseSpeech(token, cancel = false)
        if (result == null) { status.text = "No valid transcript · draft preserved"; return }
        val target = editor ?: return
        if (current?.ticket != ticket) return
        target.setText(result.edit.text)
        target.setSelection(result.edit.caret)
        // Persist before asking to send. Failed persistence leaves editable visible text.
        work(afterSuccess = { updated ->
            if (updated.ticket == ticket) {
                editor?.setSelection(result.edit.caret.coerceIn(0, editor?.length() ?: 0))
                status.text = "Dictation saved to draft"
                if (result.requestSend && resumed && updated.draft.pending == null && updated.models.isNotEmpty())
                    chooseModel(updated, mode = "dictation")
            }
        }) { workspace.edit(ticket, result.edit.text) }
    }

    private fun finishSpeech(send: Boolean) {
        val token = speechToken ?: return
        if (!resumed || !stopSpeech.isEnabled || (send && !sendSpeech.isEnabled)) return
        val offline = offlineSpeech
        if (offline != null && !offline.requestStop()) return
        if (!dictation.stop(token, send)) return
        stopSpeech.isEnabled = false; sendSpeech.isEnabled = false
        status.text = "Transcribing…"
        if (offline != null) return
        try { checkNotNull(recognizer).stopListening() }
        catch (_: Exception) { cancelSpeech("Recognition failed · draft preserved") }
    }

    private fun releaseSpeech(token: UUID, cancel: Boolean) {
        if (speechToken != token) return
        speechToken = null
        val offline = offlineSpeech
        offlineSpeech = null
        if (cancel) offline?.cancel()
        val engine = recognizer
        recognizer = null
        if (cancel) runCatching { engine?.cancel() }
        runCatching { engine?.destroy() }
        composerMode()
        speechStates.forEach { (view, enabled) -> view.isEnabled = enabled }
        speechStates = emptyMap()
        busy = false
    }

    private fun cancelSpeech(message: String) {
        val token = speechToken ?: return
        dictation.cancel(token)
        releaseSpeech(token, cancel = true)
        status.text = message
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        startQueuedTextRead()
    }

    override fun onPause() {
        resumed = false
        playbackDialog?.dismiss()
        cancelTextRead()
        stopped.set(true)
        // Serialize lifecycle cleanup after any in-flight explicit inference; never load here.
        disk.execute { resident.close() }
        cancelSpeech("Dictation cancelled · draft preserved")
        super.onPause()
    }

    override fun onDestroy() {
        playbackDialog?.dismiss()
        queuedTextPick = null
        textPick = null
        cancelTextRead()
        cancelSpeech("Dictation cancelled")
        super.onDestroy()
    }

    override fun onStop() {
        stopped.set(true)
        super.onStop()
    }
    private fun choose(s: Snapshot) {
        val choices = s.profiles.flatMap { profile -> profile.chats.map { chat -> profile to chat } }
        val labels = choices.map { (p, c) -> p.name + " · " + c.title + " [" + p.id.take(8) + "]" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Characters & conversations").setItems(labels) { _, index ->
            val (p, c) = choices[index]
            saveAnd { workspace.select(NativeChatWorkspace.Key(p.id, c.id)) }
        }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private data class DialogueReview(val details: String, val apply: (AndroidConversationStore) -> Unit)

    private fun chooseAssessmentModel(s: Snapshot, forMemory: Boolean = false) {
        val ticket = s.ticket ?: return
        chooseTextModel(s, "Select a trusted local assessment model") { model ->
            val sha = model.sha256
            AlertDialog.Builder(this).setTitle(if (forMemory) "Review memory" else "Review fictional relationship")
                .setMessage("Analyze the latest completed dialogue locally. Nothing is changed without your approval. Only continue if you trust the model publisher. Experimental inference may fail.\nSHA-256: " + sha)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Assess") { _, _ ->
                    if (resumed && alive() && !busy && current === s) assessDialogue(ticket, sha, forMemory)
                }.show()
        }
    }
    private fun assessDialogue(ticket: NativeChatWorkspace.Ticket, sha: String, forMemory: Boolean) {
        if (!resumed || busy || !alive() || !BuildConfig.MANDY_EMBEDDED_PREVIEW || current?.ticket != ticket) return
        busy = true
        stopped.set(false)
        val states = mutableMapOf<View, Boolean>()
        suspendInputs(panel, states)
        composerMode(generating = true)
        cancelGeneration.isEnabled = true
        val preset = performancePreset()
        status.text = "Assessing latest completed dialogue…"
        disk.execute {
            val result = runCatching {
                require(workspace.visible(ticket))
                AndroidConversationStore(applicationContext, account).use { history ->
                    val state = memory.get(ticket.key.character)
                    val generate: (List<NativeConversation.Message>, () -> Boolean) -> String = { messages, stop ->
                        resident.useModel(modelLibrary(), sha, stop, { admitModel(it, preset.contextTokens) }) { engine ->
                            engine.generate(messages, contextTokens = preset.contextTokens, outputTokens = minOf(384, preset.outputTokens),
                                cancelled = stop, threads = preset.threads, onText = {})
                        }
                    }
                    if (forMemory) {
                        MemoryAssessment.propose(history, ticket.key, state, stopped::get, generate)?.let { proposal ->
                            val details = "Proposed memory:\n" + proposal.note +
                                "\n\nOriginal user dialogue:\n" + proposal.evidence.user.text +
                                "\n\nCharacter reply:\n" + proposal.evidence.reply.text
                            DialogueReview(details) { store ->
                                MemoryAssessment.apply(store, memory, ticket.key, proposal, approved = true)
                            }
                        }
                    } else {
                        RelationshipAssessment.propose(history, ticket.key, state, stopped::get, generate)?.let { proposal ->
                            val details = "Proposed stage: " + proposal.stage.name.lowercase(java.util.Locale.ROOT).replace('_', ' ') +
                                "\n\nProposed note:\n" + proposal.note +
                                "\n\nEvidence — user:\n" + proposal.evidence.user.text +
                                "\n\nEvidence — character:\n" + proposal.evidence.reply.text
                            DialogueReview(details) { store ->
                                RelationshipAssessment.apply(store, memory, ticket.key, proposal, approved = true)
                            }
                        }
                    }
                }
            }
            runOnUiThread {
                if (!alive()) return@runOnUiThread
                busy = false
                composerMode()
                states.forEach { (view, enabled) -> view.isEnabled = enabled }
                if (stopped.get() || current?.ticket != ticket) {
                    status.text = "Assessment stopped"
                    return@runOnUiThread
                }
                result.onSuccess { proposal ->
                    if (proposal == null) status.text = "No new change proposed"
                    else {
                        status.text = "Review proposed change"
                        AlertDialog.Builder(this).setTitle(if (forMemory) "Memory proposal" else "Relationship proposal").setMessage(proposal.details)
                            .setNegativeButton("Reject", null)
                            .setPositiveButton("Approve") { _, _ ->
                                saveAnd {
                                    require(workspace.visible(ticket))
                                    AndroidConversationStore(applicationContext, account).use { history ->
                                        proposal.apply(history)
                                    }
                                }
                            }.show()
                    }
                }.onFailure { error -> status.text = if (error is ModelAdmission.Denied) admissionMessage(error) else "Assessment unavailable • memory and relationship unchanged" }
            }
        }
    }
    private fun editMemory(profile: NativeCharacterLibrary.Profile, state: NativeCharacterMemory.State) {
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        val stages = NativeCharacterMemory.Stage.entries
        val stage = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MandyWorkspaceActivity, android.R.layout.simple_spinner_dropdown_item,
                stages.map { it.name.lowercase(java.util.Locale.ROOT).replace('_', ' ') })
            setSelection(state.stage.ordinal)
        }
        val relationship = input("Shared relationship history", 800, state.relationship)
        val pinned = input("Always remember", 1200, state.pinned)
        val notes = input("Relevant memories, one per line", NativeCharacterMemory.MAX_EDITOR_UNITS, state.memories.joinToString("\n"))
        fields.addView(label("Fictional relationship"))
        fields.addView(stage)
        fields.addView(label("Relationship note"))
        fields.addView(relationship)
        fields.addView(label("Pinned memory"))
        fields.addView(pinned)
        fields.addView(label("Up to 128 memories, 400 characters each. Remove a line to remove a memory."))
        fields.addView(notes)
        val dialog = AlertDialog.Builder(this).setTitle("Memory · " + profile.name)
            .setView(ScrollView(this).apply { addView(fields) })
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton("Save", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val proposed = runCatching {
                state.copy(stage = stages[stage.selectedItemPosition], relationship = relationship.text.toString(),
                    pinned = pinned.text.toString(), memories = notes.text.toString().lines().map { it.trim() }.filter { it.isNotBlank() })
            }.getOrElse {
                notes.error = "Invalid, duplicate or oversized memory entries"
                return@setOnClickListener
            }
            dialog.dismiss()
            saveAnd { memory.edit(profile.id, proposed) }
        }
    }
    private fun editProfile(profile: NativeCharacterLibrary.Profile?) {
        val fields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        val name = input("Name", 120, profile?.name.orEmpty(), true)
        val identity = input("Personality, biography and speaking style", 8000, profile?.identity.orEmpty())
        fields.addView(name); fields.addView(identity)
        val dialog = AlertDialog.Builder(this).setTitle(if (profile == null) "Create character" else "Edit character")
            .setView(ScrollView(this).apply { addView(fields) })
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton("Save", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n = name.text.toString().trim(); val i = identity.text.toString()
            if (n.isBlank() || i.isBlank()) { name.error = "Name and identity are required"; return@setOnClickListener }
            dialog.dismiss()
            saveAnd {
                if (profile == null) {
                    val p = library.create(n, i, "First conversation")
                    workspace.select(NativeChatWorkspace.Key(p.id, p.chats.single().id))
                } else library.edit(profile.id, n, i)
            }
        }
    }
    private fun renameChat(s: Snapshot, profile: NativeCharacterLibrary.Profile) {
        val ticket = s.ticket ?: return
        if (busy || !alive() || !resumed || current !== s) return
        val chat = profile.chats.firstOrNull { it.id == ticket.key.chat } ?: return
        val title = input("Conversation title", 120, chat.title, single = true)
        val dialog = AlertDialog.Builder(this).setTitle("Rename conversation").setView(title)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton("Save", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = title.text.toString().trim()
            if (value.isBlank()) { title.error = "Title required"; return@setOnClickListener }
            dialog.dismiss()
            if (value != chat.title && !busy && alive() && resumed && current === s) saveAnd {
                require(workspace.visible(ticket))
                library.renameChat(profile.id, chat.id, value)
            }
        }
    }
    private fun createChat(profile: NativeCharacterLibrary.Profile) {
        val title = input("Conversation title", 120, single = true)
        val dialog = AlertDialog.Builder(this).setTitle("New conversation").setView(title)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton("Create", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = title.text.toString().trim()
            if (value.isBlank()) { title.error = "Title required"; return@setOnClickListener }
            dialog.dismiss()
            saveAnd {
                val p = library.newChat(profile.id, value)
                workspace.select(NativeChatWorkspace.Key(p.id, p.chats.last().id))
            }
        }
    }
    @Deprecated("Legacy back callback for this preview")
    override fun onBackPressed() {
        if (textRead != null) { cancelTextRead(); return }
        if (speechToken != null) { cancelSpeech("Dictation cancelled · draft preserved"); return }
        if (busy) { stopped.set(true); return }
        if (editor?.text?.toString() != current?.draft?.draft && editor != null) {
            AlertDialog.Builder(this).setMessage("Unsaved draft. Save it before leaving?")
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton("Discard changes") { _, _ -> finish() }
                .setPositiveButton("Save draft") { _, _ -> saveAnd {} }.show()
        } else finish()
    }
}
