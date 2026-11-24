package dev.caicompanion.app

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

/** Called only on the originating JVM thread by native cancellation checkpoints. */
internal class WhisperCancellation(private val cancelled: () -> Boolean) {
    fun isCancelled(): Boolean = cancelled() || Thread.currentThread().isInterrupted
}

/** Explicit one-shot offline ASR. Caller owns and clears PCM; no automatic model selection. */
internal object EmbeddedWhisper {
    private const val MAX_MODEL = 2L * 1024 * 1024 * 1024
    private external fun nativeTranscribeV2(path: ByteArray, pcm: ShortArray, russian: Boolean, threads: Int, stop: WhisperCancellation): ByteArray
    private var active = false
    @Synchronized fun transcribe(model: Path, sha: String, pcm: ShortArray, language: MandyLanguage,
                                threads: Int = 4, cancelled: () -> Boolean = { false }): String {
        check(!active) { "Reentrant transcription" }
        active = true
        try { return recognize(model, sha, pcm, language, threads, cancelled) }
        finally { active = false }
    }
    private fun recognize(model: Path, sha: String, pcm: ShortArray, language: MandyLanguage,
                          threads: Int, cancelled: () -> Boolean): String {
        require(sha.matches(Regex("[0-9a-f]{64}")) && threads in 1..8 && pcm.size in 1..2_880_000)
        fun checkActive() {
            if (cancelled() || Thread.currentThread().isInterrupted) throw IOException("Transcription cancelled")
        }
        checkActive()
        // A constant waveform cannot contain changing speech. Do not guess an energy threshold.
        val first = pcm[0]
        var varied = false
        for (i in pcm.indices) {
            if ((i and 4095) == 0) checkActive()
            if (pcm[i] != first) { varied = true; break }
        }
        require(varied) { "Recording contains no varying signal" }
        require(Files.isRegularFile(model, LinkOption.NOFOLLOW_LINKS)) { "Speech model unavailable" }
        val size = Files.size(model)
        require(size in 1..MAX_MODEL)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(128 * 1024)
        var read = 0L
        Files.newInputStream(model, LinkOption.NOFOLLOW_LINKS).use { input ->
            while (true) {
                checkActive()
                val n = input.read(buffer)
                if (n == -1) break
                require(n > 0 && n.toLong() <= size - read)
                digest.update(buffer, 0, n); read += n
            }
        }
        require(read == size && digest.digest().joinToString("") { "%02x".format(java.util.Locale.ROOT, it.toInt() and 255) } == sha) { "Speech model digest mismatch" }
        checkActive()
        System.loadLibrary("mandy_whisper")
        val path = model.toAbsolutePath().toString().toByteArray(Charsets.UTF_8)
        val result = nativeTranscribeV2(path, pcm, language == MandyLanguage.RUSSIAN, threads, WhisperCancellation(cancelled))
        try {
            checkActive()
            require(result.size in 1..64000)
            val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(result)).toString().trim()
            NativeConversation.validateText(text)
            return text
        } finally { result.fill(0) }
    }
}
