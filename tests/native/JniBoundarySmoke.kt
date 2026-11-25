package dev.caicompanion.app

import java.io.IOException
import java.nio.file.Files

fun main() {
    val root = Files.createTempDirectory("mandy-jni-smoke-")
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    try {
        val path = root.resolve("synthetic.gguf")
        Files.write(path, byteArrayOf(1))
        val model = NativeModelLibrary.Model("0".repeat(64), 1, path)
        val messages = listOf(NativeConversation.Message("system", "Identity"), NativeConversation.Message("user", "Hello"))
        for (name in listOf("unsupported.gguf", "missing.gguf", "drops-system.gguf",
            "drops-assistant.gguf", "reordered.gguf", "duplicate.gguf", "oversized.gguf", "embedded-nul.gguf")) {
            val invalid = root.resolve(name)
            Files.write(invalid, byteArrayOf(1))
            val error = runCatching {
                EmbeddedLlama.openVerified(model.copy(path = invalid)) { false }.close()
            }.exceptionOrNull()
            verify(error is IOException && error.message == "Unsupported local model chat template")
        }
        verify(runCatching { EmbeddedLlama.openVerified(model) { true }.close() }.exceptionOrNull() is IOException)
        var checksAfterLoad = 0
        verify(runCatching { EmbeddedLlama.openVerified(model) { ++checksAfterLoad == 6 }.close() }.exceptionOrNull() is IOException)
        val loadFailure = IllegalStateException("test post-load callback failure")
        var loadChecks = 0
        val failedLoad = runCatching {
            EmbeddedLlama.openVerified(model) {
                if (++loadChecks == 6) throw loadFailure
                false
            }.close()
        }
        verify(failedLoad.exceptionOrNull() === loadFailure)
        val loadCaller = Thread.currentThread()
        var loadingChecks = 0
        val cancelledWhileLoading = runCatching {
            EmbeddedLlama.openVerified(model) {
                check(Thread.currentThread() === loadCaller)
                ++loadingChecks == 3
            }.close()
        }
        verify(cancelledWhileLoading.exceptionOrNull() is IOException && loadingChecks == 3)
        loadingChecks = 0
        val progressFailure = IllegalStateException("test model progress callback failure")
        val failedWhileLoading = runCatching {
            EmbeddedLlama.openVerified(model) {
                check(Thread.currentThread() === loadCaller)
                if (++loadingChecks == 4) throw progressFailure
                false
            }.close()
        }
        verify(failedWhileLoading.exceptionOrNull() === progressFailure && loadingChecks == 4)
        // The stub refuses a new load if a previous model was leaked.
        EmbeddedLlama.openVerified(model) { false }.use { engine ->
            fun success(seed: Int, cap: Int) {
                val streamed = StringBuilder()
                var metrics: GenerationMetrics? = null
                val reply = engine.generate(messages, 512, cap, seed, onMetrics = { metrics = it }, onText = { streamed.append(it) })
                verify(reply == "a".repeat(seed) && reply == streamed.toString())
                verify(metrics?.outputTokens == seed && metrics?.promptTokens == 2)
            }
            success(1, 4)
            success(4, 4)
            success(510, 510) // exact full context, no extra decode
            val streamed = StringBuilder()
            var reported = false
            val failed = runCatching { engine.generate(messages, 512, 4, 5, onMetrics = { reported = true }, onText = { streamed.append(it) }) }
            verify(failed.exceptionOrNull() is IOException)
            verify(streamed.toString() == "aaaa" && !reported)
            var stop = false
            val cancelled = runCatching { engine.generate(messages, 512, 4, 4, cancelled = { stop }, onText = { stop = true }) }
            verify(cancelled.exceptionOrNull() is IOException)
            val callbackFailure = IllegalStateException("test callback failure")
            val callback = runCatching { engine.generate(messages, 512, 4, 4, onText = { throw callbackFailure }) }
            verify(callback.exceptionOrNull() === callbackFailure)
            var stopChecks = 0
            val stopFailure = IllegalArgumentException("test cancellation callback failure")
            val stopCallback = runCatching {
                engine.generate(messages, 512, 4, 4, cancelled = {
                    if (++stopChecks == 3) throw stopFailure
                    false
                }, onText = {})
            }
            verify(stopCallback.exceptionOrNull() === stopFailure)
            // First graph callback follows the five boundary checks; the stub
            // also invokes it on a worker, where Java must not be called.
            val caller = Thread.currentThread()
            var decodeChecks = 0
            var decodePreview = false
            var decodeMetrics = false
            val decodeStop = runCatching {
                engine.generate(messages, 512, 4, 4, cancelled = {
                    check(Thread.currentThread() === caller)
                    ++decodeChecks == 6
                }, onMetrics = { decodeMetrics = true }, onText = { decodePreview = true })
            }
            verify(decodeStop.exceptionOrNull() is IOException && decodeChecks == 6)
            verify(!decodePreview && !decodeMetrics)
            val decodeFailure = IllegalStateException("test in-decode cancellation callback failure")
            decodeChecks = 0
            val decodeException = runCatching {
                engine.generate(messages, 512, 4, 4, cancelled = {
                    check(Thread.currentThread() === caller)
                    if (++decodeChecks == 6) throw decodeFailure
                    false
                }, onText = { error("No partial reply expected") })
            }
            verify(decodeException.exceptionOrNull() === decodeFailure && decodeChecks == 6)
            val metricsFailure = IllegalStateException("test metrics callback failure")
            val metricsCallback = runCatching {
                engine.generate(messages, 512, 4, 2, onMetrics = { throw metricsFailure }, onText = {})
            }
            verify(metricsCallback.exceptionOrNull() === metricsFailure)
            success(2, 2) // engine reusable after failure/cancellation
        }
        println("PASS $checks host JNI boundary checks (simulated backend)")
        residentModelSmoke()
    } finally { root.toFile().deleteRecursively() }
}
