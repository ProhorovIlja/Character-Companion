package dev.caicompanion.app

import kotlin.random.Random

fun main() {
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun roundTrip(text: String, limit: Int) {
        val chunks = SpokenText.chunks(text, limit)
        verify(chunks.joinToString("") == text && chunks.all {
            it.isNotEmpty() && it.length <= limit && !it.first().isLowSurrogate() && !it.last().isHighSurrogate()
        })
    }
    val text = "Hello, Привет 🌙! 日本語 café.\n"
    for (limit in 2..80) roundTrip(text.repeat(20), limit)
    roundTrip("🌙".repeat(8000), 3999)
    roundTrip("x".repeat(16000), 4000)
    verify(SpokenText.chunks("hello world!", 8).first() == "hello ")
    val random = Random(2026)
    repeat(100) { roundTrip(text.repeat(random.nextInt(1, 100)), random.nextInt(2, 500)) }
    for (invalid in listOf("", "   ", "x\u0000", "\uD800", "\uDC00", "x".repeat(16001))) {
        verify(runCatching { SpokenText.chunks(invalid, 4000) }.isFailure)
    }
    verify(runCatching { SpokenText.chunks(text, 1) }.isFailure)
    verify(runCatching { SpokenText.chunks(text, 16001) }.isFailure)
    println("PASS $checks spoken-text checks")
}
