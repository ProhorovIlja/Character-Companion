package dev.caicompanion.app

fun main() {
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    val random = java.util.Random(4816)
    for (length in listOf(1, 15999, 16000, 16001, 32768, 160000, 2880000)) {
        val source = ShortArray(length) { random.nextInt(65536).toShort() }
        for (chunk in listOf(1, 320, 4097, length)) {
            if (length > 160000 && chunk == 1) continue
            PcmSpeechBuffer(16000).use { buffer ->
                var offset = 0
                val scratch = ShortArray(minOf(chunk, length))
                while (offset < length) {
                    val n = minOf(scratch.size, length - offset)
                    source.copyInto(scratch, 0, offset, offset + n)
                    buffer.append(scratch, n)
                    scratch.fill(0)
                    offset += n
                }
                buffer.append(scratch, 0)
                verify(buffer.remainingFrames == 2880000 - length)
                verify(buffer.finish().contentEquals(source))
            }
        }
    }
    PcmSpeechBuffer(16000, 1).use { buffer ->
        val source = ShortArray(16000) { 123 }
        verify(runCatching { buffer.append(source, 16001) }.isFailure)
        buffer.append(source, 16000)
        verify(runCatching { buffer.append(source, 1) }.isFailure)
        verify(buffer.finish().contentEquals(source))
    }
    println("PCM fast path: $checks checks passed")
}
