package dev.caicompanion.app

/** One-click local profiles. These change browser-shell settings, not Character.AI accounts. */
enum class BrowserProfile {
    BALANCED, COMPATIBILITY, PRIVATE, DATA_SAVER
}

data class BrowserProfileOptions(
    val blocking: Boolean,
    val strictBlocking: Boolean,
    val thirdPartyCookies: Boolean,
    val reduceImages: Boolean,
    val requireMediaGesture: Boolean = true
)

object BrowserProfiles {
    fun options(profile: BrowserProfile): BrowserProfileOptions = when (profile) {
        BrowserProfile.BALANCED -> BrowserProfileOptions(true, false, true, false)
        BrowserProfile.COMPATIBILITY -> BrowserProfileOptions(false, false, true, false)
        BrowserProfile.PRIVATE -> BrowserProfileOptions(true, true, false, false)
        BrowserProfile.DATA_SAVER -> BrowserProfileOptions(true, false, true, true)
    }
}
