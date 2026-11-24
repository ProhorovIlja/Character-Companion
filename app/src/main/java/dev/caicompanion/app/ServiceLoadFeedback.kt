package dev.caicompanion.app

/** Advisory HTTP feedback only; never retries or treats a response as an account entitlement. */
class ServiceLoadFeedback {
    enum class Notice { NONE, RATE_LIMIT, UNAVAILABLE, SERVER_ERROR }
    private var reported = false

    fun beginNavigation() { reported = false }

    fun notice(status: Int, mainFrame: Boolean, firstParty: Boolean): Notice {
        if (!mainFrame || !firstParty) return Notice.NONE
        val result = when (status) {
            429 -> Notice.RATE_LIMIT
            503 -> Notice.UNAVAILABLE
            in 500..599 -> Notice.SERVER_ERROR
            else -> Notice.NONE
        }
        if (result == Notice.RATE_LIMIT || result == Notice.UNAVAILABLE) {
            if (reported) return Notice.NONE
            reported = true
        }
        return result
    }
}
