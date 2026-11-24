package dev.caicompanion.app

import android.content.Context
import android.view.View
import android.widget.PopupMenu

/** Contextual commands stay off the chat viewport. Callbacks run only on user selection. */
object ShellMenu {
    data class Action(val title: Int, val enabled: Boolean = true, val run: () -> Unit)

    fun show(context: Context, anchor: View, actions: List<Action>) {
        val popup = PopupMenu(context, anchor)
        actions.forEachIndexed { index, action ->
            popup.menu.add(0, index, index, action.title).isEnabled = action.enabled
        }
        popup.setOnMenuItemClickListener { item ->
            val action = actions.getOrNull(item.itemId)
            if (action == null || !action.enabled) false else { action.run(); true }
        }
        popup.show()
    }
}
