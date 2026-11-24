import dev.caicompanion.app.BrowserSessionPolicy as P

fun verifyBrowserRestore() {
    val home = P.HOME
    val chat = "https://character.ai/chat/abc?char=123"
    val other = "https://c.ai/chats"
    fun resolve(restoring: Boolean = false, supplied: String? = null, saved: String? = null,
                retained: String? = null, current: String? = home, open: Boolean = false) =
        P.resolveLaunch(restoring, supplied, saved, retained, current, open)
    check(resolve(supplied = chat, open = true) == P.Launch(chat, true))
    check(resolve(supplied = chat) == P.Launch(chat, false))
    check(resolve(current = chat) == P.Launch(chat, false))
    check(resolve(true, supplied = other, saved = chat, open = true) == P.Launch(chat, false))
    check(resolve(true, supplied = other, retained = chat, open = true) == P.Launch(chat, false))
    check(resolve(true, supplied = other, current = chat, open = true) == P.Launch(chat, false))
    check(resolve(true, supplied = other, saved = home, retained = chat, open = true) == P.Launch(home, false))
    check(resolve(true, saved = "https://character.ai/callback?code=SECRET", retained = chat) == P.Launch(home, false))
    check(resolve(true, saved = "https://accounts.google.com/o/oauth2/v2/auth") == P.Launch(home, false))
    check(resolve(true, saved = "$chat#access_token=SECRET") == P.Launch(chat, false))
    check(resolve(true, saved = "https://character.ai/chat/abc?token=SECRET") == P.Launch("https://character.ai/chat/abc", false))
    check(resolve(true, saved = "https://character.ai.evil.test/chat") == P.Launch(home, false))
    check(resolve(true, saved = "https://evil@character.ai/chat") == P.Launch(home, false))
    check(resolve(true, saved = "https://character.ai/%252Fauth%252Fcallback") == P.Launch(home, false))
    check(resolve(supplied = other, saved = chat, retained = chat, open = true) == P.Launch(other, true))
    check(resolve(true, supplied = chat, current = null, open = true) == P.Launch(home, false))
    println("BrowserRestore: 16 checks passed")
}
