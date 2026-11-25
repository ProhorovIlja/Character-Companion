package dev.caicompanion.app

import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

// Host-only engine fixture; no JNI, weights or native memory allocation.
class EmbeddedLlama private constructor() : java.io.Closeable {
    var closed = false
        private set
    override fun close() { check(!closed); closed = true; closes++; onClose() }
    companion object {
        var opens = 0
        var closes = 0
        var onClose: () -> Unit = {}
        fun openVerified(model: NativeModelLibrary.Model, cancelled: () -> Boolean): EmbeddedLlama {
            check(Files.isRegularFile(model.path) && !cancelled())
            opens++
            return EmbeddedLlama()
        }
    }
}

fun main() {
    val root = Files.createTempDirectory("mandy-admission-retry-")
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun deny(reason: ModelAdmission.Reason): Nothing = throw ModelAdmission.Denied(ModelAdmission.Decision(reason, 1, 0))
    try {
        val library = NativeModelLibrary(root)
        val bytes = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
            .put(byteArrayOf(71,71,85,70)).putInt(3).putLong(1).putLong(0).array()
        val sha = library.import(ByteArrayInputStream(bytes)).sha256
        ResidentModel().use { resident ->
            var metrics: ModelPreparation? = null
            fun turn(admit: (NativeModelLibrary.Model) -> Unit = {}, cancelled: () -> Boolean = { false }) =
                resident.useMeasured(library, sha, cancelled, admit, { metrics = it }) { check(!it.closed); "reply" }
            verify(turn() == "reply" && EmbeddedLlama.opens == 1 && metrics?.reused == false)
            verify(turn() == "reply" && EmbeddedLlama.opens == 1 && metrics?.reused == true)
            var attempts = 0
            verify(turn(admit = { if (++attempts == 1) deny(ModelAdmission.Reason.INSUFFICIENT_HEADROOM); check(EmbeddedLlama.closes == 1) }) == "reply")
            verify(attempts == 2 && EmbeddedLlama.opens == 2 && metrics?.reused == false)
            attempts = 0
            verify(runCatching { turn(admit = { attempts++; deny(ModelAdmission.Reason.INSUFFICIENT_HEADROOM) }) }.exceptionOrNull() is ModelAdmission.Denied)
            verify(attempts == 2 && EmbeddedLlama.closes == 2 && EmbeddedLlama.opens == 2)
            attempts = 0
            verify(runCatching { turn(admit = { attempts++; deny(ModelAdmission.Reason.INSUFFICIENT_HEADROOM) }) }.isFailure)
            verify(attempts == 1 && EmbeddedLlama.opens == 2)
            verify(turn() == "reply")
            attempts = 0
            verify(runCatching { turn(admit = { attempts++; deny(ModelAdmission.Reason.LOW_MEMORY) }) }.isFailure)
            verify(attempts == 1 && EmbeddedLlama.closes == 3)
            verify(turn() == "reply")
            attempts = 0
            verify(runCatching { turn(admit = { attempts++; deny(ModelAdmission.Reason.INVALID_METRICS) }) }.isFailure)
            verify(attempts == 1 && EmbeddedLlama.closes == 4)
            verify(turn() == "reply")
            var stop = false
            EmbeddedLlama.onClose = { stop = true }
            attempts = 0
            verify(runCatching { turn(admit = { attempts++; deny(ModelAdmission.Reason.INSUFFICIENT_HEADROOM) }, cancelled = { stop }) }.exceptionOrNull() is IOException)
            verify(attempts == 1 && EmbeddedLlama.opens == 5 && EmbeddedLlama.closes == 5)
            EmbeddedLlama.onClose = {}
            verify(turn() == "reply" && EmbeddedLlama.opens == 6)
        }
        verify(EmbeddedLlama.opens == EmbeddedLlama.closes)
        println("PASS: $checks resident admission assertions with host engine fixture")
    } finally { root.toFile().deleteRecursively() }
}
