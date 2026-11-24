package dev.caicompanion.app

/** Explicitly selected memories from one named profile, never the global memory slot. */
object RecallContext {
    fun select(profile: String, entries: List<MemoryBank.Entry>, selected: Set<Int>): String? {
        if (entries.isEmpty() || entries.size > MemoryBank.MAX_ENTRIES || selected.isEmpty() ||
            selected.any { it !in entries.indices } || entries.any { !MemoryBank.validEntry(it) } ||
            MemoryBank.forProfile(entries, profile).size != entries.size) return null
        val text = entries.filterIndexed { index, _ -> index in selected }
            .joinToString("\n\n") { "[${it.title}]\n${it.body}" }
        return text.takeIf { it.length <= MemoryBank.MAX_RECALL && RoleplayToolkit.valid(it) }
    }

    fun combine(recalled: String, slots: Map<RoleplayToolkit.Slot, String>): Map<RoleplayToolkit.Slot, String>? {
        if (recalled.isBlank() || recalled.length > MemoryBank.MAX_RECALL || !RoleplayToolkit.valid(recalled) ||
            RoleplayToolkit.Slot.MEMORY in slots) return null
        val combined = slots + (RoleplayToolkit.Slot.MEMORY to recalled)
        return combined.takeIf { CharacterContext.compose(it) != null }
    }
}
