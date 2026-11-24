package dev.caicompanion.app

import java.io.Closeable

/** Bounded in-memory mono PCM16, normalized to 16 kHz without a temporary audio file. */
internal class PcmSpeechBuffer(val inputRate: Int, maxSeconds: Int = 180) : Closeable {
    companion object { const val RATE = 16000 }
    init { require(inputRate in 16000..96000 && maxSeconds in 1..180) }
    private val maxFrames = inputRate * maxSeconds
    private val maxSamples = RATE * maxSeconds
    private var samples = ShortArray(minOf(RATE, maxSamples))
    private var size = 0
    private var frames = 0
    private var target = 0L
    private var sum = 0L
    private var weight = 0L
    private var closed = false
    val remainingFrames: Int get() { check(!closed); return maxFrames - frames }

    /** Feed consecutive mono samples. Rounding matches the Windows area-weighted PCM resampler. */
    fun append(input: ShortArray, count: Int = input.size) {
        check(!closed)
        require(count in 0..input.size && count <= remainingFrames)
        for (i in 0 until count) {
            var current = frames.toLong() * RATE
            val end = current + RATE
            while (current < end) {
                val boundary = (target + 1) * inputRate
                val overlap = minOf(end, boundary) - current
                sum += input[i].toLong() * overlap
                weight += overlap
                current += overlap
                if (current == boundary) flush()
            }
            frames++
        }
    }
    private fun flush() {
        check(weight > 0 && size < maxSamples)
        if (size == samples.size) {
            val old = samples
            samples = old.copyOf(minOf(maxSamples, old.size * 2))
            old.fill(0)
        }
        val magnitude = (kotlin.math.abs(sum) + weight / 2) / weight
        samples[size++] = (if (sum < 0) -magnitude else magnitude).toShort()
        target++; sum = 0; weight = 0
    }
    /** Single-use result. Caller owns and should clear the returned samples after recognition. */
    fun finish(): ShortArray {
        check(!closed)
        try {
            require(frames > 0) { "Empty recording" }
            if (weight > 0) flush()
            return samples.copyOf(size)
        } finally { close() }
    }
    override fun close() {
        samples.fill(0); sum = 0; weight = 0; closed = true
    }
}
