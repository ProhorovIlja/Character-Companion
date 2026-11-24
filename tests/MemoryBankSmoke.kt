import dev.caicompanion.app.MemoryBank as M
import java.io.ByteArrayOutputStream

fun verifyMemoryBank() {
    val a = M.Entry("Mira", "Paris", "Visited Paris with Alex 🌙")
    val b = M.Entry("Mira", "Museum", "Alex likes Paris museums")
    val c = M.Entry("Other", "Paris", "Unrelated private fact")
    val bank = listOf(a, b, c)
    check(M.profiles(bank) == listOf("Mira", "Other"))
    check(M.forProfile(bank, " MIRA ") == listOf(a, b))
    check(M.forProfile(bank, "missing").isEmpty())
    check(M.put(bank, a.copy(profile = "mira", title = "PARIS", body = "Updated"))!!.size == 3)
    check(M.put(bank, a.copy(body = "Updated"))!!.last().body == "Updated")
    check(M.put(bank, a.copy(body = "")) == null)
    check(M.put(bank, a.copy(profile = "bad\nname")) == null)
    check(M.put(bank, a.copy(body = "\uD800")) == null)
    check(M.put(bank, a.copy(title = "x".repeat(101))) == null)
    check(M.put(bank, a.copy(body = "x".repeat(2001))) == null)
    check(M.remove(bank, a) == listOf(b, c))
    check(M.remove(bank, a.copy(body = "old")) == null)
    val recall = M.recall(bank, "mira", "PARIS")!!
    check(recall.entries == listOf(a, b))
    check(!recall.text.contains("Unrelated"))
    check(M.recall(bank, "Other", "Paris")!!.entries == listOf(c))
    check(M.recall(bank, "Mira", "unknown")!!.entries.isEmpty())
    check(M.recall(bank, "Mira", "!") == null)
    check(M.recall(bank, "Mira", "x".repeat(513)) == null)
    val big = (1..5).map { M.Entry("Mira", "Paris $it", "x".repeat(2000)) }
    val limited = M.recall(big, "Mira", "Paris")!!
    check(limited.entries.size == 2 && limited.omitted == 3 && limited.text.length <= M.MAX_RECALL)
    val full = (1..M.MAX_ENTRIES).map { M.Entry("Mira", "Topic $it", "Fact") }
    check(M.put(full, a) == null)
    check(M.put(full, full.first().copy(body = "Updated"))!!.size == M.MAX_ENTRIES)
    fun encode(entries: List<M.Entry>) = ByteArrayOutputStream().also { M.write(entries, it) }.toByteArray()
    val bytes = encode(bank)
    check(M.read(bytes.inputStream()) == bank)
    check(M.read(encode(emptyList()).inputStream()).isEmpty())
    check(runCatching { M.read(bytes.copyOf(bytes.size - 1).inputStream()) }.isFailure)
    check(runCatching { M.read((bytes + 0.toByte()).inputStream()) }.isFailure)
    check(runCatching { encode(listOf(a, a.copy(title = "PARIS"))) }.isFailure)
    check(runCatching { M.read(bytes.copyOf().apply { this[7] = 2 }.inputStream()) }.isFailure)
    check(runCatching { M.read(bytes.copyOf().apply { this[12] = 127 }.inputStream()) }.isFailure)
    check(M.recall(listOf(M.Entry("Луна", "Москва", "Встреча")), "луна", "МОСКВА")!!.entries.size == 1)
    println("MemoryBank: 29 checks passed")
}
