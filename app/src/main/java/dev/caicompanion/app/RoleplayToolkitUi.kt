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
            activity.getString(R.string.roleplay_dice))
        showPrivate(activity, AlertDialog.Builder(activity).setTitle(R.string.roleplay_title)
            .setItems(labels.toTypedArray()) { _, index ->
                if (active(activity)) {
                    val slot = slots.getOrNull(index)
                    if (slot != null) edit(activity, slot)
                    else when (index - slots.size) {
                        0 -> context(activity)
                        1 -> search(activity)
                        2 -> dice(activity)
                    }
                }
            }.setNegativeButton(R.string.close, null).create())
    }

    private fun edit(activity: Activity, slot: RoleplayToolkit.Slot) {
        val saved = runCatching { RoleplayToolkitStore.load(activity, slot) }.getOrElse {
            // Do not silently replace unreadable data.
            showPrivate(activity, AlertDialog.Builder(activity).setTitle(title(slot))
                .setMessage(R.string.roleplay_damaged)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.roleplay_reset) { _, _ ->
                    if (active(activity)) {
                        if (runCatching { RoleplayToolkitStore.clear(activity, slot) }.isSuccess)
                            edit(activity, slot)
                        else toast(activity, R.string.roleplay_failed)
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
            else if (runCatching { RoleplayToolkitStore.save(activity, slot, text) }.isSuccess) {
                toast(activity, R.string.roleplay_saved)
                dialog.dismiss()
            } else editor.error = activity.getString(R.string.roleplay_failed)
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

    private fun context(activity: Activity) {
        val slots = RoleplayToolkit.Slot.entries
        val selected = BooleanArray(slots.size)
        showPrivate(activity, AlertDialog.Builder(activity).setTitle(R.string.roleplay_context)
            .setMultiChoiceItems(slots.map { activity.getString(title(it)) }.toTypedArray(), selected) { _, index, checked ->
                selected[index] = checked
            }.setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.confirm) { _, _ ->
                if (active(activity)) {
                    val data = runCatching {
                        slots.filterIndexed { index, _ -> selected[index] }
                            .associateWith { RoleplayToolkitStore.load(activity, it) }
                    }.getOrElse {
                        toast(activity, R.string.roleplay_failed)
                        return@setPositiveButton
                    }
                    research(activity, data)
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

    private fun research(activity: Activity, slots: Map<RoleplayToolkit.Slot, String>) {
        val editor = privateEditor(activity, CharacterContext.MAX_PACKET)
        editor.setHint(R.string.roleplay_research_hint)
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.roleplay_context)
            .setMessage(R.string.roleplay_research_help).setView(editor)
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
            .setMessage(R.string.roleplay_context_review).setView(editor)
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
