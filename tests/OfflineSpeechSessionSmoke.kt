package dev.caicompanion.app

fun main() {
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    val session = OfflineSpeechSession()
    val audio = shortArrayOf(-2, 2)
    var recognized = false
    verify(session.run(capture = { stop, cancel ->
        verify(!stop() && !cancel())
        verify(session.requestStop())
        verify(stop() && !session.requestStop())
        audio
    }, onTranscribing = { verify(!session.requestStop()) }) { pcm, cancel ->
        verify(pcm === audio && pcm.contentEquals(shortArrayOf(-2, 2)) && !cancel())
        recognized = true
        "Привет 🌙"
    } == "Привет 🌙")
    verify(recognized && audio.all { it == 0.toShort() })
    bad { session.run({ _, _ -> error("unexpected capture") }) { _, _ -> error("unexpected ASR") } }
    verify(!session.requestStop())
    val before = OfflineSpeechSession()
    before.cancel()
    bad { before.run({ _, _ -> error("unexpected capture") }) { _, _ -> error("unexpected ASR") } }
    val stopped = OfflineSpeechSession()
    verify(stopped.requestStop())
    bad { stopped.run({ _, _ -> error("unexpected capture") }) { _, _ -> error("unexpected ASR") } }
    val during = OfflineSpeechSession()
    val cancelledAudio = shortArrayOf(1, 2)
    bad { during.run({ _, cancel -> during.cancel(); verify(cancel()); cancelledAudio }) { _, _ -> error("unexpected ASR") } }
    verify(cancelledAudio.all { it == 0.toShort() })
    val decoding = OfflineSpeechSession()
    val decodedAudio = shortArrayOf(1, 2)
    bad { decoding.run({ _, _ -> decodedAudio }) { _, cancel -> decoding.cancel(); verify(cancel()); "No result" } }
    verify(decodedAudio.all { it == 0.toShort() })
    for (text in listOf("", "\u0000", "x".repeat(16001))) {
        val a = shortArrayOf(1, 2)
        bad { OfflineSpeechSession().run({ _, _ -> a }) { _, _ -> text } }
        verify(a.all { it == 0.toShort() })
    }
    val errorAudio = shortArrayOf(1, 2)
    bad { OfflineSpeechSession().run({ _, _ -> errorAudio }, { error("callback failed") }) { _, _ -> error("unexpected ASR") } }
    verify(errorAudio.all { it == 0.toShort() })
    val engineAudio = shortArrayOf(1, 2)
    bad { OfflineSpeechSession().run({ _, _ -> engineAudio }) { _, _ -> error("recognition failed") } }
    verify(engineAudio.all { it == 0.toShort() })
    val natural = shortArrayOf(1, 2)
    verify(OfflineSpeechSession().run({ _, _ -> natural }) { _, _ -> "Hello" } == "Hello")
    verify(natural.all { it == 0.toShort() })
    bad { OfflineSpeechSession().run({ _, _ -> ShortArray(0) }) { _, _ -> error("unexpected ASR") } }
    println("OfflineSpeechSession: $checks checks passed")
}
