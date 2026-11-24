import dev.caicompanion.app.CharacterDraftBackup as B
import dev.caicompanion.app.CharacterDraftPolicy as P

fun verifyCharacterBackup() {
    val draft = P.Draft("Mira", "Ночь 🌙", "Description", "Hi {{user}}", "line\n{{char}}", listOf("Hello", ""), listOf("Drama"), "Notes", true)
    val bytes = B.encode(draft)
    val stream = bytes.inputStream().buffered()
    check(B.isBackup(stream) && B.decode(stream) == draft)
    check(!B.isBackup(byteArrayOf(1, 2).inputStream().buffered()))
    check(B.decode(B.encode(P.Draft()).inputStream()) == P.Draft())
    check(B.decode(bytes.copyOf(bytes.size - 1).inputStream()) == null)
    check(B.decode((bytes + byteArrayOf(0)).inputStream()) == null)
    for (index in listOf(0, 7, 8, 12, bytes.lastIndex)) {
        val corrupted = bytes.clone().apply { this[index] = (this[index].toInt() xor 1).toByte() }
        check(B.decode(corrupted.inputStream()) == null)
    }
    check(B.decode(bytes.clone().apply { for (i in 8..11) this[i] = -1 }.inputStream()) == null)
    check(runCatching { B.encode(draft.copy(name = "x".repeat(513))) }.isFailure)
    println("CharacterDraftBackup: 12 checks passed")
}
