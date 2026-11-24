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

/** Offline workspace preview. No model endpoint is trusted or started by this screen. */
class MandyWorkspaceActivity : Activity() {
    companion object {
        // Serialize explicitly requested disk operations across activity recreation.
        private val disk = Executors.newSingleThreadExecutor()
    }
    private data class Snapshot(val profiles: List<NativeCharacterLibrary.Profile>,
                                val ticket: NativeChatWorkspace.Ticket?,
                                val draft: NativeChatWorkspace.State,
                                val rows: List<NativeConversation.Event>)
    private lateinit var library: NativeCharacterLibrary
    private lateinit var workspace: NativeChatWorkspace
    private lateinit var account: String
    private lateinit var panel: LinearLayout
    private lateinit var status: TextView
    private var current: Snapshot? = null
    private var editor: EditText? = null
    private var busy = false
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
            workspace = NativeChatWorkspace(base.resolve("mandy-workspace-v1"), account, device)
        }
    }
    private fun snapshot(): Snapshot {
        val profiles = library.list()
        val ticket = workspace.selected()
        if (ticket == null) return Snapshot(profiles, null, NativeChatWorkspace.State(), emptyList())
        library.resolve(ticket.key)
        val rows = AndroidConversationStore(applicationContext, account).use {
            it.page(ticket.key.character, ticket.key.chat, 50).asReversed()
        }
        return Snapshot(profiles, ticket, workspace.state(ticket.key), rows)
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
                    status.text = "Local workspace • model not connected"
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
        panel.addView(button("New conversation") { createChat(profile) })
        if (s.rows.isEmpty()) panel.addView(label("No messages yet."))
        else s.rows.forEach {
            val name = if (it.role == "user") "You" else profile.name
            panel.addView(label(name + "\n" + it.text).apply { setTextIsSelectable(true) })
        }
        if (s.draft.pending != null) panel.addView(label("A pending message is saved. Retry is unavailable until the model is connected."))
        val field = input("Message " + profile.name, 16000, s.draft.draft)
        editor = field
        panel.addView(field)
        panel.addView(button("Save draft") { saveAnd {} })
        panel.addView(Button(this).apply { text = "Send • model unavailable"; isAllCaps = false; isEnabled = false })
    }
    private fun choose(s: Snapshot) {
        val choices = s.profiles.flatMap { profile -> profile.chats.map { chat -> profile to chat } }
        val labels = choices.map { (p, c) -> p.name + " · " + c.title + " [" + p.id.take(8) + "]" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Characters & conversations").setItems(labels) { _, index ->
            val (p, c) = choices[index]
            saveAnd { workspace.select(NativeChatWorkspace.Key(p.id, c.id)) }
        }.setNegativeButton(android.R.string.cancel, null).show()
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
        if (busy) return
        if (editor?.text?.toString() != current?.draft?.draft && editor != null) {
            AlertDialog.Builder(this).setMessage("Unsaved draft. Save it before leaving?")
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton("Discard changes") { _, _ -> finish() }
                .setPositiveButton("Save draft") { _, _ -> saveAnd {} }.show()
        } else finish()
    }
}
