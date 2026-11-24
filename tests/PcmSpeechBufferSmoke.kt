package dev.caicompanion.app

fun main() {
    var checks = 0
    fun checkIt(ok: Boolean) { check(ok); checks++ }
    fun reject(action: () -> Unit) = checkIt(runCatching(action).isFailure)
    fun convert(rate: Int, samples: ShortArray, split: Int = samples.size): ShortArray = PcmSpeechBuffer(rate, 1).use { b ->
        for (i in samples.indices step split) b.append(samples.copyOfRange(i, minOf(samples.size, i + split)))
        b.finish()
    }
    val edge = shortArrayOf(-32768, -1, 0, 1, 32767)
    checkIt(convert(16000, edge).contentEquals(edge))
    checkIt(convert(48000, shortArrayOf(0, 300, 600, -900, 0, 0)).contentEquals(shortArrayOf(300, -300)))
    checkIt(convert(32000, shortArrayOf(0, 1, 0, -1)).contentEquals(shortArrayOf(1, -1)))
    checkIt(convert(48000, shortArrayOf(123)).contentEquals(shortArrayOf(123)))
    checkIt(convert(48000, shortArrayOf(-32768, -32768, -32768)).contentEquals(shortArrayOf(-32768)))
    for (rate in listOf(16000, 44100, 48000, 96000)) {
        val audio = ShortArray(rate) { (it * 19 % 65536 - 32768).toShort() }
        val one = convert(rate, audio)
        checkIt(one.size == 16000)
        checkIt(one.contentEquals(convert(rate, audio, 317)))
        checkIt(convert(rate, ShortArray(rate) { 1234 }, 127).all { it == 1234.toShort() })
    }
    PcmSpeechBuffer(16000, 1).use { b ->
        reject { b.append(shortArrayOf(1), 2) }
        checkIt(b.remainingFrames == 16000)
        reject { b.append(ShortArray(16001)) }
        b.append(ShortArray(16000) { 2 })
        checkIt(b.remainingFrames == 0)
        reject { b.append(shortArrayOf(1)) }
        checkIt(b.finish().size == 16000)
        reject { b.finish() }
        reject { b.append(shortArrayOf(1)) }
    }
    PcmSpeechBuffer(48000).use { b ->
        b.append(ShortArray(96000) { 17 })
        checkIt(b.finish().contentEquals(ShortArray(32000) { 17 }))
    }
    PcmSpeechBuffer(16000).use { b -> reject { b.finish() }; reject { b.append(shortArrayOf(1)) } }
    reject { PcmSpeechBuffer(8000) }
    reject { PcmSpeechBuffer(16000, 181) }
    println("PcmSpeechBuffer: $checks checks passed")
}
