package dev.caicompanion.app

import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    val key = ByteArray(32) { (it + 32).toByte() }
    val account = "00000000-0000-0000-0000-000000000001"
    val snapshot = "00000000-0000-0000-0000-000000000063"
    val pages = listOf(Files.readAllBytes(Path.of(args[0])), Files.readAllBytes(Path.of(args[1])))
    val manifest = Files.readAllBytes(Path.of(args[2]))
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    verify(HistoryManifest.verify(key, account, manifest, pages.asSequence()) == snapshot)
    val own = HistoryManifest.create(key, account, snapshot, pages.asSequence())
    verify(own.contentEquals(manifest))
    for (i in listOf(0, 8, 44, 48, own.lastIndex)) {
        bad { HistoryManifest.verify(key, account, own.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }, pages.asSequence()) }
    }
    bad { HistoryManifest.verify(ByteArray(32), account, own, pages.asSequence()) }
    bad { HistoryManifest.verify(key, snapshot, own, pages.asSequence()) }
    bad { HistoryManifest.verify(key, account, own, pages.take(1).asSequence()) }
    bad { HistoryManifest.verify(key, account, own, (pages + pages).asSequence()) }
    bad { HistoryManifest.verify(key, account, own, pages.reversed().asSequence()) }
    bad { HistoryManifest.verify(key, account, own, sequenceOf(pages[0], pages[1].copyOf().also { it[it.lastIndex] = 0; it[20] = (it[20].toInt() xor 1).toByte() })) }
    bad { HistoryManifest.create(key, account, snapshot, generateSequence { pages[0] }.take(4097)) }
    bad { HistoryManifest.create(key, account, snapshot, sequenceOf(byteArrayOf(1))) }
    val empty = HistoryManifest.create(key, account, snapshot, emptySequence())
    verify(HistoryManifest.verify(key, account, empty, emptySequence()) == snapshot)
    Files.write(Path.of(args[3]), own)
    println("HistoryManifest: $checks checks passed")
}
