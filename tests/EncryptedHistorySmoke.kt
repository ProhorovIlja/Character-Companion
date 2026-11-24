package dev.caicompanion.app

import java.nio.file.Files
import java.nio.file.Path

/** Fixture key only. Never use this key outside tests. */
fun main(args: Array<String>) {
    require(args.size == 3)
    val key = ByteArray(32) { it.toByte() }
    val account = "00000000-0000-0000-0000-000000000001"
    val plain = Files.readAllBytes(Path.of(args[0]))
    val python = Files.readAllBytes(Path.of(args[1]))
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    verify(EncryptedHistoryEnvelope.open(key, account, python).contentEquals(plain))
    val sealed = EncryptedHistoryEnvelope.seal(key, account, plain)
    verify(sealed.size == plain.size + 36)
    verify(EncryptedHistoryEnvelope.open(key, account, sealed).contentEquals(plain))
    verify(!sealed.contentEquals(EncryptedHistoryEnvelope.seal(key, account, plain)))
    for (index in listOf(0, 8, 20, sealed.lastIndex)) {
        bad { EncryptedHistoryEnvelope.open(key, account, sealed.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }) }
    }
    bad { EncryptedHistoryEnvelope.open(ByteArray(32), account, sealed) }
    bad { EncryptedHistoryEnvelope.open(key, "00000000-0000-0000-0000-000000000002", sealed) }
    bad { EncryptedHistoryEnvelope.open(key, account, sealed.copyOf(sealed.size - 1)) }
    bad { EncryptedHistoryEnvelope.open(key, account, ByteArray(EncryptedHistoryEnvelope.MAX_ENVELOPE + 1)) }
    bad { EncryptedHistoryEnvelope.seal(key, account, ByteArray(0)) }
    bad { EncryptedHistoryEnvelope.seal(ByteArray(31), account, plain) }
    bad { EncryptedHistoryEnvelope.seal(key, "not-an-account", plain) }
    val large = ByteArray(EncryptedHistoryEnvelope.MAX_PLAINTEXT) { (it % 251).toByte() }
    verify(EncryptedHistoryEnvelope.open(key, account, EncryptedHistoryEnvelope.seal(key, account, large)).contentEquals(large))
    Files.write(Path.of(args[2]), sealed)
    println("EncryptedHistoryEnvelope: $checks checks passed")
}
