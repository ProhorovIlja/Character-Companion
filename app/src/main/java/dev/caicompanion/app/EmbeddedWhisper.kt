package dev.caicompanion.app

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Path

/** Called only on the originating JVM thread by native cancellation checkpoints. */
internal class WhisperCancellation(private val cancelled: () -> Boolean) {
    fun isCancelled(): Boolean = cancelled() || Thread.currentThread().isInterrupted
}

/** Explicit one-shot offline ASR. Caller owns and clears PCM; no automatic model selection. */
internal object EmbeddedWhisper {
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
        NativeModelLibrary.verifyFile(model, sha, NativeModelLibrary.Format.WHISPER, cancelled = cancelled)
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
