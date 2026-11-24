package dev.caicompanion.app

import android.app.Activity
import android.content.Intent
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast

/** One explicit return to a focused editor, using the renderer's standard IME interface.
 * No JavaScript, cookies, global clipboard access or send action. If focus is unavailable,
 * reopen the private draft instead of attempting another field. */
class VoiceComposerHandoff {
    companion object { const val REQUEST = 940 }
    private var ticket: VoiceComposerPolicy.Ticket? = null
    private var awaiting = false
    private var pending: String? = null
    // If the fallback Activity cannot be opened, keep the draft in process memory.
    // A later explicit Voice tap recovers it; focus changes never retry insertion.
    private val recovery = VoiceComposerPolicy.RecoveryDraft()

    fun launch(activity: Activity, page: String?) {
        if (awaiting || pending != null || activity.isFinishing || activity.isDestroyed) return
        if (recovery.hasDraft) {
            reopenRecovery(activity)
            return
        }
        val next = VoiceComposerPolicy.Ticket(page)
        ticket = next
        awaiting = true
        try {
            activity.startActivityForResult(VoiceDraftActivity.handoffIntent(activity, next.eligible), REQUEST)
        } catch (_: RuntimeException) {
            awaiting = false
            ticket = null
            Toast.makeText(activity, R.string.voice_editor_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    fun observe(page: String?) { ticket?.observe(page) }
    fun invalidate() { ticket?.invalidate() }

    fun receive(resultCode: Int, data: Intent?) {
        val wasAwaiting = awaiting
        awaiting = false
        // A recreated parent can receive the result, but cannot inherit an insertion ticket.
        if (!wasAwaiting) ticket = null
        if (pending != null || recovery.hasDraft) return
        pending = if (resultCode == Activity.RESULT_OK)
            VoiceComposerPolicy.validDraft(data?.getStringExtra(VoiceDraftActivity.EXTRA_DRAFT)) else null
        if (pending == null) ticket = null
    }

    fun deliver(activity: Activity, renderer: View?, page: String?) {
        if (!activity.hasWindowFocus() || activity.isFinishing || activity.isDestroyed) return
        val draft = pending ?: return
        pending = null
        val accepted = ticket?.consume(page) == true
        ticket = null
        if (accepted && renderer != null && renderer.isShown && renderer.hasFocus() &&
            tryInsert(renderer, draft)) {
            // commitText is an acknowledgement by the input connection, not proof of delivery to the website.
            Toast.makeText(activity, R.string.voice_handoff_check, Toast.LENGTH_LONG).show()
        } else {
            // No automatic clipboard or retry into a different field. Preserve words for review.
            recovery.preserve(draft)
            reopenRecovery(activity)
        }
    }

    private fun reopenRecovery(activity: Activity) {
        val opened = recovery.open { draft ->
            try {
                activity.startActivity(VoiceDraftActivity.handoffIntent(activity, false, draft))
                true
            } catch (_: RuntimeException) { false }
        }
        Toast.makeText(activity, if (opened) R.string.voice_handoff_unavailable
            else R.string.voice_handoff_recover, Toast.LENGTH_LONG).show()
    }

    private fun tryInsert(renderer: View, draft: String): Boolean = runCatching {
        if (!renderer.onCheckIsTextEditor()) return false
        val info = EditorInfo()
        val connection = renderer.onCreateInputConnection(info) ?: return false
        val type = info.inputType
        val variation = type and InputType.TYPE_MASK_VARIATION
        val multiline = type and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        // Only ordinary multiline text. Never password, email, URL or numeric inputs.
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT || !multiline ||
            variation !in setOf(InputType.TYPE_TEXT_VARIATION_NORMAL,
                InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE, InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE)) return false
        connection.commitText(draft, 1)
    }.getOrDefault(false)

    fun clear() { ticket = null; awaiting = false; pending = null; recovery.clear() }
}
