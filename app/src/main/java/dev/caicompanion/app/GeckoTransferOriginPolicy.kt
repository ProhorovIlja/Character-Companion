package dev.caicompanion.app

/**
 * A transfer may complete only on the exact first-party page that initiated it.
 * Callers MUST revoke the pending operation on a new top-level page load, too:
 * equality alone cannot detect leaving a page and later returning to the same URL.
 */
object GeckoTransferOriginPolicy {
    fun isSamePage(original: String?, current: String?): Boolean =
        original != null && original == current && UrlPolicy.isTrustedMediaOrigin(current)
}
