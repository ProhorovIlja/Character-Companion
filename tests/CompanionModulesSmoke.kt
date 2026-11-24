import dev.caicompanion.app.CompanionModules as C
import dev.caicompanion.app.CompanionModules.Module as M

fun verifyCompanionModules() {
    val all = C.matching("") { "" }
    check(all.size == 13)
    check(all.toSet().size == 13)
    check(C.Group.entries.all { group -> all.any { it.group == group } })
    check(all.filter { it.group == C.Group.CREATOR }.size == 5)
    check(all.filter { it.group == C.Group.KNOWLEDGE }.size == 5)
    check(all.filter { it.group == C.Group.UTILITIES }.size == 3)
    check(C.matching("  MEMORY  BANK ") { "" } == listOf(M.MEMORY_BANK))
    check(C.matching("ｗｉｋｉｐｅｄｉａ") { "" } == listOf(M.WIKIPEDIA))
    check(C.matching("память персонажа") { if (it == M.MEMORY_BANK) "Память персонажа" else "" } == listOf(M.MEMORY_BANK))
    check(C.matching("missing xyz") { "" }.isEmpty())
    check(C.matching("   ") { "" } == all)
    check(C.matching("x".repeat(10000)) { "" }.isEmpty())
    check(C.matching("memory") { "" } == listOf(M.MEMORY_BANK, M.CHAT_MEMORY, M.MEMORY_BACKUP))
    check(C.matching("context") { "shared" } == listOf(M.CONTEXT))
    println("CompanionModules: 14 checks passed")
}
