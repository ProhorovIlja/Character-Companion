package dev.caicompanion.app

/** Pure validation of GitHub release metadata; this is not APK signature verification. */
object ReleasePolicy {
    private const val BASE = "https://github.com/ProhorovIlja/Character-Companion/releases/download/"
    private val versionPattern = Regex("^v?(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})(?:-rc([1-9][0-9]{0,8}))?$")
    private val digestPattern = Regex("^sha256:[0-9a-fA-F]{64}$")
    data class Version(val major: Int, val minor: Int, val patch: Int, val rc: Int?) : Comparable<Version> {
        override fun compareTo(other: Version): Int = compareValuesBy(this, other,
            { it.major }, { it.minor }, { it.patch }, { it.rc ?: Int.MAX_VALUE })
        override fun toString(): String = "$major.$minor.$patch" + (rc?.let { "-rc$it" } ?: "")
    }
    data class Asset(val name: String, val url: String, val size: Long, val digest: String, val state: String)
    enum class Status { UNKNOWN, CURRENT, OLDER, NEWER_NO_APK, NEWER }
    fun version(tag: String): Version? {
        val m = versionPattern.matchEntire(tag) ?: return null
        return Version(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(),
            m.groupValues[4].toIntOrNull())
    }
    const val COMBINED_RENDERER = "gecko"

    /** Both installed variants converge on the combined ARM64 release. */
    fun canUseCombined(flavor: String, arm64: Boolean): Boolean =
        arm64 && flavor in setOf("gecko", "legacy")

    fun selectAsset(tag: String, flavor: String, assets: List<Asset>,
                    arm64: Boolean = flavor == COMBINED_RENDERER): Asset? {
        val v = version(tag) ?: return null
        if (v.rc != null || !canUseCombined(flavor, arm64) || assets.size > 100) return null
        val name = "CharacterCompanion-Android-$v-$COMBINED_RENDERER.apk"
        // Reject ambiguous duplicate filenames, even if only one entry looks usable.
        val asset = assets.filter { it.name == name }.singleOrNull() ?: return null
        return asset.takeIf { it.url == "$BASE$tag/$name" && it.state == "uploaded" &&
            it.size in 1..536_870_912L && digestPattern.matches(it.digest) }
    }
    fun status(installed: String, tag: String, flavor: String, assets: List<Asset>,
               arm64: Boolean = flavor == COMBINED_RENDERER): Status {
        val current = version(installed) ?: return Status.UNKNOWN
        val remote = version(tag) ?: return Status.UNKNOWN
        if (remote.rc != null) return Status.UNKNOWN
        return when {
            remote < current -> Status.OLDER
            remote == current -> Status.CURRENT
            selectAsset(tag, flavor, assets, arm64) == null -> Status.NEWER_NO_APK
            else -> Status.NEWER
        }
    }

    data class ArchiveIdentity(val packageName: String, val versionCode: Long,
        val versionName: String, val renderer: String?, val debuggable: Boolean,
        val signers: Set<String>?)

    /** Metadata gate after digest verification; Android's installer independently verifies signing. */
    fun acceptsArchive(installedFlavor: String, arm64: Boolean, installed: ArchiveIdentity,
                       tag: String, candidate: ArchiveIdentity): Boolean {
        val expected = version(tag) ?: return false
        val previous = version(installed.versionName) ?: return false
        val signers = installed.signers ?: return false
        return canUseCombined(installedFlavor, arm64) && !installed.debuggable &&
            expected.rc == null && expected > previous &&
            installed.packageName.isNotBlank() && candidate.packageName == installed.packageName &&
            installed.versionCode > 0 && candidate.versionCode > installed.versionCode &&
            version(candidate.versionName) == expected &&
            candidate.renderer == COMBINED_RENDERER && !candidate.debuggable &&
            signers.isNotEmpty() && candidate.signers == signers
    }
}
