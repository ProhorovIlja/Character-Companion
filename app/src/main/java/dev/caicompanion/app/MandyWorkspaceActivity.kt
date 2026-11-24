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
            it.outputTokens + " output · " + it.threads + " CPU threads" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Local inference preset (experimental)")
            .setSingleChoiceItems(labels, presets.indexOf(performancePreset())) { dialog, index ->
                dialog.dismiss()
                saveAnd { check(performancePrefs().edit().putString("preset", presets[index].key).commit()) }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun modelLibrary() = NativeModelLibrary(applicationContext.noBackupFilesDir.toPath().resolve("mandy-models-v1"))
    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()
    private fun alive() = !isFinishing && !isDestroyed
    override fun onCreate(savedInstanceState: Bundle?) {
        ShellAppearance.apply(this)
        super.onCreate(savedInstanceState)
        if (SettingsStore(this).protectScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ShellAppearance.color(this@MandyWorkspaceActivity, R.attr.shellBackground))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }
        status = label("Opening local workspace…", 12f).apply {
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener { AlertDialog.Builder(this@MandyWorkspaceActivity).setTitle("Workspace status").setMessage(text).setPositiveButton("Close", null).show() }
        }
        cancelGeneration = Button(this).apply {
            text = "Stop generation"; isAllCaps = false; visibility = View.GONE
            setOnClickListener { stopped.set(true); isEnabled = false; status.text = "Stopping…" }
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
            val color = ShellAppearance.color(this@MandyWorkspaceActivity, if (primary && available) R.attr.shellOnAccent else R.attr.shellText)
            val mask = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(android.graphics.Color.WHITE) }
            val fill = if (primary && available) GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ShellAppearance.color(this@MandyWorkspaceActivity, R.attr.shellAccent))
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
        setColor(ShellAppearance.color(this@MandyWorkspaceActivity, fill))
        cornerRadius = dp(radius).toFloat()
        if (outlined) setStroke(dp(1), ShellAppearance.color(this@MandyWorkspaceActivity, R.attr.shellOutline))
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
        header.addView(label(profile?.name ?: "No character", 18f).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        // Visible capability placeholders, never request permission or simulate a call.
        header.addView(chatAction("call", "Voice call not available in this build", false) {})
        header.addView(chatAction("settings", "Settings") { showWorkspaceSettings(s) })
        panel.addView(header)
        if (chat != null) panel.addView(label(chat.title, 14f))
        historyScroll = ScrollView(this).apply { isFillViewport = true }
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        historyScroll.addView(messages)
        panel.addView(historyScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (ticket == null || profile == null) {
            messages.addView(label("Create or select a character to begin."))
            messages.addView(button("Create character") { editProfile(null) })
            return
        }
        if (s.rows.isNotEmpty()) messages.addView(button("Earlier messages") { browseHistory(s, profile.name) })
        if (s.rows.isEmpty()) messages.addView(label("No messages yet."))
        else s.rows.forEach {
            val author = if (it.role == "user") "You" else profile.name
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(12)) }
            if (it.role == "user") row.background = roundedSurface(R.attr.shellAction, 20)
            row.addView(label(author, 13f).apply { setPadding(0, 0, 0, dp(4)); typeface = android.graphics.Typeface.DEFAULT_BOLD })
            row.addView(label(it.text).apply { setPadding(0, 0, 0, 0); setTextIsSelectable(true); setLineSpacing(dp(3).toFloat(), 1f) })
            messages.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                if (it.role == "user") marginStart = dp(36) else marginEnd = dp(24)
                topMargin = dp(4); bottomMargin = dp(4)
            })
        }
        if (s.draft.pending != null) {
            messages.addView(label("Pending message • " + s.draft.pending.text))
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
            setTextColor(ShellAppearance.color(this@MandyWorkspaceActivity, R.attr.shellText))
            setHintTextColor(ShellAppearance.color(this@MandyWorkspaceActivity, R.attr.shellMuted))
        }
        editor = field
        composer.addView(field)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        actions.addView(chatAction("attach", "Attachments not available in this build", false) {})
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
        actions.addView(chatAction(if (s.draft.pending == null) "send" else "retry",
            if (!canSend) "Send unavailable: local model required" else if (s.draft.pending == null) "Send message" else "Retry pending message",
            canSend) { chooseModel(s) })
        normalActions = actions
        composer.addView(actions)
        composer.addView(cancelGeneration, LinearLayout.LayoutParams(-1, -2))
        composer.addView(speechControls, LinearLayout.LayoutParams(-1, -2))
        composerMode()
        panel.addView(composer)
        if (!BuildConfig.MANDY_EMBEDDED_PREVIEW || s.models.isEmpty())
            panel.addView(label("Local model required for Send", 12f))
        // Scroll only when displaying a fresh snapshot, not on every token.
        val scroll = historyScroll
        scroll.post { if (alive() && historyScroll === scroll) scroll.scrollTo(0, (messages.height - scroll.height).coerceAtLeast(0)) }
    }

    private fun showWorkspaceSettings(s: Snapshot) {
        val options = mutableListOf<Pair<String, () -> Unit>>()
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

    private fun chooseModel(s: Snapshot, mode: String = "text") {
        val ticket = s.ticket ?: return
        val draft = editor?.text?.toString() ?: return
        if (s.draft.pending == null && draft.isBlank()) { editor?.error = "Enter a message"; return }
        if (!resumed || busy || !alive() || current?.ticket != ticket || current !== s) return
        val preset = performancePreset()
        val approved = approvedModel?.takeIf { it.first == ticket && s.models.any { model -> model.sha256 == it.second } }
        if (approved != null) {
            generate(ticket, draft, approved.second, s.draft.pending != null, mode, preset)
            return
        }
        val labels = s.models.map { it.sha256 + "\nBytes: " + it.bytes }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Choose a trusted local model").setItems(labels) { _, index ->
            val model = s.models[index]
            AlertDialog.Builder(this).setTitle("Experimental inference")
                .setMessage("Approve this model for the current conversation selection? Only use models from trusted publishers. The model is verified by digest before use; no network fallback. Clear approval in Settings.\nPreset: " + preset.label + "\nSHA-256: " + model.sha256)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Approve and send") { _, _ ->
                    if (resumed && !busy && alive() && current === s && current?.ticket == ticket && editor?.text?.toString() == draft) {
                        approvedModel = ticket to model.sha256
                        generate(ticket, draft, model.sha256, s.draft.pending != null, mode, preset)
                    }
                }.show()
        }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun generate(ticket: NativeChatWorkspace.Ticket, draft: String, sha: String, retry: Boolean, mode: String, preset: InferencePreset) {
        if (!resumed || busy || !alive() || !BuildConfig.MANDY_EMBEDDED_PREVIEW || current?.ticket != ticket) return
        busy = true
        stopped.set(false)
        val states = mutableMapOf<View, Boolean>()
        suspendInputs(panel, states)
        val preview = label("Verifying and loading model…")
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
            var metrics: GenerationMetrics? = null
            var preparation: ModelPreparation? = null
            val result = runCatching {
                workspace.edit(ticket, draft)
                saved = true
                if (!retry) workspace.prepare(ticket, mode)
                AndroidConversationStore(applicationContext, account).use { history ->
                    EmbeddedChatTurn.sendWithMemory(workspace, ticket, library, history, modelLibrary(), sha, memory, stopped::get, admit = { admitModel(it, preset.contextTokens) }, preset = preset, resident = resident, onMetrics = { metrics = it },
                        onPreparation = { preparation = it }) { origin, part ->
                        text.append(part)
                        val now = System.nanoTime()
                        if (now - lastUpdate >= 100_000_000L) {
                            lastUpdate = now
                            val visibleText = text.toString()
                            runOnUiThread {
                                if (alive() && busy && current?.ticket == origin && !stopped.get() && preview.parent === messages) {
                                    val nearBottom = !previewScroll.canScrollVertically(1)
                                    preview.text = visibleText
                                    if (nearBottom) previewScroll.post {
                                        if (alive() && busy && current?.ticket == origin && preview.parent === messages && !stopped.get())
                                            previewScroll.scrollTo(0, (messages.height - previewScroll.height).coerceAtLeast(0))
                                    }
                                }
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
                    else -> "Generation failed • check saved draft or retry state"
                }
            }
        }
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
    }

    override fun onPause() {
        resumed = false
        stopped.set(true)
        // Serialize lifecycle cleanup after any in-flight explicit inference; never load here.
        disk.execute { resident.close() }
        cancelSpeech("Dictation cancelled · draft preserved")
        super.onPause()
    }

    override fun onDestroy() {
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
        val labels = s.models.map { it.sha256 + "\nBytes: " + it.bytes }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Select a trusted local assessment model").setItems(labels) { _, index ->
            val sha = s.models[index].sha256
            AlertDialog.Builder(this).setTitle(if (forMemory) "Review memory" else "Review fictional relationship")
                .setMessage("Analyze the latest completed dialogue locally. Nothing is changed without your approval. Only continue if you trust the model publisher. Experimental inference may fail.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Assess") { _, _ -> assessDialogue(ticket, sha, forMemory) }.show()
        }.setNegativeButton(android.R.string.cancel, null).show()
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
