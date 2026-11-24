import dev.caicompanion.app.PngLorebookCard as P
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.zip.CRC32

fun verifyPngLorebook() {
    val signature = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    fun chunk(name: String, body: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        DataOutputStream(out).apply {
            val type = name.toByteArray(Charsets.US_ASCII)
            writeInt(body.size); write(type); write(body)
            writeInt(CRC32().apply { update(type); update(body) }.value.toInt())
        }
    }.toByteArray()
    val header = chunk("IHDR", byteArrayOf(0,0,0,1,0,0,0,1,8,6,0,0,0))
    val image = chunk("IDAT", byteArrayOf(1,2,3)) // metadata-only fixture, not a decoded image
    val end = chunk("IEND", byteArrayOf())
    fun text(key: String, value: ByteArray) = chunk("tEXt", key.toByteArray() + byteArrayOf(0) + value)
    fun card(key: String, value: String) = text(key, Base64.getEncoder().encode(value.toByteArray(Charsets.UTF_8)))
    fun png(vararg chunks: ByteArray) = signature + header + chunks.fold(byteArrayOf()) { a,b -> a+b } + image + end
    fun extract(bytes: ByteArray) = P.extract(ByteArrayInputStream(bytes))
    val v2 = "{\"data\":{\"character_book\":{\"name\":\"Город 😀\"}}}"
    val v3 = "{\"spec\":\"chara_card_v3\",\"data\":{}}"
    check(extract(png(card("chara", v2))) == v2)
    check(extract(png(card("ccv3", v3))) == v3)
    check(extract(png(card("chara", v2), card("ccv3", v3))) == v3)
    check(extract(png(card("ccv3", v3), card("chara", v2))) == v3)
    check(extract(png(card("chara", v2), card("chara", v2))) == null)
    check(extract(png(card("ccv3", v3), card("ccv3", v3))) == null)
    check(extract(png()) == null)
    check(extract(png(text("other", byteArrayOf(1,2,3)), card("chara", v2))) == v2)
    check(extract(png(card("chara", v2), text("ccv3", "bad!".toByteArray()))) == null)
    check(extract(png(card("chara", "not JSON"))) == null)
    check(extract(png(text("chara", Base64.getEncoder().encode(byteArrayOf(-1))))) == null)
    val valid = png(card("chara", v2))
    check(extract(valid.copyOf(valid.size-1)) == null)
    check(extract(valid + byteArrayOf(0)) == null)
    check(extract(valid.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }) == null)
    check(extract(byteArrayOf(1,2,3)) == null)
    check(extract(signature + chunk("IDAT", byteArrayOf()) + end) == null)
    check(extract(signature + header + card("chara", v2) + end) == null)
    check(extract(signature + header + byteArrayOf(127,-1,-1,-1)) == null)
    check(P.extract(ByteArrayInputStream(valid)) { true } == null)
    val input = ByteArrayInputStream(valid).buffered()
    check(P.isPng(input) && P.extract(input) == v2)
    val plain = ByteArrayInputStream("abc".toByteArray()).buffered()
    check(!P.isPng(plain) && plain.read() == 'a'.code)
    check(extract(png(card("chara", "{\"x\":\"" + "界".repeat(2_000_000) + "\"}"))) == null)
    check(extract(png(card("chara", "{\"x\":\"" + "a".repeat(100_000) + "\"}")))?.length == 100008)
    println("PngLorebookCard: 23 checks passed")
}
