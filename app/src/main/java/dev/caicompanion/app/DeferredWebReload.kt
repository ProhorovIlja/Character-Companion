package dev.caicompanion.app

/** Pure UI lifecycle coordinator: hidden/offline library never triggers an implicit network reload. */
class DeferredWebReload {
    private var pending = false

    /** Record a settings change. The Activity decides if it may reload immediately. */
    fun markPending() { pending = true }

    /** Consume a deferred refresh only when the WebView is visible and the Activity resumed. */
    fun consumeIfVisible(activityResumed: Boolean, workspaceVisible: Boolean): Boolean {
        if (!activityResumed || workspaceVisible || !pending) return false
        pending = false
        return true
    }

    /** A new first-party navigation already uses the current settings; no second reload needed. */
    fun consumedByNavigation() { pending = false }

    fun isPending(): Boolean = pending
}
