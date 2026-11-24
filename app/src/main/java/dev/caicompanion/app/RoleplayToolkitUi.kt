package dev.caicompanion.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.text.InputFilter
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** User-operated local editor and dice. Never reads or changes the site's DOM or account. */
object RoleplayToolkitUi {
    private fun active(activity: Activity) = !activity.isFinishing && !activity.isDestroyed

    private fun title(slot: RoleplayToolkit.Slot): Int = when (slot) {
        RoleplayToolkit.Slot.PERSONA -> R.string.roleplay_persona
        RoleplayToolkit.Slot.SCENE -> R.string.roleplay_scene
        RoleplayToolkit.Slot.MEMORY -> R.string.roleplay_memory
        RoleplayToolkit.Slot.PROMPT -> R.string.roleplay_prompt
    }

    private fun toast(activity: Activity, id: Int) =
        Toast.makeText(activity, id, Toast.LENGTH_SHORT).show()

    private fun showPrivate(activity: Activity, dialog: AlertDialog) {
        if (!active(activity)) return
        if (SettingsStore(activity).protectScreen)
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog.show()
    }

    fun show(activity: Activity) {
        if (!active(activity)) return
        val slots = RoleplayToolkit.Slot.entries
        val labels = slots.map { activity.getString(title(it)) } + listOf(
            activity.getString(R.string.roleplay_context), activity.getString(R.string.roleplay_web_search),
            activity.getString(R.string.roleplay_dice), activity.getString(R.string.memory_bank_title),
            activity.getString(R.string.memory_transfer_title))
        showPrivate(activity, AlertDialog.Builder(activity).setTitle(R.string.roleplay_title)
            .setItems(labels.toTypedArray()) { _, index ->
                if (active(activity)) {
                    val slot = slots.getOrNull(index)
                    if (slot != null) edit(activity, slot)
                    else when (index - slots.size) {
                        0 -> context(activity)
                        1 -> search(activity)
                        2 -> dice(activity)
                        3 -> memoryBank(activity)
                        4 -> activity.startActivity(Intent(activity, MemoryBackupActivity::class.java))
                    }
                }
            }.setNegativeButton(R.string.close, null).create())
    }

    private fun edit(activity: Activity, slot: RoleplayToolkit.Slot) {
        val context = activity.applicationContext
        ManualEditorWork.load(activity, { RoleplayToolkitStore.load(context, slot) }) { result ->
            editLoaded(activity, slot, result)
        }
    }

