package dev.caicompanion.app

/** A validated Share intent always overrides an optional offline home screen. */
object NativeStartupPolicy {
    fun useHub(preference: Boolean, validatedSharedLink: String?): Boolean =
        preference && validatedSharedLink == null
}
