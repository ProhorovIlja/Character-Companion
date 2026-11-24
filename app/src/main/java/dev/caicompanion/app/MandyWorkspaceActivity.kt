package dev.caicompanion.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
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
                                val memory: NativeCharacterMemory.State)
    private lateinit var library: NativeCharacterLibrary
    private lateinit var workspace: NativeChatWorkspace
    private lateinit var memory: NativeCharacterMemory
    private lateinit var account: String
    private lateinit var panel: LinearLayout
    private lateinit var status: TextView
    private var current: Snapshot? = null
    private var editor: EditText? = null
    private var busy = false
    private val stopped = AtomicBoolean(false)
    private lateinit var cancelGeneration: Button
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
        val title = label("Mandy AI", 24f)
        ViewCompat.setAccessibilityHeading(title, true)
        root.addView(title)
        status = label("Offline workspace preview", 14f)
        root.addView(status)
        cancelGeneration = Button(this).apply {
            text = "Stop generation"; isAllCaps = false; visibility = View.GONE
            setOnClickListener { stopped.set(true); isEnabled = false; status.text = "Stopping…" }
        }
        root.addView(cancelGeneration)
        val scroll = ScrollView(this).apply { isFillViewport = true }
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        scroll.addView(panel)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
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
        if (ticket == null) return Snapshot(profiles, null, NativeChatWorkspace.State(), emptyList(), models, NativeCharacterMemory.State())
        library.resolve(ticket.key)
        val rows = AndroidConversationStore(applicationContext, account).use {
            it.page(ticket.key.character, ticket.key.chat, 50).asReversed()
        }
        return Snapshot(profiles, ticket, workspace.state(ticket.key), rows, models, memory.get(ticket.key.character))
    }
    private fun suspendInputs(view: View, states: MutableMap<View, Boolean>) {
        states[view] = view.isEnabled
        view.isEnabled = false
        if (view is ViewGroup) for (i in 0 until view.childCount) suspendInputs(view.getChildAt(i), states)
    }
    private fun work(action: () -> Unit) {
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
                    current = it
                    render(it)
                    status.text = if (BuildConfig.MANDY_EMBEDDED_PREVIEW) "Experimental local inference • select a trusted model per send" else "Local workspace • embedded build unavailable"
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
    private fun saveAnd(action: () -> Unit) {
        val ticket = current?.ticket
        val text = editor?.text?.toString()
        work {
            if (ticket != null && text != null) workspace.edit(ticket, text)
            action()
        }
    }
    private fun render(s: Snapshot) {
        panel.removeAllViews()
        editor = null
        panel.addView(button("AI models") { if (editor != null && editor?.text?.toString() != current?.draft?.draft) {
                status.text = "Save the current draft before opening models."
            } else startActivity(android.content.Intent(this, MandyModelActivity::class.java)) })
        panel.addView(button("Create character") { editProfile(null) })
        if (s.profiles.isNotEmpty()) panel.addView(button("Characters & conversations") { choose(s) })
        val ticket = s.ticket ?: return
        val profile = s.profiles.first { it.id == ticket.key.character }
        val chat = profile.chats.first { it.id == ticket.key.chat }
        panel.addView(label(profile.name, 22f))
        panel.addView(label(chat.title, 14f))
        panel.addView(button("Edit character") { editProfile(profile) })
        panel.addView(button("Memory & relationship") { editMemory(profile, s.memory) })
        panel.addView(button("New conversation") { createChat(profile) })
        if (s.rows.isEmpty()) panel.addView(label("No messages yet."))
        else s.rows.forEach {
            val name = if (it.role == "user") "You" else profile.name
            panel.addView(label(name + "\n" + it.text).apply { setTextIsSelectable(true) })
        }
        if (s.draft.pending != null) {
            panel.addView(label("Pending message • " + s.draft.pending.text))
            panel.addView(button("Discard pending retry") {
                AlertDialog.Builder(this).setMessage("Discard retry state? Already committed messages remain in history.")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("Discard") { _, _ -> saveAnd { workspace.discardPending(ticket, s.draft.pending.id) } }.show()
            })
        }
        val field = input("Message " + profile.name, 16000, s.draft.draft)
        editor = field
        panel.addView(field)
        panel.addView(button("Save draft") { saveAnd {} })
        if (BuildConfig.MANDY_EMBEDDED_PREVIEW) {
            panel.addView(button("Refresh model inventory") { saveAnd {} })
            if (s.models.isNotEmpty()) panel.addView(button(if (s.draft.pending == null) "Experimental send…" else "Retry pending…") { chooseModel(s) })
            else panel.addView(label("Import a trusted model in AI models, then refresh."))
        } else panel.addView(Button(this).apply { text = "Send • embedded build unavailable"; isAllCaps = false; isEnabled = false })
    }
    private fun chooseModel(s: Snapshot) {
        val ticket = s.ticket ?: return
        val draft = editor?.text?.toString() ?: return
        if (s.draft.pending == null && draft.isBlank()) { editor?.error = "Enter a message"; return }
        val labels = s.models.map { it.sha256 + "\nBytes: " + it.bytes }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Choose a trusted local model").setItems(labels) { _, index ->
            val model = s.models[index]
            AlertDialog.Builder(this).setTitle("Experimental inference")
                .setMessage("Only continue if you trust the publisher of this file. Real model compatibility is not yet verified. This may be slow or fail. No network fallback.\nSHA-256: " + model.sha256)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Generate locally") { _, _ -> generate(ticket, draft, model.sha256, s.draft.pending != null) }.show()
        }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun generate(ticket: NativeChatWorkspace.Ticket, draft: String, sha: String, retry: Boolean) {
        if (busy || !alive() || !BuildConfig.MANDY_EMBEDDED_PREVIEW || current?.ticket != ticket) return
        busy = true
        stopped.set(false)
        val states = mutableMapOf<View, Boolean>()
        suspendInputs(panel, states)
        val preview = label("Verifying and loading model…")
        panel.addView(preview)
        cancelGeneration.visibility = View.VISIBLE
        cancelGeneration.isEnabled = true
        status.text = "Experimental generation"
        disk.execute {
            var saved = false
            val text = StringBuilder()
            var lastUpdate = 0L
            val result = runCatching {
                workspace.edit(ticket, draft)
                saved = true
                if (!retry) workspace.prepare(ticket)
                AndroidConversationStore(applicationContext, account).use { history ->
                    EmbeddedChatTurn.sendWithMemory(workspace, ticket, library, history, modelLibrary(), sha, memory, stopped::get) { origin, part ->
                        text.append(part)
                        val now = System.nanoTime()
                        if (now - lastUpdate >= 100_000_000L) {
                            lastUpdate = now
                            val visibleText = text.toString()
                            runOnUiThread {
                                if (alive() && busy && current?.ticket == origin && !stopped.get()) preview.text = visibleText
                            }
                        }
                    }
                }
            }
            val refreshed = if (saved) runCatching { snapshot() }.getOrNull() else null
            runOnUiThread {
                if (!alive()) return@runOnUiThread
                busy = false
                cancelGeneration.visibility = View.GONE
                if (refreshed != null) {
                    current = refreshed
                    render(refreshed)
                } else {
                    panel.removeView(preview)
                    if (!saved) states.forEach { (view, enabled) -> view.isEnabled = enabled }
                }
                status.text = when {
                    saved && refreshed == null -> "Reload workspace before continuing. Saved data was not reset."
                    result.isSuccess -> "Reply saved"
                    stopped.get() -> "Stopped • check saved draft or pending message"
                    else -> "Generation failed • check saved draft or retry state"
                }
            }
        }
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
        val notes = input("Relevant memories, one per line", 13200, state.memories.joinToString("\n"))
        fields.addView(label("Fictional relationship"))
        fields.addView(stage)
        fields.addView(label("Relationship note"))
        fields.addView(relationship)
        fields.addView(label("Pinned memory"))
        fields.addView(pinned)
        fields.addView(label("Up to 32 memories, 400 characters each. Remove a line to remove a memory."))
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
        if (busy) { stopped.set(true); return }
        if (editor?.text?.toString() != current?.draft?.draft && editor != null) {
            AlertDialog.Builder(this).setMessage("Unsaved draft. Save it before leaving?")
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton("Discard changes") { _, _ -> finish() }
                .setPositiveButton("Save draft") { _, _ -> saveAnd {} }.show()
        } else finish()
    }
}
