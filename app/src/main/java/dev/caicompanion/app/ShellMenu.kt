package dev.caicompanion.app

import android.content.Context
import android.view.View
import android.widget.PopupMenu

/** Native overflow with a short root and explicit categories; callbacks run only on selection. */
object ShellMenu {
    enum class Section(val title: Int) {
        CREATOR(R.string.shell_menu_creator),
        PAGE(R.string.shell_menu_page),
        PRIVACY(R.string.shell_menu_privacy),
        SUPPORT(R.string.shell_menu_support)
    }

    data class Action(val title: Int, val enabled: Boolean = true,
                      val section: Section? = null, val run: () -> Unit)

    fun show(context: Context, anchor: View, actions: List<Action>) {
        val popup = PopupMenu(context, anchor)
        val indexed = actions.withIndex().toList()
        var rootOrder = 0
        indexed.filter { it.value.section == null }.forEach { (index, action) ->
            popup.menu.add(0, index + 1, rootOrder++, action.title).isEnabled = action.enabled
        }
        for (section in Section.entries) {
            val members = indexed.filter { it.value.section == section }
            if (members.isEmpty()) continue
            // Parent IDs cannot overlap with leaf IDs, including disabled entries.
            val submenu = popup.menu.addSubMenu(0, actions.size + section.ordinal + 1,
                rootOrder++, section.title)
            members.forEachIndexed { order, (index, action) ->
                submenu.add(0, index + 1, order, action.title).isEnabled = action.enabled
            }
        }
        popup.setOnMenuItemClickListener { item ->
            val action = actions.getOrNull(item.itemId - 1)
            if (action == null || !action.enabled) false else { action.run(); true }
        }
        popup.show()
    }
}