    private fun editLoaded(activity: Activity, slot: RoleplayToolkit.Slot, result: Result<String>) {
        val saved = result.getOrElse {
            // Do not silently replace unreadable data.
            showPrivate(activity, AlertDialog.Builder(activity).setTitle(title(slot))
                .setMessage(R.string.roleplay_damaged)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.roleplay_reset) { _, _ ->
                    if (active(activity)) {
                        val context = activity.applicationContext
                        ManualEditorWork.load(activity, { RoleplayToolkitStore.clear(context, slot) }) { cleared ->
                            if (cleared.isSuccess) edit(activity, slot)
                            else toast(activity, R.string.roleplay_failed)
                        }
                    }
                }.create())
            return
        }
        val editor = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            isSaveEnabled = false
            isSaveFromParentEnabled = false
            filters = arrayOf(InputFilter.LengthFilter(RoleplayToolkit.MAX_CHARS))
            minLines = 4
            maxLines = 9
            setText(saved)
        }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, 0)
            addView(TextView(activity).apply { setText(R.string.roleplay_privacy) })
            addView(editor)
            addView(characterCounter(activity, editor, RoleplayToolkit.MAX_CHARS))
        }
        val dialog = AlertDialog.Builder(activity).setTitle(title(slot)).setView(body)
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.character_draft_copy, null)
            .setPositiveButton(R.string.roleplay_save, null).create()
        showPrivate(activity, dialog)
        if (!dialog.isShowing) return
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            if (active(activity)) copy(activity, editor.text.toString())
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!active(activity)) return@setOnClickListener
            val text = editor.text.toString()
            if (!RoleplayToolkit.valid(text)) editor.error = activity.getString(R.string.roleplay_invalid)
            else {
                val context = activity.applicationContext
                ManualEditorWork.run(activity, dialog, listOf(editor),
                    { RoleplayToolkitStore.save(context, slot, text) }) { saved ->
                    if (saved.isSuccess) {
                        toast(activity, R.string.roleplay_saved)
                        dialog.dismiss()
                    } else editor.error = activity.getString(R.string.roleplay_failed)
                }
            }
        }
    }

    private fun copy(activity: Activity, text: String) {
        if (!active(activity) || text.isBlank() || !RoleplayToolkit.valid(text)) {
            toast(activity, R.string.roleplay_invalid)
            return
        }
        val ok = runCatching {
            val clip = ClipData.newPlainText(activity.getString(R.string.roleplay_title), text)
            if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
            (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        }.isSuccess
        toast(activity, if (ok) R.string.character_draft_copied else R.string.roleplay_failed)
    }

    private fun context(activity: Activity, recalled: String? = null) {
        val slots = RoleplayToolkit.Slot.entries.filter { recalled == null || it != RoleplayToolkit.Slot.MEMORY }
        val selected = BooleanArray(slots.size)
        showPrivate(activity, AlertDialog.Builder(activity).setTitle(
            if (recalled == null) R.string.roleplay_context else R.string.recall_context_add_fields)
            .setMultiChoiceItems(slots.map { activity.getString(title(it)) }.toTypedArray(), selected) { _, index, checked ->
                selected[index] = checked
            }.setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                if (active(activity)) {
                    val chosen = slots.filterIndexed { index, _ -> selected[index] }
                    val context = activity.applicationContext
                    ManualEditorWork.load(activity, { chosen.associateWith { RoleplayToolkitStore.load(context, it) } }) { result ->
                        result.fold({ data ->
                            val combined = if (recalled == null) data else RecallContext.combine(recalled, data)
                            if (combined != null) research(activity, combined)
                            else {
                                toast(activity, R.string.roleplay_context_invalid)
                                context(activity, recalled)
                            }
                        }, { toast(activity, R.string.roleplay_failed) })
                    }
                }
            }.create())
    }

    private fun privateEditor(activity: Activity, max: Int, singleLine: Boolean = false): EditText =
        EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_TEXT or if (singleLine) 0 else InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            isSaveEnabled = false
            isSaveFromParentEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            filters = arrayOf(InputFilter.LengthFilter(max))
            setSingleLine(singleLine)
            if (!singleLine) { minLines = 3; maxLines = 8 }
        }

    private fun characterCounter(activity: Activity, editor: EditText, limit: Int): TextView =
        TextView(activity).apply {
            textSize = 12f
            setTextColor(ShellAppearance.color(activity, R.attr.shellMuted))
            gravity = android.view.Gravity.END
            fun update(length: Int) { text = activity.getString(R.string.editor_character_count, length, limit) }
            update(editor.length())
            editor.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    update(s?.length ?: 0)
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }

    private fun countedEditor(activity: Activity, editor: EditText, limit: Int): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPaddingRelative(pad, 0, pad, 0)
            addView(editor)
            addView(characterCounter(activity, editor, limit))
        }

    private fun research(activity: Activity, slots: Map<RoleplayToolkit.Slot, String>) {
        val editor = privateEditor(activity, CharacterContext.MAX_PACKET)
        editor.setHint(R.string.roleplay_research_hint)
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.roleplay_context)
            .setMessage(R.string.roleplay_research_help).setView(countedEditor(activity, editor, CharacterContext.MAX_PACKET))
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.confirm, null).create()
        showPrivate(activity, dialog)
        if (!dialog.isShowing) return
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!active(activity)) return@setOnClickListener
            val packet = CharacterContext.compose(slots, editor.text.toString())
            if (packet == null) editor.error = activity.getString(R.string.roleplay_context_invalid)
            else {
                dialog.dismiss()
                previewContext(activity, packet)
            }
        }
    }

    private fun previewContext(activity: Activity, packet: String) {
        val editor = privateEditor(activity, CharacterContext.MAX_PACKET)
        editor.setText(packet)
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.roleplay_context)
            .setMessage(R.string.roleplay_context_review).setView(countedEditor(activity, editor, CharacterContext.MAX_PACKET))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.character_draft_copy, null).create()
        showPrivate(activity, dialog)
        if (!dialog.isShowing) return
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (active(activity)) copy(activity, editor.text.toString())
        }
    }

    private fun search(activity: Activity) {
        val query = privateEditor(activity, CharacterContext.MAX_QUERY, singleLine = true)
        query.setHint(R.string.roleplay_query_hint)
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.roleplay_web_search)
            .setMessage(R.string.roleplay_search_privacy).setView(query)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.confirm, null).create()
        showPrivate(activity, dialog)
        if (!dialog.isShowing) return
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!active(activity)) return@setOnClickListener
            val raw = query.text.toString()
            if (CharacterContext.searchUrl(CharacterContext.Provider.DUCKDUCKGO, raw) == null) {
                query.error = activity.getString(R.string.roleplay_invalid)
                return@setOnClickListener
            }
            val providers = CharacterContext.Provider.entries
            showPrivate(activity, AlertDialog.Builder(activity).setTitle(R.string.roleplay_web_search)
                .setItems(arrayOf("DuckDuckGo", "Google", "Wikipedia")) { _, index ->
                    if (active(activity)) {
                        val url = providers.getOrNull(index)?.let { CharacterContext.searchUrl(it, raw) }
                        if (url != null) {
                            val opened = runCatching {
                                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .addCategory(Intent.CATEGORY_BROWSABLE))
                            }.isSuccess
                            if (opened) dialog.dismiss() else toast(activity, R.string.error_browser)
                        }
                    }
                }.setNegativeButton(R.string.cancel, null).create())
        }
    }

    private fun memoryBank(activity: Activity) {
        val context = activity.applicationContext
        ManualEditorWork.load(activity, { MemoryBankStore.load(context) }) { result ->
            result.fold({ memoryProfiles(activity, it) }, { toast(activity, R.string.roleplay_failed) })
        }
    }

    private fun memoryProfiles(activity: Activity, entries: List<MemoryBank.Entry>) {
        val profiles = MemoryBank.profiles(entries)
        val labels = profiles + activity.getString(R.string.memory_bank_new)
        showPrivate(activity, AlertDialog.Builder(activity).setTitle(R.string.memory_bank_title)
            .setItems(labels.toTypedArray()) { _, index ->
                if (active(activity)) {
                    val profile = profiles.getOrNull(index)
                    if (profile != null) profileMenu(activity, entries, profile)
                    else {
                        val input = privateEditor(activity, MemoryBank.MAX_PROFILE, true)
                        val dialog = AlertDialog.Builder(activity).setTitle(R.string.memory_bank_profile)
                            .setMessage(R.string.memory_bank_profile_help).setView(input)
                            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.confirm, null).create()
                        showPrivate(activity, dialog)
                        if (dialog.isShowing) dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            val name = input.text.toString().trim()
                            if (!MemoryBank.validProfile(name)) input.error = activity.getString(R.string.roleplay_invalid)
                            else { dialog.dismiss(); profileMenu(activity, entries, name) }
                        }
                    }
                }
            }.setNegativeButton(R.string.close, null).create())
    }

    private fun profileMenu(activity: Activity, entries: List<MemoryBank.Entry>, profile: String) {
        val items = MemoryBank.forProfile(entries, profile)
        val labels = listOf(activity.getString(R.string.memory_bank_add), activity.getString(R.string.memory_bank_recall)) +
            items.map { it.title }
        showPrivate(activity, AlertDialog.Builder(activity).setTitle(profile)
            .setItems(labels.toTypedArray()) { _, index ->
                if (active(activity)) when (index) {
                    0 -> memoryEdit(activity, entries, profile, null)
                    1 -> memoryRecall(activity, entries, profile)
                    else -> items.getOrNull(index - 2)?.let { entry ->
                        showPrivate(activity, AlertDialog.Builder(activity).setTitle(entry.title)
                            .setItems(arrayOf(activity.getString(R.string.character_draft_edit),
                                activity.getString(R.string.character_draft_copy), activity.getString(R.string.character_draft_library_delete))) { _, action ->
                                if (active(activity)) when (action) {
                                    0 -> memoryEdit(activity, entries, profile, entry)
                                    1 -> copy(activity, entry.body)
                                    2 -> showPrivate(activity, AlertDialog.Builder(activity).setMessage(R.string.memory_bank_delete)
                                        .setNegativeButton(R.string.cancel, null)
                                        .setPositiveButton(R.string.confirm) { _, _ ->
                                            if (active(activity)) {
                                                val context = activity.applicationContext
                                                ManualEditorWork.load(activity, { MemoryBankStore.remove(context, entry) }) { result ->
                                                    toast(activity, if (result.isSuccess) R.string.roleplay_saved else R.string.roleplay_failed)
                                                    if (result.isSuccess) memoryBank(activity)
                                                }
                                            }
                                        }.create())
                                }
                            }.setNegativeButton(R.string.close, null).create())
                    }
                }
            }.setNegativeButton(R.string.close, null).create())
    }

    private fun memoryEdit(activity: Activity, entries: List<MemoryBank.Entry>, profile: String, existing: MemoryBank.Entry?) {
        val title = privateEditor(activity, MemoryBank.MAX_TITLE, true).apply {
            setHint(R.string.memory_bank_entry_title)
            setText(existing?.title.orEmpty())
            isEnabled = existing == null
        }
        val body = privateEditor(activity, MemoryBank.MAX_BODY).apply {
            setHint(R.string.memory_bank_entry_body)
            setText(existing?.body.orEmpty())
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPaddingRelative(pad, 0, pad, 0)
            addView(title)
            addView(characterCounter(activity, title, MemoryBank.MAX_TITLE))
            addView(body)
            addView(characterCounter(activity, body, MemoryBank.MAX_BODY))
        }
        val dialog = AlertDialog.Builder(activity).setTitle(profile)
            .setMessage(R.string.memory_bank_edit_help).setView(content)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.roleplay_save, null).create()
        showPrivate(activity, dialog)
        if (!dialog.isShowing) return
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!active(activity)) return@setOnClickListener
            val entry = MemoryBank.Entry(profile, title.text.toString(), body.text.toString())
            val next = MemoryBank.put(entries, entry)
            if (next == null || (existing == null && next.size <= entries.size)) {
                body.error = activity.getString(R.string.memory_bank_invalid)
            } else {
                val context = activity.applicationContext
                ManualEditorWork.run(activity, dialog, listOf(title, body),
                    { MemoryBankStore.put(context, entries, entry) }) { result ->
                    if (result.isSuccess) {
                        toast(activity, R.string.roleplay_saved)
                        dialog.dismiss()
                        memoryBank(activity)
                    } else body.error = activity.getString(R.string.roleplay_failed)
                }
            }
        }
    }

    private fun memoryRecall(activity: Activity, entries: List<MemoryBank.Entry>, profile: String) {
        val input = privateEditor(activity, 512, true)
        input.setHint(R.string.roleplay_query_hint)
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.memory_bank_recall)
            .setMessage(R.string.memory_bank_recall_help).setView(input)
            .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.confirm, null).create()
        showPrivate(activity, dialog)
        if (!dialog.isShowing) return
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!active(activity)) return@setOnClickListener
            val query = input.text.toString()
            ManualEditorWork.run(activity, dialog, listOf(input), { MemoryBank.recall(entries, profile, query) }) { response ->
                val result = response.getOrNull()
                if (response.isFailure) input.error = activity.getString(R.string.roleplay_failed)
                else if (result == null || result.entries.isEmpty()) input.error = activity.getString(R.string.memory_bank_no_match)
                else {
                    dialog.dismiss()
                    chooseRecall(activity, profile, result)
                }
            }
        }
    }

    private fun chooseRecall(activity: Activity, profile: String, result: MemoryBank.Recall) {
        val entries = result.entries
        val selected = BooleanArray(entries.size) { true }
        val labels = entries.map { it.title + " (" + it.body.length + ")" }
        val dialog = AlertDialog.Builder(activity).setTitle(
            activity.getString(R.string.recall_context_choose, profile, result.omitted))
            .setMultiChoiceItems(labels.toTypedArray(), selected) { _, index, checked -> selected[index] = checked }
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm, null).create()
        showPrivate(activity, dialog)
        if (!dialog.isShowing) return
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (!active(activity)) return@setOnClickListener
            val text = RecallContext.select(profile, entries, selected.indices.filter { selected[it] }.toSet())
            if (text == null) toast(activity, R.string.recall_context_empty)
            else {
                dialog.dismiss()
                context(activity, text)
            }
        }
    }

    private fun dice(activity: Activity) {
        val sides = RoleplayToolkit.diceSides
        showPrivate(activity, AlertDialog.Builder(activity).setTitle(R.string.roleplay_dice)
            .setItems(sides.map { "d" + it }.toTypedArray()) { _, index ->
                if (!active(activity)) return@setItems
                val die = sides[index]
                showPrivate(activity, AlertDialog.Builder(activity).setTitle(R.string.roleplay_dice_count)
                    .setItems((1..10).map { it.toString() }.toTypedArray()) { _, number ->
                        if (active(activity)) {
                            val result = RoleplayToolkit.roll(die, number + 1)
                            val text = activity.getString(R.string.roleplay_dice_result,
                                number + 1, die, result.joinToString(", "), result.sum())
                            showPrivate(activity, AlertDialog.Builder(activity).setTitle(R.string.roleplay_dice)
                                .setMessage(text).setPositiveButton(R.string.character_draft_copy) { _, _ ->
                                    copy(activity, text)
                                }.setNegativeButton(R.string.close, null).create())
                        }
                    }.setNegativeButton(R.string.cancel, null).create())
            }.setNegativeButton(R.string.cancel, null).create())
    }
}
