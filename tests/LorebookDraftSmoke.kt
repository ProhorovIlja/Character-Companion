import dev.caicompanion.app.LorebookPolicy as P
import dev.caicompanion.app.LorebookDraftCodec as C
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

fun verifyLorebookDraft() {
    fun encode(book: P.Book): ByteArray = ByteArrayOutputStream().also { C.write(book, it) }.toByteArray()
    fun read(bytes: ByteArray) = C.read(ByteArrayInputStream(bytes))
    fun rejected(bytes: ByteArray) = runCatching { read(bytes) }.isFailure
    val book = P.Book("Город 😀", "two\nlines", listOf(
        P.Entry("Gate", listOf("a,b", "", " mixed ", "ключ"), "first\nlast\n"),
        P.Entry("", emptyList(), "{{user}} / {{char}}")), true, 4)
    val bytes = encode(book)
    check(read(bytes) == book)
    check(C.header(ByteArrayInputStream(bytes)) == C.Header(book.name, 2))
    check(encode(read(bytes)).contentEquals(bytes))
    check(rejected(bytes + byteArrayOf(0)))
    check(rejected(bytes.copyOf(bytes.size - 1)))
    check(rejected(byteArrayOf()))
    check(rejected(bytes.copyOf().also { it[0] = 0 }))
    check(rejected(bytes.copyOf().also { it[7] = 2 }))
    check(rejected(bytes.copyOf().also { it[8] = -1 }))
    check(rejected(bytes.copyOf().also { it[12] = -1 }))
    check(runCatching { encode(book.copy(skippedDisabled = -1)) }.isFailure)
    check(runCatching { encode(book.copy(skippedDisabled = 1001)) }.isFailure)
    check(runCatching { encode(book.copy(name = "\uD800")) }.isFailure)
    check(runCatching { encode(book.copy(entries = emptyList())) }.isFailure)
    check(runCatching { encode(book.copy(name = "x".repeat(513))) }.isFailure)
    val max = P.Book("", "", List(20) { P.Entry("", emptyList(), "界".repeat(100000)) })
    val large = encode(max)
    check(large.size < C.MAX_FILE_BYTES)
    check(read(large) == max)
    check(runCatching { encode(max.copy(name = "x")) }.isFailure)
    val corruptCount = ByteArrayOutputStream().also { out -> DataOutputStream(out).apply {
        writeInt(0x43434C42); writeInt(1); writeInt(0); writeInt(1001)
    }}.toByteArray()
    check(rejected(corruptCount))
    println("LorebookDraftCodec: 19 checks passed")
}
