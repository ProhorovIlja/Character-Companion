package dev.caicompanion.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Explicit document-picker transfer of local memory only. */
class MemoryBackupActivity : Activity() {
    private companion object {
        const val EXPORT = 871
        const val IMPORT = 872
        const val STATE_PICKER = "memory_picker"
    }
    private var picker = 0
    private lateinit var exportButton: Button
    private lateinit var importButton: Button
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        ShellAppearance.apply(this)
        super.onCreate(savedInstanceState)
        if (SettingsStore(this).protectScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        picker = savedInstanceState?.getInt(STATE_PICKER)?.takeIf { it == EXPORT || it == IMPORT } ?: 0
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(ShellAppearance.color(this@MemoryBackupActivity, R.attr.shellBackground))
            setOnApplyWindowInsetsListener { view, insets ->
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                }
                insets
            }
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPaddingRelative(dp(20), dp(20), dp(20), dp(20))
        }
        scroll.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(TextView(this).apply {
            setText(R.string.memory_transfer_title); textSize = 24f
            setTextColor(ShellAppearance.color(this@MemoryBackupActivity, R.attr.shellText))
        })
        column.addView(TextView(this).apply {
            setText(R.string.memory_transfer_warning); textSize = 14f
            setPadding(0, dp(12), 0, dp(20))
            setTextColor(ShellAppearance.color(this@MemoryBackupActivity, R.attr.shellMuted))
        })
        exportButton = action(column, R.string.memory_transfer_export) { choose(EXPORT) }
        importButton = action(column, R.string.memory_transfer_import) { choose(IMPORT) }
        status = TextView(this).apply {
            setPadding(0, dp(16), 0, dp(16))
            setTextColor(ShellAppearance.color(this@MemoryBackupActivity, R.attr.shellMuted))
            accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        column.addView(status)
        action(column, R.string.close) { finish() }
        setContentView(scroll)
        scroll.requestApplyInsets()
        enablePicker(picker == 0)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun action(column: LinearLayout, label: Int, click: () -> Unit): Button =
        Button(this).apply {
            setText(label)
            isAllCaps = false
            setOnClickListener { click() }
            column.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
        }

    private fun enablePicker(enabled: Boolean) {
        exportButton.isEnabled = enabled
        importButton.isEnabled = enabled
    }

    private fun choose(request: Int) {
        if (picker != 0 || isFinishing || isDestroyed) return
        val intent = Intent(if (request == EXPORT) Intent.ACTION_CREATE_DOCUMENT else Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(if (request == EXPORT) "application/octet-stream" else "*/*")
        if (request == EXPORT) intent.putExtra(Intent.EXTRA_TITLE, "character-memory.ccmx")
        picker = request
        enablePicker(false)
        try { startActivityForResult(intent, request) } catch (_: RuntimeException) {
            picker = 0; enablePicker(true); status.setText(R.string.roleplay_failed)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_PICKER, picker)
        super.onSaveInstanceState(outState)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != picker || requestCode !in listOf(EXPORT, IMPORT)) return
        picker = 0
        enablePicker(true)
        if (resultCode != RESULT_OK || isFinishing || isDestroyed) return
        val uri = data?.data
        if (uri?.scheme != "content") {
            status.setText(R.string.roleplay_failed)
            return
        }
        if (requestCode == EXPORT) export(uri) else inspect(uri)
    }

    private fun export(uri: Uri) {
        val context = applicationContext
        ManualEditorWork.load(this, {
            val entries = MemoryBankStore.load(context)
            val bytes = MemoryBankBackup.encode(entries)
            val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("No output")
            output.use { it.write(bytes); it.flush() }
            entries.size
        }) { result ->
            result.fold({ status.text = getString(R.string.memory_transfer_exported, it) },
                { status.setText(R.string.roleplay_failed) })
        }
    }

    private data class Preview(val current: List<MemoryBank.Entry>, val incoming: List<MemoryBank.Entry>,
                               val plan: MemoryBankBackup.Merge?)

    private fun inspect(uri: Uri) {
        val context = applicationContext
        ManualEditorWork.load(this, {
            val input = context.contentResolver.openInputStream(uri) ?: error("No input")
            val incoming = input.use { MemoryBankBackup.decode(it.buffered()) } ?: error("Invalid backup")
            val current = MemoryBankStore.load(context)
            Preview(current, incoming, MemoryBankBackup.merge(current, incoming))
        }) { result ->
            result.fold({ preview ->
                val plan = preview.plan
                if (plan == null) status.setText(R.string.memory_transfer_capacity)
                else {
                    val dialog = AlertDialog.Builder(this).setTitle(R.string.memory_transfer_import)
                        .setMessage(getString(R.string.memory_transfer_review, plan.added, plan.duplicates, plan.conflicts))
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.confirm) { _, _ -> applyMerge(preview) }.create()
                    if (SettingsStore(this).protectScreen) dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    dialog.show()
                }
            }, { status.setText(R.string.memory_transfer_invalid) })
        }
    }

    private fun applyMerge(preview: Preview) {
        val context = applicationContext
        ManualEditorWork.load(this, {
            MemoryBankStore.merge(context, preview.current, preview.incoming)
        }) { result ->
            result.fold({ status.text = getString(R.string.memory_transfer_imported, it.added) },
                { status.setText(R.string.roleplay_failed) })
        }
    }
}
