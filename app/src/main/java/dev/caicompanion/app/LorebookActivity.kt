package dev.caicompanion.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.PersistableBundle
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.text.InputFilter
import android.view.View
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit file/paste import and conversion. Never contacts Character.AI or injects into chats. */
class LorebookActivity : Activity() {
    companion object {
        private const val OPEN = 841
        private const val SAVE = 842
        private const val COPY_LIMIT = 100_000
    }
    private var book: LorebookPolicy.Book? = null
    private var savedId: String? = null
    private var savedBook: LorebookPolicy.Book? = null
    private var pendingExport: String? = null
    private var picker = 0
    private var busy = false
    private val closed = AtomicBoolean(false)
    private lateinit var summary: TextView
    private lateinit var status: TextView
    private lateinit var entries: ListView
    private lateinit var searchInput: EditText
    private lateinit var reviewOnly: CheckBox
    private var query = ""
    private var visibleIndices: List<Int> = emptyList()
    private lateinit var exportJson: Button
    private lateinit var exportText: Button
    private lateinit var copyText: Button
    private data class Retained(val book: LorebookPolicy.Book?, val picker: Int, val export: String?,
                                val query: String, val reviewOnly: Boolean,
                                val savedId: String?, val savedBook: LorebookPolicy.Book?)

    override fun onCreate(savedInstanceState: Bundle?) {
        ShellAppearance.apply(this)
        super.onCreate(savedInstanceState)
        if (SettingsStore(this).protectScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        (lastNonConfigurationInstance as? Retained)?.let {
            book = it.book; picker = it.picker; pendingExport = it.export
            savedId = it.savedId; savedBook = it.savedBook
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(ShellAppearance.color(this@LorebookActivity, R.attr.shellBackground))
            setOnApplyWindowInsetsListener { view, insets ->
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    view.setPadding(bars.left + dp(16), bars.top + dp(12), bars.right + dp(16), bars.bottom + dp(12))
                }
                insets
            }
        }
        scroll.addView(column)
        fun button(label: Int, action: () -> Unit) = Button(this).apply {
            setText(label); isAllCaps = false; minHeight = dp(48)
            setOnClickListener { if (!busy && picker == 0) action() }
            column.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        column.addView(TextView(this).apply { setText(R.string.lorebook_title); textSize = 23f })
        column.addView(TextView(this).apply { setText(R.string.lorebook_help) })
        button(R.string.lorebook_library) { showLibrary() }
        button(R.string.lorebook_save_local) { saveLocal(false) }
        button(R.string.lorebook_save_copy) { saveLocal(true) }
        button(R.string.lorebook_creator_guide) { showCreatorGuide() }
        button(R.string.lorebook_open) { replace { open() } }
        button(R.string.lorebook_paste) { replace { paste() } }
        button(R.string.lorebook_new) { replace {
            savedId = null; savedBook = null
            book = LorebookPolicy.Book("", "", listOf(LorebookPolicy.Entry("", emptyList(), "")))
            resetSearch()
            refresh()
            editMetadata()
        } }
        button(R.string.lorebook_metadata) { editMetadata() }
        button(R.string.lorebook_add_entry) { if (book != null) editEntry(null) }
        summary = TextView(this).apply { textSize = 16f; setPadding(0, dp(12), 0, dp(8)) }
        column.addView(summary)
        status = TextView(this)
        column.addView(status)
        searchInput = privateField(R.string.lorebook_search, "", false).apply {
            filters = arrayOf(InputFilter.LengthFilter(120))
        }
        column.addView(searchInput)
        button(R.string.lorebook_search_apply) { query = searchInput.text.toString(); refresh() }
        reviewOnly = CheckBox(this).apply {
            setText(R.string.lorebook_review_only)
            isSaveEnabled = false
        }
        column.addView(reviewOnly)
        reviewOnly.isChecked = (lastNonConfigurationInstance as? Retained)?.reviewOnly == true
        query = (lastNonConfigurationInstance as? Retained)?.query.orEmpty()
        searchInput.setText(query)
        entries = ListView(this)
        column.addView(entries, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)))
        entries.setOnItemClickListener { _, _, index, _ ->
            if (!busy && picker == 0) visibleIndices.getOrNull(index)?.let(::entryActions)
        }
        reviewOnly.setOnCheckedChangeListener { _, _ -> if (!busy && picker == 0) refresh() }
        exportJson = button(R.string.lorebook_export_json) { prepareExport(false, false) }
        exportText = button(R.string.lorebook_export_text) { prepareExport(true, false) }
        copyText = button(R.string.lorebook_copy) { prepareExport(true, true) }
        button(R.string.close) { confirmClose() }
        setContentView(scroll)
        column.requestApplyInsets()
        refresh()
    }

    @Deprecated("Legacy Android back navigation")
    override fun onBackPressed() { confirmClose() }

    private fun confirmClose() {
        if ((book == null || book === savedBook) && !busy && picker == 0) finish()
        else AlertDialog.Builder(this).setMessage(R.string.lorebook_close_warning)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ -> finish() }.show()
    }

    private fun privateField(label: Int, value: String, multiline: Boolean): EditText =
        EditText(this).apply {
            setHint(label)
            setText(value)
            isSaveEnabled = false
            isSaveFromParentEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                if (multiline) android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE else 0
            if (multiline) { minLines = 3; maxLines = 8 } else setSingleLine(true)
        }

    private fun resetSearch() {
        query = ""
        if (::searchInput.isInitialized) searchInput.setText("")
        if (::reviewOnly.isInitialized) reviewOnly.isChecked = false
    }

    private fun editorDialog(title: Int, fields: List<EditText>, save: () -> Boolean) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            fields.forEach { addView(it) }
        }
        val dialog = AlertDialog.Builder(this).setTitle(title)
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm, null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (save()) dialog.dismiss()
            else fields.last().error = getString(R.string.lorebook_edit_limit)
        }
    }

    private fun editMetadata() {
        val current = book ?: return
        val name = privateField(R.string.lorebook_name, current.name, false)
        val desc = privateField(R.string.lorebook_description, current.description, true)
        editorDialog(R.string.lorebook_metadata, listOf(name, desc)) {
            val next = LorebookEditor.metadata(current, name.text.toString(), desc.text.toString())
            if (next == null || book !== current) false else {
                book = next
                refresh()
                true
            }
        }
    }

    private fun editEntry(index: Int?) {
        val current = book ?: return
        val entry = if (index == null) LorebookPolicy.Entry("", emptyList(), "")
            else current.entries.getOrNull(index) ?: return
        val title = privateField(R.string.lorebook_entry_title, entry.title, false)
        val originalKeys = entry.keys.joinToString("\n")
        val keys = privateField(R.string.lorebook_entry_keys, originalKeys, true)
        val content = privateField(R.string.lorebook_entry_content, entry.content, true)
        editorDialog(R.string.lorebook_edit_entry, listOf(title, keys, content)) {
            // Keep imported keys verbatim if this field was not edited.
            val words = if (keys.text.toString() == originalKeys) entry.keys
                else LorebookEditor.keywords(keys.text.toString())
            val next = words?.let { LorebookEditor.replace(current, index,
                LorebookPolicy.Entry(title.text.toString(), it, content.text.toString())) }
            if (next == null || book !== current) false else {
                book = next
                refresh()
                true
            }
        }
    }

    private fun entryActions(index: Int) {
        val current = book ?: return
        val entry = current.entries.getOrNull(index) ?: return
        AlertDialog.Builder(this).setTitle(entry.title.ifBlank { getString(R.string.lorebook_untitled) })
            .setItems(arrayOf(getString(R.string.lorebook_preview_entry), getString(R.string.lorebook_edit_entry),
                getString(R.string.lorebook_duplicate), getString(R.string.lorebook_delete))) { _, choice ->
                if (book !== current || busy || picker != 0) return@setItems
                when (choice) {
                    0 -> AlertDialog.Builder(this).setTitle(entry.title)
                        .setMessage(entry.keys.joinToString(", ") + "\n\n" + entry.content)
                        .setPositiveButton(R.string.close, null).show()
                    1 -> editEntry(index)
                    2 -> {
                        val next = LorebookEditor.replace(current, null, entry)
                        if (next != null) { book = next; refresh() }
                        else status.setText(R.string.lorebook_edit_limit)
                    }
                    3 -> AlertDialog.Builder(this).setMessage(R.string.lorebook_delete_confirm)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.confirm) { _, _ ->
                            if (book === current) {
                                val next = LorebookEditor.remove(current, index)
                                if (next != null) { book = next; refresh() }
                                else status.setText(R.string.lorebook_delete_last)
                            }
                        }.show()
                }
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value + .5f).toInt()
    private fun replace(action: () -> Unit) {
        if (book == null || book === savedBook) action() else AlertDialog.Builder(this).setMessage(R.string.lorebook_replace)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.confirm) { _, _ -> action() }.show()
    }

    private fun refresh() {
        val current = book
        summary.text = if (current == null) getString(R.string.lorebook_empty) else
            getString(R.string.lorebook_summary, current.name.ifBlank { getString(R.string.lorebook_untitled) },
                current.entries.size, current.entries.count(LorebookEditor::review), current.skippedDisabled) +
                (if (current.conversionLoss) "\n" + getString(R.string.lorebook_loss) else "") +
                "\n" + getString(if (current === savedBook) R.string.lorebook_local_saved else R.string.lorebook_local_unsaved)
        visibleIndices = current?.let { LorebookEditor.search(it, query, reviewOnly.isChecked) }.orEmpty()
        val labels = visibleIndices.map { i ->
            val entry = current!!.entries[i]
            val title = entry.title.ifBlank { getString(R.string.lorebook_untitled) }
            if (LorebookEditor.review(entry)) getString(R.string.lorebook_review_label, title) else title
        }
        entries.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        exportJson.isEnabled = current != null && !busy
        exportText.isEnabled = current != null && !busy
        copyText.isEnabled = current != null && !busy
    }

    /** Only explicit library actions touch disk; no autosave or background sync. */
    private fun <T> libraryWork(work: () -> T, done: (T) -> Unit) {
        if (busy || picker != 0 || closed.get()) return
        busy = true
        refresh()
        status.setText(R.string.lorebook_processing)
        Thread({
            val result = runCatching { work() }
            runOnUiThread {
                if (closed.get() || isFinishing || isDestroyed) return@runOnUiThread
                busy = false
                refresh()
                result.fold(done, { status.setText(R.string.lorebook_library_failed) })
            }
        }, "manual-lorebook-library").start()
    }

    private fun saveLocal(copy: Boolean) {
        val snapshot = book ?: return
        val id = if (copy) null else savedId
        val context = applicationContext
        libraryWork({ LorebookDraftStore.save(context, id, snapshot) }) { stored ->
            savedId = stored
            savedBook = snapshot
            status.setText(R.string.lorebook_local_saved)
            refresh()
        }
    }

    private fun showLibrary() {
        val context = applicationContext
        libraryWork({ LorebookDraftStore.list(context) }) { items ->
            if (items.isEmpty()) {
                status.setText(R.string.lorebook_library_empty)
                return@libraryWork
            }
            status.setText(R.string.lorebook_library)
            val labels = items.map { item ->
                val header = item.header
                if (header == null) getString(R.string.lorebook_library_damaged, item.id.take(8))
                else getString(R.string.lorebook_library_row,
                    header.name.ifBlank { getString(R.string.lorebook_untitled) }, header.entries, item.id.take(8))
            }
            AlertDialog.Builder(this).setTitle(R.string.lorebook_library)
                .setItems(labels.toTypedArray()) { _, index ->
                    val item = items[index]
                    AlertDialog.Builder(this).setTitle(labels[index])
                        .setItems(arrayOf(getString(R.string.lorebook_library_load),
                            getString(R.string.lorebook_library_delete))) { _, action ->
                            if (action == 0) replace {
                                libraryWork({ LorebookDraftStore.load(context, item.id) }) { loaded ->
                                    book = loaded
                                    savedId = item.id
                                    savedBook = loaded
                                    resetSearch()
                                    status.setText(R.string.lorebook_loaded)
                                    refresh()
                                }
                            } else AlertDialog.Builder(this).setMessage(R.string.lorebook_library_delete_confirm)
                                .setNegativeButton(R.string.cancel, null)
                                .setPositiveButton(R.string.confirm) { _, _ ->
                                    libraryWork({ LorebookDraftStore.delete(context, item.id) }) {
                                        if (savedId == item.id) { savedId = null; savedBook = null }
                                        status.setText(R.string.lorebook_library_deleted)
                                        refresh()
                                    }
                                }.show()
                        }.setNegativeButton(R.string.cancel, null).show()
                }.setNegativeButton(R.string.close, null).show()
        }
    }

    private fun showCreatorGuide() {
        AlertDialog.Builder(this).setTitle(R.string.lorebook_creator_guide)
            .setMessage(R.string.lorebook_creator_tips)
            .setPositiveButton(R.string.close, null).show()
    }

    private fun open() {
        picker = OPEN
        try {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*" // Some providers expose .lorebook/.md/.json as generic binary.
            }, OPEN)
        } catch (_: RuntimeException) { picker = 0; status.setText(R.string.lorebook_failed) }
    }

    private fun paste() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val item = runCatching { clipboard.primaryClip?.getItemAt(0)?.text }.getOrNull()
        if (item == null || item.length > LorebookPolicy.MAX_CHARS) { status.setText(R.string.lorebook_failed); return }
        val text = item.toString()
        importWork { text }
    }

    private fun parse(text: String): LorebookPolicy.Book? {
        val first = text.removePrefix("\uFEFF").trimStart().firstOrNull()
        return if (first == '{' || first == '[') LorebookJson.parse(text.removePrefix("\uFEFF").trimStart())
        else LorebookPolicy.parseText(text)
    }

    private fun importWork(read: () -> String?) {
        busy = true
        refresh()
        status.setText(R.string.lorebook_processing)
        Thread({
            val parsed = runCatching { read()?.let(::parse) }.getOrNull()
            runOnUiThread {
                if (closed.get() || isFinishing || isDestroyed) return@runOnUiThread
                busy = false
                if (parsed != null) book = parsed
                if (parsed != null) { savedId = null; savedBook = null; resetSearch() }
                status.setText(if (parsed != null) R.string.lorebook_loaded else R.string.lorebook_failed)
                refresh()
            }
        }, "manual-lorebook-import").start()
    }

    private fun prepareExport(asText: Boolean, copy: Boolean) {
        val current = book ?: return
        fun proceed() {
            if (busy || picker != 0) return
            busy = true
            refresh()
            status.setText(R.string.lorebook_processing)
            Thread({
                val output = runCatching {
                    if (asText) LorebookPolicy.toText(current) else LorebookJson.encode(current)
                }.getOrNull()
                runOnUiThread {
                    if (closed.get() || isFinishing || isDestroyed) return@runOnUiThread
                    busy = false
                    refresh()
                    if (output == null || (copy && output.length > COPY_LIMIT)) {
                        status.setText(R.string.lorebook_export_failed)
                        return@runOnUiThread
                    }
                    if (copy) {
                        try {
                            val clip = ClipData.newPlainText(getString(R.string.lorebook_title), output)
                            if (android.os.Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply {
                                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                            }
                            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                            status.setText(R.string.lorebook_copied)
                        } catch (_: RuntimeException) { status.setText(R.string.lorebook_export_failed) }
                    } else {
                        pendingExport = output
                        picker = SAVE
                        try {
                            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = if (asText) "text/plain" else "application/json"
                                putExtra(Intent.EXTRA_TITLE, if (asText) "lorebook.txt" else "lorebook.json")
                            }, SAVE)
                        } catch (_: RuntimeException) {
                            picker = 0; pendingExport = null
                            status.setText(R.string.lorebook_export_failed)
                        }
                    }
                }
            }, "manual-lorebook-convert").start()
        }
        if (current.entries.any(LorebookEditor::review) || current.conversionLoss || current.skippedDisabled > 0 || current.name.isBlank()) {
            AlertDialog.Builder(this).setMessage(R.string.lorebook_export_warning)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.confirm) { _, _ -> proceed() }.show()
        } else proceed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != picker || picker == 0) return
        picker = 0
        val snapshot = pendingExport
        pendingExport = null
        if (resultCode != RESULT_OK) return
        val uri = data?.data
        if (uri == null || uri.scheme != "content" || uri.authority.isNullOrBlank()) {
            status.setText(R.string.lorebook_failed); return
        }
        if (requestCode == OPEN) {
            importWork {
                val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                contentResolver.openInputStream(uri)?.buffered()?.use { input ->
                    if (PngLorebookCard.isPng(input)) {
                        PngLorebookCard.extract(input) { closed.get() }
                    } else InputStreamReader(input, decoder).use { reader ->
                        BoundedText.read(reader, LorebookPolicy.MAX_CHARS) { closed.get() }
                    }
                }
            }
        } else if (requestCode == SAVE && snapshot != null) {
            busy = true
            refresh()
            Thread({
                val ok = runCatching {
                    if (closed.get()) return@runCatching false
                    val output = contentResolver.openOutputStream(uri, "w") ?: error("missing output")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(snapshot) }
                    true
                }.getOrDefault(false)
                runOnUiThread {
                    if (closed.get() || isFinishing || isDestroyed) return@runOnUiThread
                    busy = false
                    status.setText(if (ok) R.string.lorebook_saved else R.string.lorebook_export_failed)
                    refresh()
                }
            }, "manual-lorebook-export").start()
        }
    }

    override fun onRetainNonConfigurationInstance(): Any = Retained(book, picker, pendingExport, query, reviewOnly.isChecked, savedId, savedBook)
    override fun onDestroy() {
        closed.set(true)
        super.onDestroy()
    }
}
