import dev.caicompanion.app.ServiceLoadFeedback
import dev.caicompanion.app.ServiceLoadFeedback.Notice as N

fun verifyServiceLoad() {
    val state = ServiceLoadFeedback()
    check(state.notice(429, false, true) == N.NONE)
    check(state.notice(503, true, false) == N.NONE)
    check(state.notice(429, true, true) == N.RATE_LIMIT)
    check(state.notice(429, true, true) == N.NONE)
    check(state.notice(503, true, true) == N.NONE)
    check(state.notice(500, true, true) == N.SERVER_ERROR)
    state.beginNavigation()
    check(state.notice(503, true, true) == N.UNAVAILABLE)
    check(state.notice(503, true, true) == N.NONE)
    state.beginNavigation()
    check(state.notice(200, true, true) == N.NONE)
    check(state.notice(403, true, true) == N.NONE)
    check(state.notice(404, true, true) == N.NONE)
    check(state.notice(502, false, true) == N.NONE)
    check(state.notice(502, true, true) == N.SERVER_ERROR)
    check(state.notice(599, true, true) == N.SERVER_ERROR)
    check(state.notice(600, true, true) == N.NONE)
    check(state.notice(429, true, true) == N.RATE_LIMIT)
    println("ServiceLoad: 16 checks passed")
}

