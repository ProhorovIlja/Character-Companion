import dev.caicompanion.app.LoginCompatibilityPolicy as P
import dev.caicompanion.app.LoginCompatibilitySession

fun verifyLoginRoutes() {
    val base = "https://character.ai"
    val legitimate = listOf("/login", "/en-US/login", "/auth/authorize", "/signup?next=%2Fchat", "/login?code=123")
    val ambiguous = listOf("/login/../chat", "/login/./chat", "/en/login/../../chat", "/login/%2e%2e/chat",
        "/login/%252e%252e/chat", "/login/a%2fb", "/login/a;b", "/login/\u202echat", "/chat/login", "/?next=/login")
    legitimate.forEach { check(P.isLoginPage(base + it)) }
    ambiguous.forEach { check(!P.isLoginPage(base + it)) }
    val session = LoginCompatibilitySession()
    session.setVisible(true)
    check(session.observePage("$base/login"))
    check(!session.observePage("$base/login/../chat") && !session.bypass)
    check(session.toggleManual("$base/chat/abc") && session.bypass)
    check(!session.observePage("$base/chat/xyz") && !session.manual)
    session.observePage("$base/login")
    session.setVisible(false)
    check(!session.bypass && !session.observePage("$base/login"))
    println("LoginRoutes: 20 checks passed")
}
