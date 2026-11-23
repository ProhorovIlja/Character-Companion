package dev.caicompanion.app

/** Pure Android workspace/navigation rules, independent of Activity, WebView and network. */
object NativeWorkspaceNavigation {
    private const val HOME = "https://character.ai/"

    fun safeDestination(candidate: String?): String =
        UrlPolicy.sanitizeBookmark(candidate) ?: HOME

    fun needsInitialLoad(currentPage: String?): Boolean =
        currentPage.isNullOrBlank() || currentPage == "about:blank"

    fun shouldResumeRenderer(activityResumed: Boolean, workspaceVisible: Boolean): Boolean =
        activityResumed && !workspaceVisible
}
