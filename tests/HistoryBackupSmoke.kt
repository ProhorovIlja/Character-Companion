package dev.caicompanion.app

import java.nio.file.Files
import java.util.UUID

fun main() {
    fun id(n: Long) = UUID(0,n).toString()
    val root = Files.createTempDirectory("mandy-backup-smoke")
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    try {
        val key = ByteArray(32) { it.toByte() }
        val mac = ByteArray(32) { (it + 32).toByte() }
        val account = id(1); val snapshot = id(2)
        val a = NativeConversation.Event(id(3), id(4), id(5), id(6), 1, "user", "text", "Привет 🌙")
        val b = a.copy(id = id(7), clock = 2, text = "Second")
        val pages = mutableListOf<ByteArray>()
        val manifest = HistorySnapshot.export(key, mac, account, snapshot, sequenceOf(listOf(a), listOf(b))) { pages.add(it) }
        val target = root.resolve("history.mandy")
        verify(HistoryBackup.write(target, mac, account, manifest, pages.asSequence()) == snapshot)
        val original = Files.readAllBytes(target)
        verify(original.copyOfRange(0, 8).toString(Charsets.US_ASCII) == "MANDYB01")
        verify(!original.toString(Charsets.UTF_8).contains(a.text))
        var committed = emptyList<NativeConversation.Event>()
        fun restore(path: java.nio.file.Path = target, k: ByteArray = key, who: String = account): String {
            var draft = committed
            val result = HistoryBackup.restore(path, k, mac, who) { draft = HistoryWire.merge(draft + it) }
            committed = draft
            return result
        }
        verify(restore() == snapshot && committed == listOf(a,b))
        verify(restore() == snapshot && committed == listOf(a,b))
        val corrupt = root.resolve("bad.mandy")
        for (bytes in listOf(original.copyOf(original.size - 1), original + byteArrayOf(1), original + byteArrayOf(0,0,0,0), original.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }, original.copyOf().also { it[0] = 0 }, original.copyOf().also { it[8] = 127 })) {
            Files.write(corrupt, bytes)
            committed = emptyList()
            bad { restore(corrupt) }
            verify(committed.isEmpty())
        }
        bad { restore(k = ByteArray(32)) }
        bad { restore(who = id(99)) }
        bad { HistoryBackup.write(target, mac, account, manifest, pages.take(1).asSequence()) }
        verify(Files.readAllBytes(target).contentEquals(original))
        bad { HistoryBackup.write(target, mac, account, manifest, pages.reversed().asSequence()) }
        verify(Files.readAllBytes(target).contentEquals(original))
        bad { HistoryBackup.capture(target, root, mac, account) { stage -> stage(pages[0]); error("capture failed") } }
        verify(Files.readAllBytes(target).contentEquals(original))
        verify(Files.list(root).use { it.count() } == 2L)
        verify(HistoryBackup.capture(target, root, mac, account) { stage -> pages.forEach(stage); manifest } == snapshot)
        verify(Files.readAllBytes(target).contentEquals(original))
        verify(Files.list(root).use { it.count() } == 2L)
        val empty = HistorySnapshot.export(key, mac, account, snapshot, emptySequence()) { error("unexpected") }
        verify(HistoryBackup.write(target, mac, account, empty, emptySequence()) == snapshot)
        verify(restore() == snapshot && committed.isEmpty())
        println("HistoryBackup: $checks checks passed")
    } finally { root.toFile().deleteRecursively() }
}
