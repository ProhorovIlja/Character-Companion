package dev.caicompanion.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.io.IOException

/** User-controlled local dictation workspace; never accesses the Character.AI page DOM. */
class VoiceDraftActivity : Activity() {
    companion object {
        private const val AUDIO_PERMISSION_REQUEST = 927
        private const val SYSTEM_RECOGNITION_REQUEST = 928
        private const val SAVE_TEXT_REQUEST = 929
    }

    private lateinit var editor: EditText
    private lateinit var status: TextView
    private lateinit var listenButton: Button
    private lateinit var languageSpinner: Spinner
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never place the unsent dictation in a saved instance-state Bundle.
        if (SettingsStore(this).protectScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(17, 18, 25))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    view.setPadding(bars.left + dp(14), bars.top + dp(10),
                        bars.right + dp(14), bars.bottom + dp(10))
                }
                insets
            }
        }
        setContentView(root)
        root.requestApplyInsets()
        root.addView(TextView(this).apply {
            text = getString(R.string.voice_title)
            textSize = 21f
            setTextColor(Color.rgb(201, 169, 113))
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.voice_privacy)
            textSize = 12f
            setTextColor(Color.rgb(200, 195, 190))
        })
        languageSpinner = Spinner(this)
        val languageNames = listOf(getString(R.string.voice_lang_device),
            getString(R.string.voice_lang_ru), getString(R.string.voice_lang_en))
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languageNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter
        root.addView(languageSpinner)
        editor = EditText(this).apply {
            hint = getString(R.string.voice_hint)
            setTextColor(Color.rgb(235, 225, 211))
            setHintTextColor(Color.rgb(175, 170, 165))
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 5
            maxLines = 16
            filters = arrayOf(android.text.InputFilter.LengthFilter(VoiceDraftPolicy.MAX_CHARS))
        }
        root.addView(editor, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        status = TextView(this).apply { setTextColor(Color.rgb(200, 195, 190)) }
        status.setText(R.string.voice_idle)
        root.addView(status)
        listenButton = Button(this).apply {
            setText(R.string.voice_start_local)
            setOnClickListener { if (listening) stopListening() else startOnDevice() }
        }
        root.addView(listenButton)
        root.addView(Button(this).apply {
            setText(R.string.voice_system)
            setOnClickListener { confirmSystemRecognizer() }
        })
        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER }
        fun action(label: Int, handler: () -> Unit) {
            actions.addView(Button(this).apply { setText(label); setOnClickListener { handler() } },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        action(R.string.voice_copy) { copyDraft() }
        action(R.string.voice_save) { saveDraft() }
        action(R.string.voice_clear) { clearDraft() }
        root.addView(actions)
    }

    private fun dp(px: Int) = (px * resources.displayMetrics.density + 0.5f).toInt()

    private fun requestIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        val language = when (languageSpinner.selectedItemPosition) {
            1 -> "ru-RU"
            2 -> "en-US"
            else -> null
        }
        if (language != null) putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
    }

    private fun startOnDevice() {
        if (listening) return
        if (Build.VERSION.SDK_INT < 31 || !runCatching {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
            }.getOrDefault(false)) {
            status.setText(R.string.voice_local_missing)
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), AUDIO_PERMISSION_REQUEST)
            return
        }
        try {
            val created = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            recognizer = created
            created.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { status.setText(R.string.voice_listening) }
                override fun onBeginningOfSpeech() { status.setText(R.string.voice_listening) }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { status.setText(R.string.voice_processing) }
                override fun onError(error: Int) {
                    finishListening()
                    status.text = getString(R.string.voice_recognition_error, error)
                }
                override fun onResults(results: Bundle?) {
                    finishListening()
                    acceptRecognition(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull())
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            listening = true
            listenButton.setText(R.string.voice_stop)
            status.setText(R.string.voice_listening)
            created.startListening(requestIntent())
        } catch (_: RuntimeException) {
            finishListening()
            status.setText(R.string.voice_local_missing)
        }
    }

    private fun stopListening() {
        // Cancel does not return an incomplete transcript; the prior draft is untouched.
        runCatching { recognizer?.cancel() }
        finishListening()
        status.setText(R.string.voice_idle)
    }

    private fun finishListening() {
        listening = false
        if (::listenButton.isInitialized) listenButton.setText(R.string.voice_start_local)
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun confirmSystemRecognizer() {
        if (listening) return
        AlertDialog.Builder(this)
            .setTitle(R.string.voice_system)
            .setMessage(R.string.voice_system_warning)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.voice_continue) { _, _ ->
                try {
                    startActivityForResult(requestIntent(), SYSTEM_RECOGNITION_REQUEST)
                } catch (_: ActivityNotFoundException) {
                    status.setText(R.string.voice_system_missing)
                } catch (_: SecurityException) {
                    status.setText(R.string.voice_system_missing)
                }
            }.show()
    }

    private fun acceptRecognition(candidate: String?) {
        val merged = VoiceDraftPolicy.append(editor.text.toString(), candidate)
        if (merged == null) status.setText(R.string.voice_empty_or_full)
        else {
            editor.setText(merged)
            editor.setSelection(merged.length)
            status.setText(R.string.voice_ready)
        }
    }

    private fun copyDraft() {
        val content = editor.text.toString()
        if (content.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.voice_title), content))
        status.setText(R.string.voice_copied)
    }

    private fun saveDraft() {
        if (editor.text.isBlank()) return
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "character-companion-draft.txt")
            }, SAVE_TEXT_REQUEST)
        } catch (_: ActivityNotFoundException) {
            status.setText(R.string.voice_save_failed)
        } catch (_: SecurityException) {
            status.setText(R.string.voice_save_failed)
        }
    }

    private fun clearDraft() {
        if (editor.text.isBlank()) return
        AlertDialog.Builder(this)
            .setMessage(R.string.voice_clear_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.voice_clear) { _, _ ->
                editor.text.clear()
                status.setText(R.string.voice_idle)
            }.show()
    }

    @Deprecated("Activity Result API works with all supported Android versions")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            SYSTEM_RECOGNITION_REQUEST -> acceptRecognition(
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull())
            SAVE_TEXT_REQUEST -> {
                val target = data?.data ?: return
                try {
                    val output = contentResolver.openOutputStream(target, "w")
                        ?: throw IOException("No output stream")
                    output.use { it.write(editor.text.toString().toByteArray(Charsets.UTF_8)) }
                    status.setText(R.string.voice_saved)
                } catch (_: Exception) { status.setText(R.string.voice_save_failed) }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AUDIO_PERMISSION_REQUEST) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startOnDevice()
            else status.setText(R.string.voice_permission_denied)
        }
    }

    override fun onPause() {
        // No listening while another Activity, the home screen or app switcher has focus.
        if (listening) stopListening()
        super.onPause()
    }

    override fun onDestroy() {
        finishListening()
        super.onDestroy()
    }
}
