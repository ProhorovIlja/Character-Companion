package dev.caicompanion.app

/** Explicitly copied local diagnostics; no URLs, user identifiers, conversations or credentials. */
object SupportReport {
    data class Snapshot(
        val appVersion: String,
        val androidApi: Int,
        val webViewPackage: String?,
        val webViewVersion: String?,
        val javaHeapMb: Long,
        val filterRuleCount: Int,
        val importedRuleCount: Int,
        val blockedRequests: Long,
        val rendererRestarts: Int,
        val protectionEnabled: Boolean,
        val strictBlocking: Boolean
    )

    /** Keep provider-supplied data single-line and bounded, even on custom ROMs. */
    private val safeVersionOrPackage = Regex("^[A-Za-z0-9._+-]{1,80}$")
    private fun safeComponent(input: String?): String =
        if (input != null && safeVersionOrPackage.matches(input)) input else "unknown"

    fun format(snapshot: Snapshot): String = buildString {
        appendLine("Character Companion diagnostic report")
        appendLine("App: ${safeComponent(snapshot.appVersion)}")
        appendLine("Android API: ${snapshot.androidApi.coerceIn(0, 999)}")
        appendLine("Android 17 runtime: ${if (snapshot.androidApi >= 37) "yes" else "no"}")
        appendLine("WebView provider: ${safeComponent(snapshot.webViewPackage)}")
        appendLine("WebView version: ${safeComponent(snapshot.webViewVersion)}")
        appendLine("Java heap (MiB): ${snapshot.javaHeapMb.coerceAtLeast(0)}")
        appendLine("Filter rule count: ${snapshot.filterRuleCount.coerceAtLeast(0)}")
        appendLine("Imported domains: ${snapshot.importedRuleCount.coerceAtLeast(0)}")
        appendLine("Blocked requests this session: ${snapshot.blockedRequests.coerceAtLeast(0)}")
        appendLine("Automatic renderer restarts: ${snapshot.rendererRestarts.coerceAtLeast(0)}")
        appendLine("Protection enabled: ${snapshot.protectionEnabled}")
        appendLine("Strict filter: ${snapshot.strictBlocking}")
        append("Contains no chat content, account identifiers, URLs, cookies, auth tokens or device serial.")
    }
}
