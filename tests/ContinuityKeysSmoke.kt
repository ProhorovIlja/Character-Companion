package dev.caicompanion.app

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

fun main(args: Array<String>) {
    fun id(n: Long) = UUID(0,n).toString()
    val account = id(1)
    val secret = ByteArray(32) { it.toByte() }
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    val code = ContinuityKeys.encode(account, secret)
    verify(code == Files.readString(Path.of(args[0])))
    val decoded = ContinuityKeys.decode(code)
    verify(decoded.account == account && decoded.secret.contentEquals(secret))
    val keys = ContinuityKeys.derive(account, decoded.secret)
    verify(keys.history.contentEquals(Files.readAllBytes(Path.of(args[1]))))
    verify(keys.manifest.contentEquals(Files.readAllBytes(Path.of(args[2]))))
    verify(!keys.history.contentEquals(keys.manifest))
    ContinuityKeys.derive(id(2), secret).use { verify(!it.history.contentEquals(keys.history) && !it.manifest.contentEquals(keys.manifest)) }
    ContinuityKeys.derive(account, ByteArray(32)).use { verify(!it.history.contentEquals(keys.history)) }
    ContinuityKeys.generate(account).use { first -> ContinuityKeys.generate(account).use { second ->
        verify(!first.secret.contentEquals(second.secret) && first.secret.size == 32)
    } }
    for (invalid in listOf(code + "=", " " + code, code.replace("MANDY1", "MANDY2"), code.replace('.', '/'), code.dropLast(1) + "9")) bad { ContinuityKeys.decode(invalid) }
    bad { ContinuityKeys.encode(account, ByteArray(31)) }
    bad { ContinuityKeys.derive("invalid", secret) }
    val e = NativeConversation.Event(id(3), id(4), id(5), id(6), 1, "user", "text", "Привет 🌙")
    val pages = mutableListOf<ByteArray>()
    val manifest = HistorySnapshot.export(keys.history, keys.manifest, account, id(7), sequenceOf(listOf(e))) { pages.add(it) }
    ContinuityKeys.decode(code).use { recovery -> ContinuityKeys.derive(recovery.account, recovery.secret).use { restored ->
        var events = emptyList<NativeConversation.Event>()
        verify(HistorySnapshot.import(restored.history, restored.manifest, account, manifest, pages.asSequence()) { events = it } == id(7))
        verify(events == listOf(e))
    } }
    ContinuityKeys.derive(account, ByteArray(32)).use { wrong -> bad { HistorySnapshot.import(wrong.history, wrong.manifest, account, manifest, pages.asSequence()) { error("unexpected") } } }
    decoded.close(); keys.close()
    verify(decoded.secret.all { it == 0.toByte() } && keys.history.all { it == 0.toByte() } && keys.manifest.all { it == 0.toByte() })
    println("ContinuityKeys: $checks checks passed")
}
