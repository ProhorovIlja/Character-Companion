import dev.caicompanion.app.WikipediaResearch as W
import dev.caicompanion.app.CharacterContext as C
import java.net.URI
import java.net.URLDecoder

fun verifyWikipediaResearch() {
    val query = "Stockholm & Paris 🌙 #?"
    for (lang in W.Language.entries) {
        val url = URI(W.endpoint(lang, query)!!)
        check(url.scheme == "https" && url.host == lang.host && url.path == "/w/api.php")
        check(url.fragment == null && url.userInfo == null)
        check(URLDecoder.decode(url.rawQuery.substringAfter("&gsrsearch="), "UTF-8") == query)
    }
    check(W.endpoint(W.Language.EN, " ") == null)
    check(W.endpoint(W.Language.EN, "x".repeat(513)) == null)
    check(W.endpoint(W.Language.EN, "a\nb") == null)
    check(W.endpoint(W.Language.EN, "\uD800") == null)
    val source = W.Source(123, "Paris", "An excerpt 🌙")
    val packet = W.packet(W.Language.EN, listOf(source))!!
    check(packet.contains("https://en.wikipedia.org/?curid=123"))
    check(packet.contains("An excerpt 🌙"))
    check(packet.contains("https://creativecommons.org/licenses/by-sa/4.0/"))
    check(W.packet(W.Language.RU, listOf(source))!!.contains("https://ru.wikipedia.org/?curid=123"))
    check(W.packet(W.Language.EN, emptyList()) == null)
    check(W.packet(W.Language.EN, listOf(source, source)) == null)
    check(W.packet(W.Language.EN, listOf(source.copy(id = 0))) == null)
    check(W.packet(W.Language.EN, listOf(source.copy(title = "bad\nname"))) == null)
    check(W.packet(W.Language.EN, listOf(source.copy(extract = ""))) == null)
    check(W.packet(W.Language.EN, listOf(source.copy(extract = "x".repeat(1201)))) == null)
    check(W.packet(W.Language.EN, (1L..6L).map { source.copy(id = it) }) == null)
    check(W.append("old", packet) == "old\n\n$packet")
    check(W.append("", packet) == packet)
    check(W.append("x".repeat(16000), packet) == null)
    check(W.append("old", "") == null)
    check(C.compose(emptyMap(), packet)!!.contains("External research"))
    check(W.readResponse("Привет 🌙".byteInputStream()) == "Привет 🌙")
    check(W.readResponse(ByteArray(W.MAX_RESPONSE) { 65 }.inputStream())!!.length == W.MAX_RESPONSE)
    check(W.readResponse(ByteArray(W.MAX_RESPONSE + 1).inputStream()) == null)
    check(W.readResponse(byteArrayOf(0xc3.toByte()).inputStream()) == null)
    check(W.readResponse("a".byteInputStream()) { true } == null)
    println("WikipediaResearch: 34 checks passed")
}
