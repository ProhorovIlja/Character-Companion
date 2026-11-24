package dev.caicompanion.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Manual model file library preview. No engine activation. */
class MandyModelActivity : Activity() {
    companion object {
        private val worker = Executors.newSingleThreadExecutor()
        private const val PICK_MODEL = 607
    }
    private val stopped = AtomicBoolean(false)
    private lateinit var status: TextView
    private lateinit var inventory: TextView
    private lateinit var importButton: Button
    private lateinit var cancelButton: Button
    private var busy = false
    override fun onCreate(savedInstanceState: Bundle?) {
        ShellAppearance.apply(this)
        super.onCreate(savedInstanceState)
        if (SettingsStore(this).protectScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        status = TextView(this).apply { text = "Model library • local files only"; textSize = 20f }
        inventory = TextView(this).apply { setTextIsSelectable(true) }
        importButton = Button(this).apply {
            text = "Import GGUF"
            isAllCaps = false
            setOnClickListener {
                if (!busy) AlertDialog.Builder(this@MandyModelActivity)
                    .setTitle("Import a local model")
                    .setMessage("Choose a trusted GGUF file. The app copies it into private storage and computes SHA-256. This does not prove publisher authenticity or activate inference.")
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton("Choose file") { _, _ ->
                        try { startActivityForResult(AndroidModelImport.picker(), PICK_MODEL) }
                        catch (_: android.content.ActivityNotFoundException) { status.text = "No document picker available." }
                    }.show()
            }
        }
        cancelButton = Button(this).apply {
            text = "Cancel import"; isAllCaps = false; isEnabled = false
            setOnClickListener { stopped.set(true); text = "Cancelling…" }
        }
        panel.addView(status); panel.addView(importButton); panel.addView(cancelButton); panel.addView(inventory)
        setContentView(ScrollView(this).apply { addView(panel) })
        refresh()
    }
    private fun refresh() {
        worker.execute {
            val result = runCatching { NativeModelLibrary(applicationContext.noBackupFilesDir.toPath().resolve("mandy-models-v1")).list() }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) inventory.text = result.fold(
                    { models -> if (models.isEmpty()) "No imported models." else models.joinToString("\n\n") { "SHA-256: " + it.sha256 + "\nBytes: " + it.bytes } },
                    { "Model inventory unavailable. Files have not been reset." })
            }
        }
    }
    @Deprecated("Activity result for the preview")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_MODEL || resultCode != RESULT_OK || busy) return
        val uri = data?.data ?: return
        stopped.set(false)
        busy = true; importButton.isEnabled = false; cancelButton.isEnabled = true
        status.text = "Importing and hashing…"
        worker.execute {
            val result = runCatching { AndroidModelImport.copy(applicationContext, uri, cancelled = stopped::get) }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                busy = false; importButton.isEnabled = true; cancelButton.isEnabled = false; cancelButton.text = "Cancel import"
                status.text = if (result.isSuccess) "Imported • runtime not connected" else "Import failed or cancelled. Existing models preserved."
                refresh()
            }
        }
    }
    override fun onStop() {
        stopped.set(true)
        super.onStop()
    }
}
