package dev.caicompanion.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
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
        val labels = slots.map { activity.getString(title(it)) } + activity.getString(R.string.roleplay_dice)
        showPrivate(activity, AlertDialog.Builder(activity).setTitle(R.string.roleplay_title)
            .setItems(labels.toTypedArray()) { _, index ->
                if (active(activity)) {
                    val slot = slots.getOrNull(index)
                    if (slot != null) edit(activity, slot) else dice(activity)
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
