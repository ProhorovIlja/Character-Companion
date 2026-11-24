package dev.caicompanion.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private val recognitionGuard = VoiceRecognitionGuard()
    // Only an explicit save action creates a document. Never save a transcript to Bundle.
    private var pendingSaveText: String? = null
    private data class RetainedDraft(
        val text: String, val pendingSave: String?, val languageIndex: Int,
        val selectionStart: Int, val selectionEnd: Int
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Never place the unsent dictation in a saved instance-state Bundle.
        if (SettingsStore(this).protectScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        // Weighted EditText in a fixed vertical layout used to hide the action buttons
        // when the IME, landscape mode or large accessibility fonts reduced height.
        // Scroll the whole workspace instead; speech actions remain reachable.
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            clipToPadding = false
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        }
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
        scroll.addView(root, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)
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
            // Prevent landscape IMEs from replacing the editor with a full-screen form.
            imeOptions = imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            filters = arrayOf(android.text.InputFilter.LengthFilter(VoiceDraftPolicy.MAX_CHARS))
        }
        // Only retain the draft in the Activity's in-memory non-configuration instance.
        // Process death still clears it, rather than writing private words into OS state.
        (lastNonConfigurationInstance as? RetainedDraft)?.let { retained ->
            editor.setText(retained.text)
            val restored = VoiceDraftPolicy.selection(
                editor.text.toString(), retained.selectionStart, retained.selectionEnd)
            editor.setSelection(restored.first, restored.last)
            pendingSaveText = retained.pendingSave
            languageSpinner.setSelection(VoiceDraftPolicy.languageIndex(retained.languageIndex))
        }
        // A fixed minimum editor viewport permits natural outer scrolling even under
        // a small/landscape window. The editor itself scrolls for longer transcripts.
        root.addView(editor, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(196)))
        status = TextView(this).apply {
            setTextColor(Color.rgb(200, 195, 190))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
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
        // A deliberate handoff back to the browser: no DOM access, no auto-paste/send.
        root.addView(Button(this).apply {
            setText(R.string.voice_copy_return)
            setOnClickListener { copyAndReturn() }
        })
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
            val sessionToken = recognitionGuard.start()
            created.setRecognitionListener(object : RecognitionListener {
                private fun active() = recognitionGuard.isCurrent(sessionToken)
                override fun onReadyForSpeech(params: Bundle?) {
                    if (active()) status.setText(R.string.voice_listening)
                }
                override fun onBeginningOfSpeech() {
                    if (active()) status.setText(R.string.voice_listening)
                }
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (active()) status.setText(R.string.voice_processing)
                }
                override fun onError(error: Int) {
                    if (!recognitionGuard.finish(sessionToken)) return
                    finishListening()
                    status.text = when (VoiceRecognitionFeedback.issue(error)) {
                        VoiceIssue.NO_SPEECH -> getString(R.string.voice_error_no_speech)
                        VoiceIssue.LANGUAGE_NOT_SUPPORTED -> getString(R.string.voice_error_language_unsupported)
                        VoiceIssue.MODEL_MISSING -> getString(R.string.voice_error_model_missing)
                        VoiceIssue.BUSY -> getString(R.string.voice_error_busy)
                        VoiceIssue.PERMISSION -> getString(R.string.voice_permission_denied)
                        VoiceIssue.MICROPHONE -> getString(R.string.voice_error_microphone)
                        VoiceIssue.NETWORK_OR_SERVICE -> getString(R.string.voice_error_service)
                        VoiceIssue.OTHER -> getString(R.string.voice_recognition_error, error)
                    }
                }
                override fun onResults(results: Bundle?) {
                    if (!recognitionGuard.finish(sessionToken)) return
                    val candidate = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    finishListening()
                    acceptRecognition(candidate)
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
        // Invalidate first: cancelled providers may still deliver a queued result.
        recognitionGuard.cancel()
        runCatching { recognizer?.cancel() }
        finishListening()
        status.setText(R.string.voice_idle)
    }

    private fun finishListening() {
        recognitionGuard.cancel()
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
        // An unfocused EditText may report -1 for selection; in that case append
        // rather than discarding the user's recognized words without explanation.
        val original = editor.text.toString()
        val start = editor.selectionStart.takeIf { it >= 0 } ?: original.length
        val end = editor.selectionEnd.takeIf { it >= 0 } ?: start
        val edit = VoiceDraftPolicy.insert(original, candidate, start, end)
        if (edit == null) status.setText(R.string.voice_empty_or_full)
        else {
            editor.setText(edit.text)
            editor.setSelection(edit.caret)
            status.setText(R.string.voice_ready)
        }
    }

    /** Copy only on an explicit tap. Android clipboard is accessible outside this app. */
    private fun copyDraft(): Boolean {
        val content = editor.text.toString()
        if (content.isBlank()) {
            status.setText(R.string.voice_copy_empty)
            return false
        }
        val clip = ClipData.newPlainText(getString(R.string.voice_title), content)
        if (Build.VERSION.SDK_INT >= 33) {
            // Hides the recognized text from Android 13+ clipboard previews.
            // This does NOT encrypt or isolate the clipboard from other apps.
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        return try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(clip)
            status.setText(R.string.voice_copied)
            true
        } catch (_: RuntimeException) {
            status.setText(R.string.voice_copy_failed)
            false
        }
    }

    private fun copyAndReturn() {
        // Never dismiss the editor if clipboard handoff failed or was empty.
        if (copyDraft()) finish()
    }

    private fun saveDraft() {
        val snapshot = editor.text.toString().takeIf { it.isNotBlank() } ?: return
        if (pendingSaveText != null) return
        // SAF picker can outlive an Activity recreation. Keep the exact user-approved
        // text in memory; never write a blank/replaced draft after process death.
        pendingSaveText = snapshot
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"
                putExtra(Intent.EXTRA_TITLE, "character-companion-draft.txt")
            }, SAVE_TEXT_REQUEST)
        } catch (_: ActivityNotFoundException) {
            pendingSaveText = null
            status.setText(R.string.voice_save_failed)
        } catch (_: SecurityException) {
            pendingSaveText = null
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
        when (requestCode) {
            SYSTEM_RECOGNITION_REQUEST -> if (resultCode == RESULT_OK) acceptRecognition(
                data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull())
            SAVE_TEXT_REQUEST -> {
                val snapshot = pendingSaveText
                pendingSaveText = null
                if (resultCode != RESULT_OK) return
                // Process-death restoration does not recover private draft text.
                // Never open/truncate the output stream in that case.
                if (snapshot == null) { status.setText(R.string.voice_save_failed); return }
                val target = data?.data ?: return
                try {
                    val output = contentResolver.openOutputStream(target, "w")
                        ?: throw IOException("No output stream")
                    output.use { it.write(snapshot.toByteArray(Charsets.UTF_8)) }
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

    @Deprecated("Keep transient private draft in memory only across configuration changes")
    override fun onRetainNonConfigurationInstance(): Any =
        RetainedDraft(editor.text.toString(), pendingSaveText, languageSpinner.selectedItemPosition,
            editor.selectionStart, editor.selectionEnd)

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
