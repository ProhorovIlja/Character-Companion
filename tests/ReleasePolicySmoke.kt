import dev.caicompanion.app.ReleasePolicy
import dev.caicompanion.app.ReleasePolicy.Status

fun verifyReleasePolicy() {
    val tag = "v0.39.0"
    val name = "CharacterCompanion-Android-0.39.0-gecko.apk"
    val base = "https://github.com/ProhorovIlja/Character-Companion/releases/download/$tag/"
    val asset = ReleasePolicy.Asset(name, base + name, 123456L, "sha256:" + "a".repeat(64), "uploaded")
    check(ReleasePolicy.status("0.38.0-rc1", tag, "gecko", listOf(asset)) == Status.NEWER)
    check(ReleasePolicy.status("0.39.0-rc2", tag, "gecko", listOf(asset)) == Status.NEWER)
    check(ReleasePolicy.status("0.39.0", tag, "gecko", listOf(asset)) == Status.CURRENT)
    check(ReleasePolicy.status("0.40.0", tag, "gecko", listOf(asset)) == Status.OLDER)
    check(ReleasePolicy.status("0.9.0", "v0.10.0", "gecko", emptyList()) == Status.NEWER_NO_APK)
    check(ReleasePolicy.status("0.38.0", "v0.39.0-rc1", "gecko", listOf(asset)) == Status.UNKNOWN)
    check(ReleasePolicy.status("garbage", tag, "gecko", listOf(asset)) == Status.UNKNOWN)
    check(ReleasePolicy.status("0.38.0", "latest", "gecko", listOf(asset)) == Status.UNKNOWN)
    check(ReleasePolicy.selectAsset(tag, "gecko", listOf(asset)) == asset)
    check(ReleasePolicy.selectAsset(tag, "legacy", listOf(asset)) == null)
    check(ReleasePolicy.selectAsset(tag, "gecko", listOf(asset, asset)) == null)
    check(ReleasePolicy.selectAsset(tag, "gecko", listOf(asset.copy(url = "https://evil.test/$name"))) == null)
    check(ReleasePolicy.selectAsset(tag, "gecko", listOf(asset.copy(url = asset.url + "?x=1"))) == null)
    check(ReleasePolicy.selectAsset(tag, "gecko", listOf(asset.copy(digest = ""))) == null)
    check(ReleasePolicy.selectAsset(tag, "gecko", listOf(asset.copy(digest = "sha256:" + "z".repeat(64)))) == null)
    check(ReleasePolicy.selectAsset(tag, "gecko", listOf(asset.copy(size = 0))) == null)
    check(ReleasePolicy.selectAsset(tag, "gecko", listOf(asset.copy(size = 536870913))) == null)
    check(ReleasePolicy.selectAsset(tag, "gecko", listOf(asset.copy(state = "starter"))) == null)
    check(ReleasePolicy.selectAsset(tag, "gecko", listOf(asset.copy(name = "debug.apk"))) == null)
    check(ReleasePolicy.selectAsset(tag, "gecko", List(101) { asset }) == null)
    check(ReleasePolicy.version("v00.1.0") == null)
    check(ReleasePolicy.version("v9999999999.0.0") == null)
    check(ReleasePolicy.version("1.2.3-rc0") == null)
    check(ReleasePolicy.version("1.2.3-rc9")!! < ReleasePolicy.version("1.2.3-rc10")!!)
    check(ReleasePolicy.version("1.2.3-rc10")!! < ReleasePolicy.version("1.2.3")!!)
    check(ReleasePolicy.version("v1.2.3").toString() == "1.2.3")
    println("ReleasePolicy: 26 checks passed")
}
