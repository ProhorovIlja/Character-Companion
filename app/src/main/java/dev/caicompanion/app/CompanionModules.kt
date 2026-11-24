package dev.caicompanion.app

import java.text.Normalizer
import java.util.Locale

/** Built-in module catalog. Stable typed routes, not downloadable executable plugins. */
object CompanionModules {
    enum class Group { CREATOR, KNOWLEDGE, UTILITIES }
    enum class Module(val group: Group) {
        CHARACTER(Group.CREATOR), LOREBOOK(Group.CREATOR), PERSONA(Group.CREATOR),
        SCENE(Group.CREATOR), PROMPT(Group.CREATOR),
        MEMORY_BANK(Group.KNOWLEDGE), CHAT_MEMORY(Group.KNOWLEDGE), CONTEXT(Group.KNOWLEDGE),
        WIKIPEDIA(Group.KNOWLEDGE), WEB_SEARCH(Group.KNOWLEDGE), EVOLUTION(Group.KNOWLEDGE),
        MEMORY_BACKUP(Group.UTILITIES), DICE(Group.UTILITIES), FREE_HELP(Group.UTILITIES),
        VOICE(Group.UTILITIES), UPDATES(Group.UTILITIES)
    }

    private fun fold(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()

    fun matching(query: String, labels: (Module) -> String): List<Module> {
        val terms = fold(query.take(128)).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return Module.entries.toList()
        return Module.entries.filter { module ->
            val text = fold(module.name.replace('_', ' ') + " " + labels(module))
            terms.all { text.contains(it) }
        }
    }
    /** Ignore unknown/stale preference IDs; enum names, never ordinals, are stored. */
    fun decodePins(ids: Set<String>): Set<Module> = Module.entries.filter { it.name in ids }.toSet()
    fun encodePins(pins: Set<Module>): Set<String> = pins.map { it.name }.toSet()
    fun toggle(pins: Set<Module>, module: Module): Set<Module> =
        if (module in pins) pins - module else pins + module

    data class Sections(val pinned: List<Module>, val groups: Map<Group, List<Module>>)
    fun sections(query: String, pins: Set<Module>, labels: (Module) -> String): Sections {
        val matches = matching(query, labels)
        val pinned = matches.filter { it in pins }
        val groups = matches.filterNot { it in pins }.groupBy { it.group }
        return Sections(pinned, groups)
    }
}
