import dev.caicompanion.app.CompanionModules as C
import dev.caicompanion.app.CompanionModules.Module as M

fun verifyCompanionModules() {
    val all = C.matching("") { "" }
    check(all.size == 15)
    check(all.toSet().size == 15)
    check(C.Group.entries.all { group -> all.any { it.group == group } })
    check(all.filter { it.group == C.Group.CREATOR }.size == 5)
    check(all.filter { it.group == C.Group.KNOWLEDGE }.size == 5)
    check(all.filter { it.group == C.Group.UTILITIES }.size == 5)
    check(C.matching("  MEMORY  BANK ") { "" } == listOf(M.MEMORY_BANK))
    check(C.matching("ｗｉｋｉｐｅｄｉａ") { "" } == listOf(M.WIKIPEDIA))
    check(C.matching("память персонажа") { if (it == M.MEMORY_BANK) "Память персонажа" else "" } == listOf(M.MEMORY_BANK))
    check(C.matching("missing xyz") { "" }.isEmpty())
    check(C.matching("   ") { "" } == all)
    check(C.matching("x".repeat(10000)) { "" }.isEmpty())
    check(C.matching("memory") { "" } == listOf(M.MEMORY_BANK, M.CHAT_MEMORY, M.MEMORY_BACKUP))
    check(C.matching("context") { "shared" } == listOf(M.CONTEXT))
    val pins = setOf(M.MEMORY_BANK, M.VOICE, M.UPDATES)
    check(C.decodePins(setOf("VOICE", "MEMORY_BANK", "REMOVED", "UPDATES")) == pins)
    check(C.decodePins(setOf("voice", "0", "UNKNOWN")).isEmpty())
    check(C.decodePins(C.encodePins(pins)) == pins)
    check(C.toggle(pins, M.VOICE) == pins - M.VOICE)
    check(C.toggle(pins, M.PERSONA) == pins + M.PERSONA)
    check(pins.size == 3)
    val sections = C.sections("", pins) { "" }
    check(sections.pinned == listOf(M.MEMORY_BANK, M.VOICE, M.UPDATES))
    check(sections.groups.values.flatten().none { it in pins })
    check((sections.pinned + sections.groups.values.flatten()).toSet() == all.toSet())
    check(sections.pinned.size + sections.groups.values.sumOf { it.size } == all.size)
    check(C.sections("voice", pins) { "" }.pinned == listOf(M.VOICE))
    check(C.sections("voice", pins) { "" }.groups.isEmpty())
    check(C.sections("memory", pins) { "" }.groups.values.flatten() == listOf(M.CHAT_MEMORY, M.MEMORY_BACKUP))
    check(C.sections("missing xyz", pins) { "" }.pinned.isEmpty())
    check(C.sections("", all.toSet()) { "" }.groups.isEmpty())
    check(C.sections("", emptySet()) { "" }.pinned.isEmpty())
    println("CompanionModules: 30 checks passed")
}
