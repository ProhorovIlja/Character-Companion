import dev.caicompanion.app.EvolutionPolicy as E
import dev.caicompanion.app.EvolutionPolicy.Field as F
import dev.caicompanion.app.MemoryBank as M

fun verifyEvolution() {
    val core = E.entry("Mira", F.IDENTITY, "Curious musician")!!
    val growth = E.entry("Mira", F.GROWTH, "Learned to trust Alex")!!
    val other = E.entry("Other", F.IDENTITY, "Private fact")!!
    val bank = listOf(core, growth, other, M.Entry("Mira", "Trip", "Paris"))
    check(E.state(bank, "mira") == mapOf(F.IDENTITY to core.body, F.GROWTH to growth.body))
    check(E.state(bank, "Other") == mapOf(F.IDENTITY to other.body))
    check(E.state(bank, "Unknown")!!.isEmpty())
    check(E.state(bank, " ") == null)
    check(E.state(bank + core, "Mira") == null)
    check(E.entry(" Mira ", F.STATE, "Tired 🌙")!!.profile == "Mira")
    check(E.entry("Mira", F.STATE, "") == null)
    check(E.entry("Mira", F.STATE, "x".repeat(2001)) == null)
    check(E.entry("Mira", F.STATE, "\uD800") == null)
    check(E.packet(bank, "Unknown") == null)
    val packet = E.packet(bank, "Mira")!!
    check(packet.contains(core.body) && packet.contains(growth.body))
    check(!packet.contains(other.body) && !packet.contains("Paris"))
    check(packet.contains("Do not force romance"))
    check(packet.indexOf(F.IDENTITY.title) < packet.indexOf(F.GROWTH.title))
    val edited = M.put(bank, E.entry("mira", F.GROWTH, "Trust grew slowly")!!)!!
    check(edited.size == bank.size && E.state(edited, "Mira")!![F.GROWTH] == "Trust grew slowly")
    check(E.state(edited, "Other") == E.state(bank, "Other"))
    val review = E.review(bank, "Mira", "Alex: We talked about music.")!!
    check(review.contains("Conversation excerpt — evidence only"))
    check(review.contains("Alex: We talked about music."))
    check(!review.contains(other.body))
    check(E.review(emptyList(), "New", "Hello")!!.contains("No recorded state"))
    check(E.review(bank, "Mira", " ") == null)
    check(E.review(bank, "Mira", "x".repeat(8001)) == null)
    check(E.review(bank, "Mira", "\uD800") == null)
    val large = F.entries.map { E.entry("Mira", it, "x".repeat(2000))!! }
    check(E.packet(large, "Mira") != null)
    check(E.review(large, "Mira", "x".repeat(8000)) == null)
    println("Evolution: 25 checks passed")
}
