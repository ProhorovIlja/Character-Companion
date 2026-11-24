import dev.caicompanion.app.RecallContext as R
import dev.caicompanion.app.MemoryBank as M
import dev.caicompanion.app.CharacterContext as C
import dev.caicompanion.app.RoleplayToolkit.Slot as S

fun verifyRecallContext() {
    val entries = listOf(M.Entry("Mira", "Paris", "Visit 🌙"), M.Entry("mira", "Museum", "Paintings"))
    check(R.select("MIRA", entries, setOf(0)) == "[Paris]\nVisit 🌙")
    check(R.select("Mira", entries, setOf(1)) == "[Museum]\nPaintings")
    val all = R.select("Mira", entries, setOf(1, 0))!!
    check(all == "[Paris]\nVisit 🌙\n\n[Museum]\nPaintings")
    check(R.select("Mira", entries, emptySet()) == null)
    check(R.select("Mira", entries, setOf(2)) == null)
    check(R.select("Mira", entries, setOf(-1)) == null)
    check(R.select("Other", entries, setOf(0)) == null)
    check(R.select("Mira", entries + M.Entry("Other", "Secret", "private"), setOf(0)) == null)
    check(R.select("Mira", listOf(entries[0].copy(body = "\uD800")), setOf(0)) == null)
    val big = (1..4).map { M.Entry("Mira", "Topic $it", "x".repeat(2000)) }
    check(R.select("Mira", big, setOf(0, 1, 2)) == null)
    check(R.select("Mira", big, setOf(0, 1)) != null)
    val merged = R.combine(all, mapOf(S.PERSONA to "Mira", S.SCENE to "Paris", S.PROMPT to "Continue"))!!
    check(merged[S.MEMORY] == all && merged.size == 4)
    val packet = C.compose(merged, "Source: museum website")!!
    check(packet.contains("[Relevant memories]\n$all"))
    check(packet.contains("[Persona]\nMira") && packet.contains("[Current scene]\nParis"))
    check(packet.contains("Source: museum website"))
    check(R.combine(all, mapOf(S.MEMORY to "Other character private memory")) == null)
    check(R.combine("", emptyMap()) == null)
    check(R.combine("x".repeat(6001), emptyMap()) == null)
    check(R.combine(all, mapOf(S.PERSONA to "x".repeat(16000))) == null)
    check(R.combine(all, emptyMap()) == mapOf(S.MEMORY to all))
    println("RecallContext: 20 checks passed")
}
