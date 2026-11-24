import dev.caicompanion.app.ChatRefreshPolicy as P

fun verifyChatRefresh() {
    val base = "https://character.ai/chat/abc"
    check(P.isChat(base))
    check(P.isChat("$base/"))
    check(P.isChat("$base?chat=one"))
    check(P.isChat("$base?unknown=one#anchor"))
    check(P.isChat("https://www.character.ai:443/chat/abc"))
    check(P.isChat("https://character.ai/chat?char=abc&chat=one"))
    check(P.isChat("https://character.ai/chat/?char=abc"))
    check(!P.isChat("https://character.ai/chat?char=abc&char=def"))
    check(!P.isChat("https://character.ai/chat?char=%2Fauth"))
    check(!P.isChat("https://character.ai/chat"))
    check(!P.isChat("https://character.ai/chat/abc/other"))
    check(!P.isChat("https://character.ai.evil.test/chat/abc"))
    check(!P.isChat("https://user@character.ai/chat/abc"))
    check(!P.isChat("http://character.ai/chat/abc"))
    check(!P.isChat("https://character.ai:444/chat/abc"))
    check(!P.isChat("$base?x=" + "x".repeat(4096)))
    check(P.samePage("$base?chat=one", "$base?chat=one"))
    check(!P.samePage("$base?chat=one", "$base?chat=two"))
    check(!P.samePage("$base#one", "$base#two"))
    check(!P.samePage("https://character.ai/", base))
    check(!P.samePage(null, null))
    check(!P.samePage("", ""))
    println("ChatRefresh: 22 checks passed")
}
