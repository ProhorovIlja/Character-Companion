import dev.caicompanion.app.RoleplayToolkit as T
import java.io.ByteArrayOutputStream
import kotlin.random.Random

fun verifyRoleplayToolkit() {
    val slot = T.Slot.MEMORY
    fun encode(text: String): ByteArray = ByteArrayOutputStream().also { T.write(slot, text, it) }.toByteArray()
    fun decode(bytes: ByteArray) = T.read(slot, bytes.inputStream())
    val text = "Привет 🌙\nDialogue\tcontext"
    check(decode(encode(text)) == text)
    check(decode(encode("")) == "")
    check(decode(encode("x".repeat(T.MAX_CHARS))).length == T.MAX_CHARS)
    check(decode(encode("漢".repeat(T.MAX_CHARS))).length == T.MAX_CHARS)
    check(decode(encode("😀".repeat(T.MAX_CHARS / 2))).length == T.MAX_CHARS)
    check(!T.valid("x".repeat(T.MAX_CHARS + 1)))
    check(!T.valid("\uD800"))
    check(!T.valid("\uDC00"))
    check(!T.valid("bad\u0000"))
    check(T.valid("\r\n\t"))
    check(runCatching { encode("\uD800") }.isFailure)
    val bytes = encode(text)
    check(runCatching { T.read(T.Slot.SCENE, bytes.inputStream()) }.isFailure)
    check(runCatching { decode(bytes.copyOf(bytes.size - 1)) }.isFailure)
    check(runCatching { decode(bytes + 0.toByte()) }.isFailure)
    check(runCatching { decode(bytes.copyOf().apply { this[0] = 0 }) }.isFailure)
    check(runCatching { decode(bytes.copyOf().apply { this[7] = 2 }) }.isFailure)
    check(runCatching { decode(bytes.copyOf().apply { this[12] = 127 }) }.isFailure)
    check(runCatching { decode(encode("a").apply { this[lastIndex] = 0xc3.toByte() }) }.isFailure)
    check(T.Slot.entries.map { it.key }.distinct().size == 4)
    check(T.Slot.entries.map { it.wireId }.distinct().size == 4)
    for (sides in T.diceSides) {
        val rolls = T.roll(sides, 10, Random(sides))
        check(rolls.size == 10 && rolls.all { it in 1..sides })
    }
    check(runCatching { T.roll(5, 1) }.isFailure)
    check(runCatching { T.roll(20, 0) }.isFailure)
    check(runCatching { T.roll(20, 11) }.isFailure)
    check(T.roll(20, 5, Random(3)) == T.roll(20, 5, Random(3)))
    println("RoleplayToolkit: 31 checks passed")
}
