import dev.caicompanion.app.CharxCard as C
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun verifyCharx() {
    fun archive(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
    }.toByteArray()
    val card = """{"spec":"chara_card_v3","data":{"name":"Мира 🌙"}}"""
    val bytes = card.toByteArray()
    val zip = archive("assets/a.png" to byteArrayOf(1, 2), "card.json" to bytes)
    check(C.isZip(zip.inputStream().buffered()))
    check(!C.isZip(bytes.inputStream().buffered()))
    val stream = zip.inputStream().buffered()
    check(C.isZip(stream) && C.extract(stream) == card)
    check(C.extract(archive("card.json" to bytes, "../ignored" to byteArrayOf(0)).inputStream()) == card)
    check(C.extract(archive("folder/card.json" to bytes).inputStream()) == null)
    check(C.extract(archive("card.json" to byteArrayOf(0xc3.toByte(), 0x28)).inputStream()) == null)
    check(C.extract(archive("card.json" to " ".toByteArray()).inputStream()) == null)
    check(C.extract(archive("card.json" to ("\uFEFF" + card).toByteArray()).inputStream()) == card)
    check(C.extract(archive("card.json" to ByteArray(2_000_001) { 65 }).inputStream()) == null)
    check(C.extract(zip.inputStream()) { true } == null)
    check(C.extract(zip.copyOf(25).inputStream()) == null)
    val many = Array(C.MAX_ENTRIES) { "asset-$it" to byteArrayOf() }
    check(C.extract(archive("card.json" to bytes, *many).inputStream()) == null)
    val duplicate = archive("card.json" to bytes, "same.json" to bytes).clone()
    val old = "same.json".toByteArray(); val replacement = "card.json".toByteArray()
    for (i in 0..duplicate.size - old.size) {
        if (old.indices.all { duplicate[i + it] == old[it] }) replacement.copyInto(duplicate, i)
    }
    check(C.extract(duplicate.inputStream()) == null)
    println("CharxCard: 13 checks passed")
}
