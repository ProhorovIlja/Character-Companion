package dev.caicompanion.app

import java.util.UUID

fun main() {
    fun id(n: Long) = UUID(0, n).toString()
    val key = ByteArray(32) { it.toByte() }
    val mac = ByteArray(32) { (it + 32).toByte() }
    val account = id(1); val snapshot = id(2)
    val a = NativeConversation.Event(id(3), id(4), id(5), id(6), 1, "user", "text", "Привет 🌙")
    val b = a.copy(id = id(7), clock = 2, text = "Second")
    val staged = mutableListOf<ByteArray>()
    val manifest = HistorySnapshot.export(key, mac, account, snapshot, sequenceOf(listOf(a), listOf(b))) { staged.add(it) }
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    verify(staged.size == 2)
    verify(HistoryManifest.verify(mac, account, manifest, staged.asSequence()) == snapshot)
    var committed = emptyList<NativeConversation.Event>()
    fun load(pages: List<ByteArray>, m: ByteArray = manifest, k: ByteArray = key, accountId: String = account): String {
        // Test transaction double: publish only after the entire import succeeds.
        var draft = committed
        val result = HistorySnapshot.import(k, mac, accountId, m, pages.asSequence()) { rows -> draft = HistoryWire.merge(draft + rows) }
        committed = draft
        return result
    }
    val altered = staged[1].copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }
    for (pages in listOf(staged.take(1), staged + staged[0], staged.reversed(), listOf(staged[0], altered))) {
        bad { load(pages) }
        verify(committed.isEmpty())
    }
    bad { load(staged, k = ByteArray(32)) }
    bad { load(staged, accountId = id(99)) }
    bad { load(staged, k = mac) }
    val invalid = EncryptedHistoryEnvelope.seal(key, account, "{}".toByteArray())
    val invalidPages = listOf(staged[0], invalid)
    bad { load(invalidPages, HistoryManifest.create(mac, account, snapshot, invalidPages.asSequence())) }
    verify(committed.isEmpty())
    verify(load(staged) == snapshot && committed == listOf(a,b))
    verify(load(staged) == snapshot && committed == listOf(a,b))
    val conflict = EncryptedHistoryEnvelope.seal(key, account, HistoryWire.encode(listOf(a.copy(text = "changed"))))
    val new = EncryptedHistoryEnvelope.seal(key, account, HistoryWire.encode(listOf(b.copy(id = id(8), clock = 3))))
    val conflicting = listOf(new, conflict)
    bad { load(conflicting, HistoryManifest.create(mac, account, snapshot, conflicting.asSequence())) }
    verify(committed == listOf(a,b))
    val empty = HistorySnapshot.export(key, mac, account, snapshot, emptySequence()) { error("no page") }
    verify(load(emptyList(), empty) == snapshot && committed == listOf(a,b))
    bad { HistorySnapshot.export(key, mac, account, snapshot, sequenceOf(emptyList())) {} }
    bad { HistorySnapshot.export(key, mac, account, snapshot, sequenceOf(List(65) { a })) {} }
    bad { HistorySnapshot.export(key, key, account, snapshot, emptySequence()) {} }
    bad { HistorySnapshot.export(key, mac, account, snapshot, sequenceOf(listOf(a))) { error("staging failed") } }
    val independent = mutableListOf<ByteArray>()
    val safe = HistorySnapshot.export(key, mac, account, snapshot, sequenceOf(listOf(a))) { independent.add(it.copyOf()); it.fill(0) }
    verify(HistoryManifest.verify(mac, account, safe, independent.asSequence()) == snapshot)
    println("HistorySnapshot: $checks checks passed (transaction fixture)")
}
