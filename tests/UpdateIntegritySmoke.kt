import dev.caicompanion.app.UpdateIntegrity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

fun verifyUpdateIntegrity() {
    val bytes = "abc".toByteArray()
    val digest = "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    fun checkCopy(size: Long, hash: String = digest, cancel: Boolean = false): Boolean {
        val out = ByteArrayOutputStream()
        val ok = UpdateIntegrity.copyVerified(ByteArrayInputStream(bytes), out, size, hash) { cancel }
        if (ok) check(out.toByteArray().contentEquals(bytes))
        return ok
    }
    check(checkCopy(3))
    check(checkCopy(3, digest.replaceAfter(':', digest.substringAfter(':').uppercase())))
    check(!checkCopy(2))
    check(!checkCopy(4))
    check(!checkCopy(0))
    check(!checkCopy(536870913))
    check(!checkCopy(3, "sha256:" + "0".repeat(64)))
    check(!checkCopy(3, "bad"))
    check(!checkCopy(3, cancel = true))
    check(UpdateIntegrity.redirectAllowed("https://release-assets.githubusercontent.com/path?sig=x"))
    check(UpdateIntegrity.redirectAllowed("https://objects.githubusercontent.com/path"))
    check(!UpdateIntegrity.redirectAllowed("http://release-assets.githubusercontent.com/path"))
    check(!UpdateIntegrity.redirectAllowed("https://release-assets.githubusercontent.com.evil.test/path"))
    check(!UpdateIntegrity.redirectAllowed("https://evil@release-assets.githubusercontent.com/path"))
    check(!UpdateIntegrity.redirectAllowed("https://release-assets.githubusercontent.com:444/path"))
    check(!UpdateIntegrity.redirectAllowed("https://release-assets.githubusercontent.com/path#x"))
    println("UpdateIntegrity: 16 checks passed")
}
