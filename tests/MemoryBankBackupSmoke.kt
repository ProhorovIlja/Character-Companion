import dev.caicompanion.app.MemoryBank as M
import dev.caicompanion.app.MemoryBankBackup as B

fun verifyMemoryBankBackup() {
    val a = M.Entry("Mira", "Paris", "Visited Paris 🌙")
    val b = M.Entry("Mira", "Museum", "Art")
    val c = M.Entry("Other", "Paris", "Different story")
    val bank = listOf(a, b, c)
    val bytes = B.encode(bank)
    check(B.decode(bytes.inputStream()) == bank)
    check(B.decode(B.encode(emptyList()).inputStream()) == emptyList<M.Entry>())
    check(B.decode(bytes.copyOf(bytes.size - 1).inputStream()) == null)
    check(B.decode((bytes + 0.toByte()).inputStream()) == null)
    check(B.decode(bytes.copyOf().apply { this[0] = 0 }.inputStream()) == null)
    check(B.decode(bytes.copyOf().apply { this[7] = 2 }.inputStream()) == null)
    check(B.decode(bytes.copyOf().apply { this[8] = 127 }.inputStream()) == null)
    check(B.decode(bytes.copyOf().apply { this[12] = (this[12].toInt() xor 1).toByte() }.inputStream()) == null)
    check(B.decode(bytes.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }.inputStream()) == null)
    check(runCatching { B.encode(listOf(a, a)) }.isFailure)
    val merge = B.merge(listOf(a), listOf(a.copy(profile = "mira", title = "PARIS"), b, c))!!
    check(merge.entries == bank && merge.added == 2 && merge.duplicates == 1 && merge.conflicts == 0)
    val conflict = B.merge(listOf(a), listOf(a.copy(body = "Changed"), c))!!
    check(conflict.entries == listOf(a, c) && conflict.conflicts == 1 && conflict.added == 1)
    check(B.merge(bank, bank)!!.duplicates == 3)
    check(B.merge(bank, emptyList())!!.entries == bank)
    check(B.merge(emptyList(), bank)!!.entries == bank)
    check(B.merge(listOf(a, a), bank) == null)
    check(B.merge(bank, listOf(a.copy(body = ""))) == null)
    val full = (1..M.MAX_ENTRIES).map { M.Entry("Mira", "Topic $it", "漢".repeat(M.MAX_BODY)) }
    check(B.merge(full, listOf(a)) == null)
    check(B.merge(full, listOf(full.first()))!!.entries == full)
    check(B.decode(B.encode(full).inputStream()) == full)
    check(B.encode(full).size <= B.MAX_PAYLOAD + 44)
    println("MemoryBankBackup: 21 checks passed")
}
