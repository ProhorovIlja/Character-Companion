package dev.caicompanion.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicBoolean

/** Private creator preparation. No web injection, account access or automatic publishing. */
class CharacterDraftActivity : Activity() {
    private var draft = CharacterDraftPolicy.Draft()
    private var picking = false
    private var busy = false
    private val closed = AtomicBoolean(false)
    private lateinit var status: TextView
    private lateinit var summary: TextView
    private val fieldButtons = mutableMapOf<CharacterDraftPolicy.Field, Button>()
    private data class Retained(val draft: CharacterDraftPolicy.Draft, val picking: Boolean)

    override fun onCreate(savedInstanceState: Bundle?) {
        ShellAppearance.apply(this)
        super.onCreate(savedInstanceState)
        if (SettingsStore(this).protectScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        (lastNonConfigurationInstance as? Retained)?.let { draft = it.draft; picking = it.picking }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(ShellAppearance.color(this@CharacterDraftActivity, R.attr.shellBackground))
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    view.setPadding(bars.left + dp(16), bars.top + dp(12), bars.right + dp(16), bars.bottom + dp(12))
                }
                insets
            }
        }
        fun button(label: Int, action: () -> Unit) = Button(this).apply {
            setText(label); isAllCaps = false; minHeight = dp(48)
            setOnClickListener { if (!busy && !picking) action() }
            column.addView(this)
        }
        column.addView(TextView(this).apply { setText(R.string.character_draft_title); textSize = 23f })
        column.addView(TextView(this).apply { setText(R.string.character_draft_help) })
        button(R.string.character_draft_import) { replace { openCard() } }
        button(R.string.character_draft_paste) { replace { pasteCard() } }
        button(R.string.character_draft_new) { replace { draft = CharacterDraftPolicy.Draft(); refresh() } }
        summary = TextView(this)
        column.addView(summary)
        for (field in CharacterDraftPolicy.Field.entries) {
            fieldButtons[field] = button(label(field)) { fieldMenu(field) }
        }
        button(R.string.character_draft_alternatives) { alternatives() }
        button(R.string.character_draft_tags) { preview(R.string.character_draft_tags, draft.tags.joinToString("\n")) }
        button(R.string.character_draft_notes) { preview(R.string.character_draft_notes, draft.sourceNotes) }
        status = TextView(this)
        column.addView(status)
        button(R.string.close) { confirmClose() }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(column) })
        column.requestApplyInsets()
        refresh()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value + .5f).toInt()
    private fun label(field: CharacterDraftPolicy.Field): Int = when (field) {
        CharacterDraftPolicy.Field.NAME -> R.string.character_draft_name
        CharacterDraftPolicy.Field.TAGLINE -> R.string.character_draft_tagline
        CharacterDraftPolicy.Field.DESCRIPTION -> R.string.character_draft_description
        CharacterDraftPolicy.Field.GREETING -> R.string.character_draft_greeting
        CharacterDraftPolicy.Field.DEFINITION -> R.string.character_draft_definition
    }

    private fun refresh() {
        val count = CharacterDraftPolicy.Field.entries.count { CharacterDraftPolicy.needsReview(it, draft.value(it)) }
        summary.text = getString(R.string.character_draft_summary, count, draft.alternatives.size, draft.tags.size) +
            if (draft.conversionLoss) "\n" + getString(R.string.character_draft_loss) else ""
        for ((field, button) in fieldButtons) {
            val text = draft.value(field)
            button.text = getString(R.string.character_draft_count, getString(label(field)), text.length, field.targetMax) +
                if (CharacterDraftPolicy.needsReview(field, text)) " · " + getString(R.string.character_draft_review) else ""
            button.isEnabled = !busy && !picking
        }
    }

    private fun fieldMenu(field: CharacterDraftPolicy.Field) {
        AlertDialog.Builder(this).setTitle(label(field))
            .setItems(arrayOf(getString(R.string.character_draft_edit), getString(R.string.character_draft_copy))) { _, i ->
                if (i == 0) edit(field) else copy(draft.value(field))
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun edit(field: CharacterDraftPolicy.Field) {
        val before = draft
        val editor = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            setText(before.value(field))
            isSaveEnabled = false
            isSaveFromParentEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            minLines = 2; maxLines = 10
        }
        val dialog = AlertDialog.Builder(this).setTitle(label(field))
            .setView(ScrollView(this).apply { addView(editor) })
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm, null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val next = CharacterDraftPolicy.edit(before, field, editor.text.toString())
            if (next == null || draft !== before) editor.error = getString(R.string.character_draft_invalid)
            else { draft = next; refresh(); dialog.dismiss() }
        }
    }

    private fun preview(title: Int, text: String) {
        if (text.isEmpty()) { status.setText(R.string.character_draft_empty); return }
        AlertDialog.Builder(this).setTitle(title).setMessage(text)
            .setPositiveButton(R.string.character_draft_copy) { _, _ -> copy(text) }
            .setNegativeButton(R.string.close, null).show()
    }

    private fun alternatives() {
        val before = draft
        if (before.alternatives.isEmpty()) { status.setText(R.string.character_draft_empty); return }
        val labels = before.alternatives.mapIndexed { index, text ->
            getString(R.string.character_draft_alternative_row, index + 1, text.length)
        }
        AlertDialog.Builder(this).setTitle(R.string.character_draft_alternatives)
            .setItems(labels.toTypedArray()) { _, index ->
                val value = before.alternatives[index]
                AlertDialog.Builder(this).setMessage(value)
                    .setPositiveButton(R.string.character_draft_use_greeting) { _, _ ->
                        if (draft === before) {
                            val next = CharacterDraftPolicy.useAlternative(before, index)
                            if (next != null) { draft = next; refresh() }
                            else status.setText(R.string.character_draft_invalid)
                        }
                    }.setNeutralButton(R.string.character_draft_copy) { _, _ -> copy(value) }
                    .setNegativeButton(R.string.cancel, null).show()
            }.setNegativeButton(R.string.close, null).show()
    }

    private fun copy(value: String) {
        if (value.isEmpty()) { status.setText(R.string.character_draft_empty); return }
        try {
            val clip = ClipData.newPlainText(getString(R.string.character_draft_title), value)
            if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
            status.setText(R.string.character_draft_copied)
        } catch (_: RuntimeException) { status.setText(R.string.character_draft_invalid) }
    }

    private fun replace(action: () -> Unit) {
        if (draft == CharacterDraftPolicy.Draft()) action()
        else AlertDialog.Builder(this).setMessage(R.string.character_draft_replace)
            .setPositiveButton(R.string.confirm) { _, _ -> action() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun openCard() {
        picking = true
        refresh()
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }, 851)
        } catch (_: RuntimeException) {
            picking = false; refresh(); status.setText(R.string.character_draft_invalid)
        }
    }

    private fun pasteCard() {
        val text = runCatching {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.text
        }.getOrNull()
        if (text == null || text.length > LorebookPolicy.MAX_CHARS) {
            status.setText(R.string.character_draft_invalid); return
        }
        val snapshot = text.toString()
        importWork { snapshot }
    }

    private fun importWork(read: () -> String?) {
        busy = true; refresh(); status.setText(R.string.lorebook_processing)
        Thread({
            val result = runCatching { read()?.let(CharacterCardJson::parse) }.getOrNull()
            runOnUiThread {
                if (closed.get() || isFinishing || isDestroyed) return@runOnUiThread
                busy = false
                if (result != null) draft = result
                status.setText(if (result == null) R.string.character_draft_invalid else R.string.character_draft_loaded)
                refresh()
            }
        }, "manual-character-card-import").start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 851 || !picking) return
        picking = false; refresh()
        if (resultCode != RESULT_OK) return
        val uri = data?.data
        if (uri == null || uri.scheme != "content" || uri.authority.isNullOrBlank()) {
            status.setText(R.string.character_draft_invalid); return
        }
        importWork {
            contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                if (PngLorebookCard.isPng(input)) PngLorebookCard.extract(input) { closed.get() }
                else InputStreamReader(input, Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)).use { reader ->
                    BoundedText.read(reader, LorebookPolicy.MAX_CHARS) { closed.get() }
                }
            }
        }
    }

    private fun confirmClose() {
        if (draft == CharacterDraftPolicy.Draft() && !busy && !picking) finish()
        else AlertDialog.Builder(this).setMessage(R.string.character_draft_close)
            .setPositiveButton(R.string.confirm) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel, null).show()
    }

    @Deprecated("Legacy Android back navigation")
    override fun onBackPressed() { confirmClose() }
    override fun onRetainNonConfigurationInstance(): Any = Retained(draft, picking)
    override fun onDestroy() { closed.set(true); super.onDestroy() }
}
