import dev.caicompanion.app.HostBlocker
import dev.caicompanion.app.UrlPolicy
import dev.caicompanion.app.FilterEngine
import dev.caicompanion.app.BoundedText
import dev.caicompanion.app.BrowserProfiles
import dev.caicompanion.app.BrowserProfile
import java.io.StringReader

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
    check(!UrlPolicy.isInternal("https://character.ai.evil.test/"))
    check(HostBlocker.normalizeRule("-invalid.example") == null)
    check(!UrlPolicy.isTrustedMediaOrigin("https://accounts.google.com"))
    check(UrlPolicy.safeBookmark("https://foo.c.ai/"))
    check(UrlPolicy.sanitizeBookmark("https://character.ai/chat/abc?char=123#fragment") ==
        "https://character.ai/chat/abc?char=123")
    check(UrlPolicy.sanitizeBookmark("https://character.ai/chat/abc?access_token=secret") ==
        "https://character.ai/chat/abc")
    check(UrlPolicy.sanitizeBookmark("https://character.ai/chat/abc?c%6fde=123") ==
        "https://character.ai/chat/abc")
    check(UrlPolicy.sanitizeBookmark("https://accounts.google.com/?code=123") == null)
    check(UrlPolicy.sanitizeBookmark("https://character.ai:8443/chat") == null)
    check(!UrlPolicy.isHttps("https://character.ai:9999/"))
    check(UrlPolicy.safeShareUrl("https://character.ai/chat/abc?char=1#fragment") ==
        "https://character.ai/chat/abc?char=1")
    check(UrlPolicy.safeShareUrl("https://accounts.google.com/o/oauth2?code=secret") == null)
    check(UrlPolicy.safeShareUrl("https://evil.test/?code=secret") == null)
    check(UrlPolicy.safeShareUrl("https://character.ai/login?token=secret") == null)
    check(UrlPolicy.safeShareUrl("https://character.ai/%6fAuth%2fcallback?code=secret") == null)
    check(UrlPolicy.safeShareUrl("https://character.ai/account") == null)
    check(UrlPolicy.sanitizeBookmark("https://character.ai/chat/public") == "https://character.ai/chat/public")
    check(UrlPolicy.isIdentityUrl("https://accounts.google.com/o/oauth2/auth"))
    check(!UrlPolicy.isIdentityUrl("https://accounts.google.com.evil.org"))
    check(BoundedText.read(StringReader("test"), 4) == "test")
    check(BoundedText.read(StringReader("test"), 3) == null)
    check(BoundedText.read(StringReader(""), 4) == "")
    val strictEngine = FilterEngine(listOf("doubleclick.net"), listOf("sentry.io"))
    check(!strictEngine.shouldBlock("https://sentry.io/"))
    strictEngine.strict = true
    strictEngine.replace("", "")
    check(strictEngine.shouldBlock("https://sentry.io/"))
    strictEngine.replace("", "sentry.io")
    check(!strictEngine.shouldBlock("https://sentry.io/"))
    check(strictEngine.shouldBlock("https://doubleclick.net/"))
    val engine = FilterEngine(listOf("doubleclick.net"))
    check(engine.shouldBlock("https://a.doubleclick.net/x"))
    check(engine.blockedRequestCount.get() == 1L)
    engine.enabled = false
    check(!engine.shouldBlock("https://a.doubleclick.net/x"))
    engine.enabled = true
    engine.replace("tracker.test", "a.doubleclick.net")
    check(!engine.shouldBlock("https://a.doubleclick.net/x"))
    check(engine.shouldBlock("https://tracker.test/x"))
    check(BrowserProfiles.options(BrowserProfile.BALANCED).blocking)
    check(!BrowserProfiles.options(BrowserProfile.BALANCED).strictBlocking)
    check(!BrowserProfiles.options(BrowserProfile.COMPATIBILITY).blocking)
    check(BrowserProfiles.options(BrowserProfile.COMPATIBILITY).thirdPartyCookies)
    check(BrowserProfiles.options(BrowserProfile.PRIVATE).strictBlocking)
    check(!BrowserProfiles.options(BrowserProfile.PRIVATE).thirdPartyCookies)
    check(BrowserProfiles.options(BrowserProfile.DATA_SAVER).reduceImages)
    check(BrowserProfiles.options(BrowserProfile.DATA_SAVER).requireMediaGesture)
    println("PolicySmoke: browser policy and profile assertions passed")
}
