import dev.caicompanion.app.UpdateProgress
import dev.caicompanion.app.UpdateIntegrity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

fun verifyUpdateProgress() {
    val gate = UpdateProgress(1000, 10)
    check(gate.next(0, 10) == null)
    check(gate.next(100, 259) == null)
    check(gate.next(100, 260) == 10)
    check(gate.next(200, 261) == null)
    check(gate.next(200, 510) == 20)
    check(gate.next(100, 800) == null)
    check(gate.next(1001, 800) == null)
    check(gate.next(500, 500) == null)
    check(gate.next(1000, 511) == 100)
    check(gate.next(1000, 900) == null)
    check(UpdateProgress(536870912, 0).next(536870912, 1) == 100)
    check(runCatching { UpdateProgress(0, 0) }.isFailure)
    check(UpdateProgress.rateLimited(429, null, null))
    check(UpdateProgress.rateLimited(403, "0", null))
    check(UpdateProgress.rateLimited(403, null, "60"))
    check(!UpdateProgress.rateLimited(403, "1", null))
    check(!UpdateProgress.rateLimited(404, "0", "60"))
    val bytes = ByteArray(150000) { (it % 251).toByte() }
    val digest = "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    val counts = mutableListOf<Long>()
    val out = ByteArrayOutputStream()
    check(UpdateIntegrity.copyVerified(bytes.inputStream(), out, bytes.size.toLong(), digest, counts::add) { false })
    check(counts == listOf(65536L, 131072L, 150000L))
    check(out.toByteArray().contentEquals(bytes))
    var stop = false
    val input = object : ByteArrayInputStream(bytes) {
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            stop = true
            return n
        }
    }
    val cancelledOut = ByteArrayOutputStream()
    check(!UpdateIntegrity.copyVerified(input, cancelledOut, bytes.size.toLong(), digest) { stop })
    check(cancelledOut.size() == 0)
    val emptyRead = object : InputStream() {
        override fun read(): Int = 0
        override fun read(b: ByteArray, off: Int, len: Int): Int = 0
    }
    check(!UpdateIntegrity.copyVerified(emptyRead, ByteArrayOutputStream(), bytes.size.toLong(), digest) { false })
    println("UpdateProgress: 23 checks passed")
}
