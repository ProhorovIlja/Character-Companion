package dev.caicompanion.app

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

/** Host-only integration using real model-library files and the simulated JNI backend. */
internal fun residentModelSmoke() {
    val root = Files.createTempDirectory("mandy-resident-smoke-")
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    try {
        fun fixture(marker: Byte): ByteArray = ByteBuffer.allocate(25).order(ByteOrder.LITTLE_ENDIAN)
            .put(byteArrayOf(71, 71, 85, 70)).putInt(3).putLong(1).putLong(0).put(marker).array()
        val library = NativeModelLibrary(root)
        val a = library.import(fixture(1).inputStream())
        val b = library.import(fixture(2).inputStream())
        val messages = listOf(NativeConversation.Message("system", "Identity"), NativeConversation.Message("user", "Hello"))
        val enough = ModelAdmission.Memory(4096 * ModelAdmission.MIB, 8192 * ModelAdmission.MIB, 128 * ModelAdmission.MIB, false)
        val tight = enough.copy(available = 1024 * ModelAdmission.MIB)
        val low = enough.copy(low = true)
        var admissionCalls = 0
        fun admit(model: NativeModelLibrary.Model, memory: ModelAdmission.Memory = enough) {
            admissionCalls++
            ModelAdmission.requireAllowed(model.bytes, memory)
        }
        ResidentModel().use { resident ->
            fun turn(model: NativeModelLibrary.Model, memory: (Int) -> ModelAdmission.Memory = { enough }, expectedReuse: Boolean) {
                val before = admissionCalls
                var preparation: ModelPreparation? = null
                val answer = resident.useMeasured(library, model.sha256, { false },
                    { admit(it, memory(admissionCalls - before)) }, { preparation = it }) { engine ->
                    engine.generate(messages, 512, 4, 2, onText = {})
                }
                verify(answer == "aa")
                verify(preparation?.reused == expectedReuse)
                verify(preparation?.let { it.verifyNanos >= 0 && it.admissionNanos >= 0 && it.loadNanos >= 0 && (!it.reused || it.loadNanos == 0L) } == true)
            }
            turn(a, expectedReuse = false)
            turn(a, expectedReuse = true)
            turn(b, expectedReuse = false)
            val beforeRetry = admissionCalls
            turn(b, { if (it == 0) tight else enough }, expectedReuse = false)
            verify(admissionCalls - beforeRetry == 2)
            val beforeLow = admissionCalls
            val denial = runCatching { resident.useModel(library, b.sha256, { false }, { admit(it, low) }) { error("Generation must not run") } }.exceptionOrNull()
            verify(denial is ModelAdmission.Denied && denial.decision.reason == ModelAdmission.Reason.LOW_MEMORY)
            verify(admissionCalls - beforeLow == 1)
            turn(b, expectedReuse = false)
            // Change bytes without changing the path or length; residency must not bypass digest verification.
            Files.write(b.path, fixture(3))
            val beforeCorrupt = admissionCalls
            verify(runCatching { resident.useModel(library, b.sha256, { false }, { admit(it) }) { error("Corrupt model must not run") } }.exceptionOrNull() is IllegalArgumentException)
            verify(admissionCalls == beforeCorrupt)
            Files.write(b.path, fixture(2))
            turn(b, expectedReuse = false)
            val failure = IllegalStateException("test action failure")
            verify(runCatching { resident.useModel(library, b.sha256, { false }, { admit(it) }) { throw failure } }.exceptionOrNull() === failure)
            turn(b, expectedReuse = false)
            resident.close()
            turn(b, expectedReuse = false)
        }
        // The stub refuses a second live model; a new owner confirms release at scope exit.
        ResidentModel().use { resident ->
            verify(resident.useModel(library, a.sha256, { false }, { admit(it) }) { true })
        }
        println("PASS $checks resident-model integration checks (simulated backend)")
    } finally { root.toFile().deleteRecursively() }
}
