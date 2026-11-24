package dev.caicompanion.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
                                val memory: NativeCharacterMemory.State,
                                val speechModels: List<NativeModelLibrary.Model> = emptyList())
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
    private val resident = ResidentModel()
    private lateinit var cancelGeneration: Button
    private val dictation = NativeDictation()
    private var speechToken: UUID? = null
    private var recognizer: SpeechRecognizer? = null
    private var offlineSpeech: OfflineSpeechSession? = null
    private var speechStates: Map<View, Boolean> = emptyMap()
    private lateinit var speechControls: LinearLayout
    private lateinit var stopSpeech: Button
    private lateinit var sendSpeech: Button
    private var resumed = false
    /** Sample system memory only during explicitly requested local inference. */
    private fun admitModel(model: NativeModelLibrary.Model, contextTokens: Int) {
        val manager = applicationContext.getSystemService(android.app.ActivityManager::class.java)
            ?: throw ModelAdmission.Denied(ModelAdmission.Decision(ModelAdmission.Reason.INVALID_METRICS, 0, 0))
        val info = android.app.ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        ModelAdmission.requireAllowed(model.bytes, ModelAdmission.Memory(info.availMem, info.totalMem, info.threshold, info.lowMemory), contextTokens)
    }
    private fun admissionMessage(error: ModelAdmission.Denied): String {
        val decision = error.decision
        return when (decision.reason) {
            ModelAdmission.Reason.INVALID_METRICS -> "System memory information unavailable"
            ModelAdmission.Reason.LOW_MEMORY -> "Android reports low memory. Close other apps or select a smaller model."
            else -> "Conservative memory check refused model. Available " + decision.available / ModelAdmission.MIB +
                " MiB; estimated minimum " + decision.required / ModelAdmission.MIB + " MiB."
        }
    }
    private fun performancePrefs() = getSharedPreferences("mandy_performance_v1", MODE_PRIVATE)
    private fun performancePreset() = InferencePreset.fromKey(runCatching {
        performancePrefs().getString("preset", null)
    }.getOrNull())
    private fun choosePerformancePreset() {
        val presets = InferencePreset.entries
        val labels = presets.map { it.label + " · " + it.contextTokens + " context / " +
            it.outputTokens + " output · " + it.threads + " CPU threads" }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Local inference preset (experimental)")
            .setSingleChoiceItems(labels, presets.indexOf(performancePreset())) { dialog, index ->
                dialog.dismiss()
                saveAnd { check(performancePrefs().edit().putString("preset", presets[index].key).commit()) }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
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
        speechControls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        stopSpeech = Button(this).apply {
            text = "Stop · keep draft"; isAllCaps = false
            setOnClickListener { finishSpeech(false) }
        }
        sendSpeech = Button(this).apply {
            text = "Stop · send"; isAllCaps = false
            setOnClickListener { finishSpeech(true) }
        }
        speechControls.addView(stopSpeech)
        speechControls.addView(sendSpeech)
        speechControls.addView(Button(this).apply {
            text = "Cancel dictation"; isAllCaps = false
            setOnClickListener { cancelSpeech("Dictation cancelled") }
        })
        root.addView(speechControls)
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
        val speechModels = if (BuildConfig.MANDY_WHISPER_PREVIEW) AndroidModelImport.library(applicationContext, speech = true).list() else emptyList()
        if (ticket == null) return Snapshot(profiles, null, NativeChatWorkspace.State(), emptyList(), models, NativeCharacterMemory.State(), speechModels)
        library.resolve(ticket.key)
        val rows = AndroidConversationStore(applicationContext, account).use {
            it.page(ticket.key.character, ticket.key.chat, 50).asReversed()
        }
        return Snapshot(profiles, ticket, workspace.state(ticket.key), rows, models, memory.get(ticket.key.character), speechModels)
    }
    private fun suspendInputs(view: View, states: MutableMap<View, Boolean>) {
        states[view] = view.isEnabled
        view.isEnabled = false
        if (view is ViewGroup) for (i in 0 until view.childCount) suspendInputs(view.getChildAt(i), states)
    }
    private fun work(afterSuccess: (Snapshot) -> Unit = {}, action: () -> Unit) {
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
                    afterSuccess(it)
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
        if (BuildConfig.MANDY_EMBEDDED_PREVIEW) {
            panel.addView(button("Performance · " + performancePreset().label) { choosePerformancePreset() })
            panel.addView(button("Release loaded model") { saveAnd { resident.close() } })
        }
        panel.addView(button("Create character") { editProfile(null) })
        if (s.profiles.isNotEmpty()) panel.addView(button("Characters & conversations") { choose(s) })
        val ticket = s.ticket ?: return
        val profile = s.profiles.first { it.id == ticket.key.character }
        val chat = profile.chats.first { it.id == ticket.key.chat }
        panel.addView(label(profile.name, 22f))
        panel.addView(label(chat.title, 14f))
        panel.addView(button("Edit character") { editProfile(profile) })
        panel.addView(button("Memory & relationship") { editMemory(profile, s.memory) })
        if (BuildConfig.MANDY_EMBEDDED_PREVIEW && s.models.isNotEmpty() && s.rows.lastOrNull()?.role == "character" && s.draft.pending == null) {
            panel.addView(button("Review relationship from dialogue…") { chooseAssessmentModel(s) })
            if (s.memory.memories.size < NativeCharacterMemory.MAX_MEMORIES) panel.addView(button("Review memory from dialogue…") { chooseAssessmentModel(s, forMemory = true) })
        }
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
        panel.addView(button("Dictate on device…") { chooseSpeechLanguage(s) })
        if (BuildConfig.MANDY_WHISPER_PREVIEW) {
            if (s.speechModels.isNotEmpty()) panel.addView(button("Dictate with local Whisper…") { chooseOfflineSpeech(s) })
            else panel.addView(label("Import a trusted multilingual Whisper model in AI models, then refresh."))
        }
        panel.addView(button("Save draft") { saveAnd {} })
        if (BuildConfig.MANDY_EMBEDDED_PREVIEW) {
            panel.addView(button("Refresh model inventory") { saveAnd {} })
            if (s.models.isNotEmpty()) panel.addView(button(if (s.draft.pending == null) "Experimental send…" else "Retry pending…") { chooseModel(s) })
            else panel.addView(label("Import a trusted model in AI models, then refresh."))
        } else panel.addView(Button(this).apply { text = "Send • embedded build unavailable"; isAllCaps = false; isEnabled = false })
    }
    private fun chooseModel(s: Snapshot, mode: String = "text") {
        val ticket = s.ticket ?: return
        val draft = editor?.text?.toString() ?: return
        if (s.draft.pending == null && draft.isBlank()) { editor?.error = "Enter a message"; return }
        val preset = performancePreset()
        val labels = s.models.map { it.sha256 + "\nBytes: " + it.bytes }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Choose a trusted local model").setItems(labels) { _, index ->
            val model = s.models[index]
            AlertDialog.Builder(this).setTitle("Experimental inference")
                .setMessage("Only continue if you trust the publisher of this file. Real model compatibility is not yet verified. This may be slow or fail. No network fallback.\nPreset: " + preset.label + "\nSHA-256: " + model.sha256)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Generate locally") { _, _ -> generate(ticket, draft, model.sha256, s.draft.pending != null, mode, preset) }.show()
        }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun generate(ticket: NativeChatWorkspace.Ticket, draft: String, sha: String, retry: Boolean, mode: String, preset: InferencePreset) {
        if (!resumed || busy || !alive() || !BuildConfig.MANDY_EMBEDDED_PREVIEW || current?.ticket != ticket) return
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
            var metrics: GenerationMetrics? = null
            var preparation: ModelPreparation? = null
            val result = runCatching {
                workspace.edit(ticket, draft)
                saved = true
                if (!retry) workspace.prepare(ticket, mode)
                AndroidConversationStore(applicationContext, account).use { history ->
                    EmbeddedChatTurn.sendWithMemory(workspace, ticket, library, history, modelLibrary(), sha, memory, stopped::get, admit = { admitModel(it, preset.contextTokens) }, preset = preset, resident = resident, onMetrics = { metrics = it },
                        onPreparation = { preparation = it }) { origin, part ->
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
                    result.isSuccess -> metrics?.let { m ->
                        "Reply saved · " + m.promptTokens + " input / " + m.outputTokens + " output tokens · " +
                            m.retainedMessages + " messages · " + m.fittingAttempts + " fitting attempts\n" +
                            "Fit " + m.fitNanos / 1_000_000 + " ms · context " + m.contextNanos / 1_000_000 +
                            " ms · prefill " + m.prefillNanos / 1_000_000 + " ms · generation " + m.generationNanos / 1_000_000 + " ms" +
                            (preparation?.let { prep ->
                                "\n" + (if (prep.reused) "Resident model" else "Model loaded") +
                                    " · verify " + prep.verifyNanos / 1_000_000 + " ms · admission " +
                                    prep.admissionNanos / 1_000_000 + " ms · load " + prep.loadNanos / 1_000_000 + " ms"
                            } ?: "")
                    } ?: "Reply saved"
                    stopped.get() -> "Stopped • check saved draft or pending message"
                    result.exceptionOrNull() is ModelAdmission.Denied -> admissionMessage(result.exceptionOrNull() as ModelAdmission.Denied) + " • pending message retained"
                    else -> "Generation failed • check saved draft or retry state"
                }
            }
        }
    }
    private fun chooseSpeechLanguage(s: Snapshot) {
        if (!resumed || busy || current?.ticket != s.ticket || s.ticket == null) return
        if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            status.text = "On-device recognition unavailable. No remote recognizer will be used."
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Grant microphone permission, then tap Dictate again."
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 940)
            return
        }
        val languages = MandyLanguage.entries
        val names = languages.map { it.displayName }
        AlertDialog.Builder(this).setTitle("On-device dictation language").setItems(names.toTypedArray()) { _, index ->
            beginSpeech(s, languages[index])
        }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun chooseOfflineSpeech(s: Snapshot) {
        if (!resumed || busy || !BuildConfig.MANDY_WHISPER_PREVIEW || current?.ticket != s.ticket || s.ticket == null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "Grant microphone permission, then tap Dictate again."
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 940)
            return
        }
        val labels = s.speechModels.map { it.sha256 + "\nBytes: " + it.bytes }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Trusted offline speech model").setItems(labels) { _, index ->
            val model = s.speechModels[index]
            AlertDialog.Builder(this).setTitle("Experimental offline dictation")
                .setMessage("Use only a trusted multilingual model. The microphone and recognition remain local. The result must be reviewed in the draft; speech detection is not validated. The loaded chat model will be released before recording.\nSHA-256: " + model.sha256)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Choose language") { _, _ ->
                    val languages = MandyLanguage.entries
                    AlertDialog.Builder(this).setTitle("Offline dictation language")
                        .setItems(languages.map { it.displayName }.toTypedArray()) { _, selected ->
                            beginSpeech(s, languages[selected], model)
                        }.setNegativeButton(android.R.string.cancel, null).show()
                }.show()
        }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun beginSpeech(s: Snapshot, language: MandyLanguage, offline: NativeModelLibrary.Model? = null) {
        val ticket = s.ticket ?: return
        val field = editor ?: return
        if (!resumed || busy || !alive() || current?.ticket != ticket ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (offline != null && (!BuildConfig.MANDY_WHISPER_PREVIEW || offline !in s.speechModels)) return
        if (offline == null && (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this))) {
            status.text = "On-device recognition unavailable"
            return
        }
        val draft = field.text.toString()
        val start = field.selectionStart.takeIf { it >= 0 } ?: draft.length
        val end = field.selectionEnd.takeIf { it >= 0 } ?: start
        val canSend = offline == null && BuildConfig.MANDY_EMBEDDED_PREVIEW && s.draft.pending == null && s.models.isNotEmpty()
        val token = runCatching { dictation.begin(ticket, draft, start, end, canSend) }.getOrElse {
            status.text = "Draft is invalid for dictation"; return
        }
        speechToken = token
        busy = true
        val states = mutableMapOf<View, Boolean>()
        suspendInputs(panel, states)
        speechStates = states
        speechControls.visibility = View.VISIBLE
        stopSpeech.isEnabled = true
        sendSpeech.visibility = if (canSend) View.VISIBLE else View.GONE
        sendSpeech.isEnabled = canSend
        status.text = "Opening on-device microphone…"
        if (offline != null) {
            val session = OfflineSpeechSession()
            offlineSpeech = session
            val threads = performancePreset().threads
            disk.execute {
                val result = runCatching {
                    session.run(capture = { stop, cancelled ->
                        require(workspace.visible(ticket))
                        resident.close()
                        // Conservative existing admission estimate; actual ASR memory needs measurement.
                        admitModel(offline, 4096)
                        runOnUiThread {
                            if (alive() && resumed && speechToken == token && stopSpeech.isEnabled)
                                status.text = "Recording locally…"
                        }
                        AndroidPcmCapture.record(applicationContext, stopRequested = stop, cancelled = cancelled).samples
                    }, onTranscribing = {
                        runOnUiThread {
                            if (alive() && speechToken == token) {
                                stopSpeech.isEnabled = false
                                sendSpeech.isEnabled = false
                                status.text = "Transcribing locally…"
                            }
                        }
                    }) { pcm, cancelled ->
                        admitModel(offline, 4096)
                        EmbeddedWhisper.transcribe(offline.path, offline.sha256, pcm, language, threads, cancelled)
                    }
                }
                runOnUiThread {
                    if (!alive() || speechToken != token) return@runOnUiThread
                    if (!resumed) { cancelSpeech("Dictation cancelled"); return@runOnUiThread }
                    result.onSuccess { completeSpeech(token, ticket, listOf(it)) }
                        .onFailure { error ->
                            cancelSpeech(if (error is ModelAdmission.Denied) admissionMessage(error) + " · draft preserved"
                                else "Offline transcription failed or cancelled · draft preserved")
                        }
                }
            }
            return
        }
        try {
            val engine = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            recognizer = engine
            engine.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { if (speechToken == token && resumed && stopSpeech.isEnabled) status.text = "Listening on device…" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (speechToken == token) {
                        stopSpeech.isEnabled = false; sendSpeech.isEnabled = false
                        status.text = "Transcribing…"
                    }
                }
                override fun onError(error: Int) {
                    if (speechToken == token) cancelSpeech("Recognition failed or language unavailable · draft preserved")
                }
                override fun onResults(results: Bundle?) {
                    if (speechToken != token) return
                    if (!resumed || !alive()) { cancelSpeech("Dictation cancelled"); return }
                    completeSpeech(token, ticket, results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
            }
            engine.startListening(intent)
        } catch (_: Exception) {
            cancelSpeech("On-device recognition could not start · draft preserved")
        }
    }

    private fun completeSpeech(token: UUID, ticket: NativeChatWorkspace.Ticket, candidates: List<String>?) {
        if (speechToken != token || !alive() || !resumed) return
        val result = dictation.complete(token, current?.ticket, editor?.text?.toString(),
            candidates)
        releaseSpeech(token, cancel = false)
        if (result == null) { status.text = "No valid transcript · draft preserved"; return }
        val target = editor ?: return
        if (current?.ticket != ticket) return
        target.setText(result.edit.text)
        target.setSelection(result.edit.caret)
        // Persist before asking to send. Failed persistence leaves editable visible text.
        work(afterSuccess = { updated ->
            if (updated.ticket == ticket) {
                editor?.setSelection(result.edit.caret.coerceIn(0, editor?.length() ?: 0))
                status.text = "Dictation saved to draft"
                if (result.requestSend && resumed && updated.draft.pending == null && updated.models.isNotEmpty())
                    chooseModel(updated, mode = "dictation")
            }
        }) { workspace.edit(ticket, result.edit.text) }
    }

    private fun finishSpeech(send: Boolean) {
        val token = speechToken ?: return
        if (!resumed || !stopSpeech.isEnabled || (send && !sendSpeech.isEnabled)) return
        val offline = offlineSpeech
        if (offline != null && (send || !offline.requestStop())) return
        if (!dictation.stop(token, send)) return
        stopSpeech.isEnabled = false; sendSpeech.isEnabled = false
        status.text = "Transcribing…"
        if (offline != null) return
        try { checkNotNull(recognizer).stopListening() }
        catch (_: Exception) { cancelSpeech("Recognition failed · draft preserved") }
    }

    private fun releaseSpeech(token: UUID, cancel: Boolean) {
        if (speechToken != token) return
        speechToken = null
        val offline = offlineSpeech
        offlineSpeech = null
        if (cancel) offline?.cancel()
        val engine = recognizer
        recognizer = null
        if (cancel) runCatching { engine?.cancel() }
        runCatching { engine?.destroy() }
        speechControls.visibility = View.GONE
        speechStates.forEach { (view, enabled) -> view.isEnabled = enabled }
        speechStates = emptyMap()
        busy = false
    }

    private fun cancelSpeech(message: String) {
        val token = speechToken ?: return
        dictation.cancel(token)
        releaseSpeech(token, cancel = true)
        status.text = message
    }

    override fun onResume() {
        super.onResume()
        resumed = true
    }

    override fun onPause() {
        resumed = false
        stopped.set(true)
        // Serialize lifecycle cleanup after any in-flight explicit inference; never load here.
        disk.execute { resident.close() }
        cancelSpeech("Dictation cancelled · draft preserved")
        super.onPause()
    }

    override fun onDestroy() {
        cancelSpeech("Dictation cancelled")
        super.onDestroy()
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
    private data class DialogueReview(val details: String, val apply: (AndroidConversationStore) -> Unit)

    private fun chooseAssessmentModel(s: Snapshot, forMemory: Boolean = false) {
        val ticket = s.ticket ?: return
        val labels = s.models.map { it.sha256 + "\nBytes: " + it.bytes }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Select a trusted local assessment model").setItems(labels) { _, index ->
            val sha = s.models[index].sha256
            AlertDialog.Builder(this).setTitle(if (forMemory) "Review memory" else "Review fictional relationship")
                .setMessage("Analyze the latest completed dialogue locally. Nothing is changed without your approval. Only continue if you trust the model publisher. Experimental inference may fail.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Assess") { _, _ -> assessDialogue(ticket, sha, forMemory) }.show()
        }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun assessDialogue(ticket: NativeChatWorkspace.Ticket, sha: String, forMemory: Boolean) {
        if (!resumed || busy || !alive() || !BuildConfig.MANDY_EMBEDDED_PREVIEW || current?.ticket != ticket) return
        busy = true
        stopped.set(false)
        val states = mutableMapOf<View, Boolean>()
        suspendInputs(panel, states)
        cancelGeneration.visibility = View.VISIBLE
        cancelGeneration.isEnabled = true
        val preset = performancePreset()
        status.text = "Assessing latest completed dialogue…"
        disk.execute {
            val result = runCatching {
                require(workspace.visible(ticket))
                AndroidConversationStore(applicationContext, account).use { history ->
                    val state = memory.get(ticket.key.character)
                    val generate: (List<NativeConversation.Message>, () -> Boolean) -> String = { messages, stop ->
                        resident.useModel(modelLibrary(), sha, stop, { admitModel(it, preset.contextTokens) }) { engine ->
                            engine.generate(messages, contextTokens = preset.contextTokens, outputTokens = minOf(384, preset.outputTokens),
                                cancelled = stop, threads = preset.threads, onText = {})
                        }
                    }
                    if (forMemory) {
                        MemoryAssessment.propose(history, ticket.key, state, stopped::get, generate)?.let { proposal ->
                            val details = "Proposed memory:\n" + proposal.note +
                                "\n\nOriginal user dialogue:\n" + proposal.evidence.user.text +
                                "\n\nCharacter reply:\n" + proposal.evidence.reply.text
                            DialogueReview(details) { store ->
                                MemoryAssessment.apply(store, memory, ticket.key, proposal, approved = true)
                            }
                        }
                    } else {
                        RelationshipAssessment.propose(history, ticket.key, state, stopped::get, generate)?.let { proposal ->
                            val details = "Proposed stage: " + proposal.stage.name.lowercase(java.util.Locale.ROOT).replace('_', ' ') +
                                "\n\nProposed note:\n" + proposal.note +
                                "\n\nEvidence — user:\n" + proposal.evidence.user.text +
                                "\n\nEvidence — character:\n" + proposal.evidence.reply.text
                            DialogueReview(details) { store ->
                                RelationshipAssessment.apply(store, memory, ticket.key, proposal, approved = true)
                            }
                        }
                    }
                }
            }
            runOnUiThread {
                if (!alive()) return@runOnUiThread
                busy = false
                cancelGeneration.visibility = View.GONE
                states.forEach { (view, enabled) -> view.isEnabled = enabled }
                if (stopped.get() || current?.ticket != ticket) {
                    status.text = "Assessment stopped"
                    return@runOnUiThread
                }
                result.onSuccess { proposal ->
                    if (proposal == null) status.text = "No new change proposed"
                    else {
                        status.text = "Review proposed change"
                        AlertDialog.Builder(this).setTitle(if (forMemory) "Memory proposal" else "Relationship proposal").setMessage(proposal.details)
                            .setNegativeButton("Reject", null)
                            .setPositiveButton("Approve") { _, _ ->
                                saveAnd {
                                    require(workspace.visible(ticket))
                                    AndroidConversationStore(applicationContext, account).use { history ->
                                        proposal.apply(history)
                                    }
                                }
                            }.show()
                    }
                }.onFailure { error -> status.text = if (error is ModelAdmission.Denied) admissionMessage(error) else "Assessment unavailable • memory and relationship unchanged" }
            }
        }
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
        val notes = input("Relevant memories, one per line", NativeCharacterMemory.MAX_EDITOR_UNITS, state.memories.joinToString("\n"))
        fields.addView(label("Fictional relationship"))
        fields.addView(stage)
        fields.addView(label("Relationship note"))
        fields.addView(relationship)
        fields.addView(label("Pinned memory"))
        fields.addView(pinned)
        fields.addView(label("Up to 128 memories, 400 characters each. Remove a line to remove a memory."))
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
        if (speechToken != null) { cancelSpeech("Dictation cancelled · draft preserved"); return }
        if (busy) { stopped.set(true); return }
        if (editor?.text?.toString() != current?.draft?.draft && editor != null) {
            AlertDialog.Builder(this).setMessage("Unsaved draft. Save it before leaving?")
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton("Discard changes") { _, _ -> finish() }
                .setPositiveButton("Save draft") { _, _ -> saveAnd {} }.show()
        } else finish()
    }
}
