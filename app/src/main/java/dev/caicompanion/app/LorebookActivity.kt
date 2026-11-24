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
    private var pendingExport: String? = null
    private var picker = 0
    private var busy = false
    private val closed = AtomicBoolean(false)
    private lateinit var summary: TextView
    private lateinit var status: TextView
    private lateinit var entries: ListView
    private lateinit var exportJson: Button
    private lateinit var exportText: Button
    private lateinit var copyText: Button
    private data class Retained(val book: LorebookPolicy.Book?, val picker: Int, val export: String?)

    override fun onCreate(savedInstanceState: Bundle?) {
        ShellAppearance.apply(this)
        super.onCreate(savedInstanceState)
        if (SettingsStore(this).protectScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        (lastNonConfigurationInstance as? Retained)?.let {
            book = it.book; picker = it.picker; pendingExport = it.export
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
        button(R.string.lorebook_open) { replace { open() } }
        button(R.string.lorebook_paste) { replace { paste() } }
        summary = TextView(this).apply { textSize = 16f; setPadding(0, dp(12), 0, dp(8)) }
        column.addView(summary)
        status = TextView(this)
        column.addView(status)
        entries = ListView(this)
        column.addView(entries, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)))
        entries.setOnItemClickListener { _, _, index, _ ->
            book?.entries?.getOrNull(index)?.let { entry ->
                AlertDialog.Builder(this).setTitle(entry.title.ifBlank { getString(R.string.lorebook_untitled) })
                    .setMessage(entry.keys.joinToString(", ") + "\n\n" + entry.content)
                    .setPositiveButton(R.string.close, null).show()
            }
        }
        exportJson = button(R.string.lorebook_export_json) { prepareExport(false, false) }
        exportText = button(R.string.lorebook_export_text) { prepareExport(true, false) }
        copyText = button(R.string.lorebook_copy) { prepareExport(true, true) }
        button(R.string.close) { finish() }
        setContentView(scroll)
        column.requestApplyInsets()
        refresh()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value + .5f).toInt()
    private fun replace(action: () -> Unit) {
        if (book == null) action() else AlertDialog.Builder(this).setMessage(R.string.lorebook_replace)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.confirm) { _, _ -> action() }.show()
    }

    private fun refresh() {
        val current = book
        summary.text = if (current == null) getString(R.string.lorebook_empty) else
            getString(R.string.lorebook_summary, current.name.ifBlank { getString(R.string.lorebook_untitled) },
                current.entries.size, current.needsReview, current.skippedDisabled) +
                if (current.conversionLoss) "\n" + getString(R.string.lorebook_loss) else ""
        entries.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            current?.entries?.map { it.title.ifBlank { getString(R.string.lorebook_untitled) } } ?: emptyList<String>())
        exportJson.isEnabled = current != null && !busy
        exportText.isEnabled = current != null && !busy
        copyText.isEnabled = current != null && !busy
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
        if (current.needsReview > 0 || current.conversionLoss || current.skippedDisabled > 0 || current.name.isBlank()) {
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
                contentResolver.openInputStream(uri)?.let { input ->
                    InputStreamReader(input, decoder).use { reader ->
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

    override fun onRetainNonConfigurationInstance(): Any = Retained(book, picker, pendingExport)
    override fun onDestroy() {
        closed.set(true)
        super.onDestroy()
    }
}
