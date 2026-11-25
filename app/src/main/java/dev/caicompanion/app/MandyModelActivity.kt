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
        private const val PICK_SPEECH = 608
    }
    private val stopped = AtomicBoolean(false)
    private lateinit var status: TextView
    private lateinit var inventory: TextView
    private lateinit var importButton: Button
    private lateinit var cancelButton: Button
    private lateinit var speechButton: Button
    private var busy = false
    private var pendingImport: Pair<Int, String?>? = null
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
            setOnClickListener { if (!busy) chooseImport(false) }
        }
        speechButton = Button(this).apply {
            text = "Import Whisper speech model"
            isAllCaps = false
            setOnClickListener { if (!busy) chooseImport(true) }
        }
        cancelButton = Button(this).apply {
            text = "Cancel import"; isAllCaps = false; isEnabled = false
            setOnClickListener { stopped.set(true); text = "Cancelling…" }
        }
        panel.addView(status); panel.addView(importButton); panel.addView(speechButton); panel.addView(cancelButton); panel.addView(inventory)
        setContentView(ScrollView(this).apply { addView(panel) })
        refresh()
    }
    @Suppress("DEPRECATION")
    private fun chooseImport(speech: Boolean) {
        if (busy || pendingImport != null || isFinishing || isDestroyed) return
        val request = if (speech) PICK_SPEECH else PICK_MODEL
        val field = android.widget.EditText(this).apply {
            hint = "Optional trusted SHA-256 (64 hex digits)"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
            filters = arrayOf(android.text.InputFilter.LengthFilter(64))
            isSaveEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 8, 24, 8)
            addView(TextView(this@MandyModelActivity).apply {
                text = if (speech) "Choose a trusted multilingual Whisper GGML model. Local dictation requires an embedded speech build."
                else "Choose a trusted GGUF model. Import does not establish engine compatibility."
            })
            addView(TextView(this@MandyModelActivity).apply {
                text = "Optional: paste the SHA-256 from a trusted publisher or release manifest. A matching digest checks the selected bytes, not publisher authenticity. No model is loaded during import."
            })
            addView(field)
        }
        val dialog = AlertDialog.Builder(this).setTitle(if (speech) "Import speech model" else "Import local AI model")
            .setView(content).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Choose file", null).create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (busy || pendingImport != null || isFinishing || isDestroyed) return@setOnClickListener
            val value = field.text.toString().trim().lowercase(java.util.Locale.ROOT)
            if (value.isNotEmpty() && !value.matches(Regex("[0-9a-f]{64}"))) {
                field.error = "Use exactly 64 hexadecimal digits"
                return@setOnClickListener
            }
            pendingImport = request to value.ifEmpty { null }
            dialog.dismiss()
            try { startActivityForResult(AndroidModelImport.picker(speech), request) }
            catch (_: Exception) { pendingImport = null; status.text = "No document picker available." }
        }
    }
    private fun refresh() {
        worker.execute {
            val result = runCatching {
                fun describe(models: List<NativeModelLibrary.Model>): String =
                    if (models.isEmpty()) "No imported models." else models.joinToString("\n\n") { "SHA-256: " + it.sha256 + "\nBytes: " + it.bytes }
                "Text models (GGUF)\n" + describe(AndroidModelImport.library(applicationContext).list()) +
                    "\n\nSpeech models (Whisper GGML)\n" + describe(AndroidModelImport.library(applicationContext, speech = true).list())
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) inventory.text = result.fold(
                    { it },
                    { "Model inventory unavailable. Files have not been reset." })
            }
        }
    }
    @Deprecated("Activity result for the preview")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val selection = pendingImport ?: return
        if (requestCode != selection.first) return
        pendingImport = null
        if (resultCode != RESULT_OK || busy || isFinishing || isDestroyed) return
        val speech = requestCode == PICK_SPEECH
        val uri = data?.data ?: return
        if (uri.scheme != "content") { status.text = "Select one readable model file."; return }
        stopped.set(false)
        busy = true; importButton.isEnabled = false; speechButton.isEnabled = false; cancelButton.isEnabled = true
        status.text = "Importing and hashing…"
        worker.execute {
            val posted = AtomicBoolean(false)
            var lastUpdate = 0L
            val result = runCatching {
                AndroidModelImport.copy(applicationContext, uri, expectedSha = selection.second, cancelled = stopped::get,
                    progress = { bytes ->
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastUpdate >= 250 && posted.compareAndSet(false, true)) {
                            lastUpdate = now
                            runOnUiThread {
                                try {
                                    if (!isFinishing && !isDestroyed && busy && !stopped.get())
                                        status.text = "Importing and hashing · " + (bytes / (1024 * 1024)) + " MiB"
                                } finally { posted.set(false) }
                            }
                        }
                    }, speech = speech)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                busy = false; importButton.isEnabled = true; speechButton.isEnabled = true; cancelButton.isEnabled = false; cancelButton.text = "Cancel import"
                status.text = if (result.isSuccess) {
                    if (selection.second != null) "Imported · expected SHA-256 matched · not loaded"
                    else "Imported · SHA-256 recorded · not loaded"
                } else "Import failed or cancelled. Check format, storage and expected SHA-256. Existing models preserved."
                refresh()
            }
        }
    }
    override fun onStop() {
        stopped.set(true)
        super.onStop()
    }
}
