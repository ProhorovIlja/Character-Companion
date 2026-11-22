import dev.caicompanion.app.HostBlocker
import dev.caicompanion.app.UrlPolicy
import dev.caicompanion.app.FilterEngine

fun main() {
    val filters = HostBlocker(
        listOf("# ignore", "doubleclick.net", "example.com", "evilnotallowed.net", "http://bad.invalid"),
        listOf("tracker.test", "..invalid", "foo*.com"),
        listOf("safe.example.com")
    )
    check(filters.shouldBlock("https://doubleclick.net/a"))
    check(filters.shouldBlock("https://foo.doubleclick.net/a"))
    check(!filters.shouldBlock("https://notdoubleclick.net/a"))
    check(!filters.shouldBlock("https://safe.example.com/a"))
    check(filters.shouldBlock("https://other.example.com/a"))
    check(!filters.shouldBlock("https://character.ai/chats"))
    check(!filters.shouldBlock("https://foo.character.ai/chats"))
    check(!filters.shouldBlock("https://c.ai"))
    check(!filters.shouldBlock("https://foo.c.ai"))
    check(!filters.shouldBlock("https://character.ai.attacker.com/"))
    check(!filters.shouldBlock("file:///etc/passwd"))
    check(!filters.shouldBlock("javascript:alert(1)"))
    check(!filters.shouldBlock("https://example.com.evil.org"))
    check(filters.shouldBlock("http://tracker.test/a"))
    check(!filters.shouldBlock("https://www.foo.com"))
    check(HostBlocker.normalizeRule("a..b") == null)
    check(HostBlocker.normalizeRule("https://a.com/") == null)
    check(HostBlocker.normalizeRule("dot.example") == "dot.example")
    check(UrlPolicy.isHttps("https://character.ai/"))
    check(!UrlPolicy.isHttps("http://character.ai/"))
    check(!UrlPolicy.isHttps("https://evil@character.ai/"))
    check(!UrlPolicy.isHttps("intent://character.ai"))
    check(UrlPolicy.isInternal("https://character.ai/"))
    check(UrlPolicy.isInternal("https://accounts.google.com/"))
    check(!UrlPolicy.isInternal("https://notcharacter.ai/"))
    check(!UrlPolicy.isTrustedMediaOrigin("https://accounts.google.com"))
    check(UrlPolicy.safeBookmark("https://foo.c.ai/"))
    val engine = FilterEngine(listOf("doubleclick.net"))
    check(engine.shouldBlock("https://a.doubleclick.net/x"))
    check(engine.blockedRequestCount.get() == 1L)
    engine.enabled = false
    check(!engine.shouldBlock("https://a.doubleclick.net/x"))
    engine.enabled = true
    engine.replace("tracker.test", "a.doubleclick.net")
    check(!engine.shouldBlock("https://a.doubleclick.net/x"))
    check(engine.shouldBlock("https://tracker.test/x"))
    println("PolicySmoke: 34 assertions passed")
}
