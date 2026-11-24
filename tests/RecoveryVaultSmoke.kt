package dev.caicompanion.app

import java.nio.file.Files
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

fun main() {
    fun id(n: Long) = UUID(0, n).toString()
    val root = Files.createTempDirectory("mandy-vault")
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    try {
        val account = id(1)
        val secret = ByteArray(32) { it.toByte() }
        val code = ContinuityKeys.encode(account, secret)
        val key = SecretKeySpec(ByteArray(32) { (it + 32).toByte() }, "AES")
        var create = 0; var read = 0
        val vault = RecoveryVault(root, account) { if (it) create++ else read++; key }
        val path = root.resolve(account).resolve("recovery.bin")
        bad { vault.load() }
        verify(create == 0 && read == 0)
        bad { vault.install(ContinuityKeys.encode(id(2), secret)) }
        bad { vault.install("invalid") }
        verify(create == 0 && Files.notExists(path))
        vault.install(code)
        verify(create == 1 && Files.size(path) == 68L)
        val bytes = Files.readAllBytes(path)
        verify(!bytes.toList().windowed(32).any { it == secret.toList() })
        verify(vault.load().use { it.account == account && it.secret.contentEquals(secret) })
        verify(read == 1 && create == 1)
        verify(RecoveryVault(root, account) { check(!it); key }.load().use { it.secret.contentEquals(secret) })
        bad { vault.install(ContinuityKeys.encode(account, ByteArray(32))) }
        verify(create == 1 && Files.readAllBytes(path).contentEquals(bytes))
        bad { RecoveryVault(root, account) { check(!it); error("missing key") }.load() }
        bad { RecoveryVault(root, account) { check(!it); SecretKeySpec(ByteArray(32), "AES") }.load() }
        for (index in listOf(0, 8, 20, 67)) {
            Files.write(path, bytes.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() })
            bad { vault.load() }
            bad { vault.install(code) }
        }
        Files.write(path, bytes.copyOf(67))
        bad { vault.load() }
        Files.write(path, bytes + byteArrayOf(1))
        bad { vault.load() }
        Files.write(path, bytes)
        val other = RecoveryVault(root, id(2)) { key }
        val otherPath = root.resolve(id(2)).resolve("recovery.bin")
        Files.copy(path, otherPath)
        bad { other.load() }
        verify(vault.load().use { it.secret.contentEquals(secret) })
        val failed = RecoveryVault(root, id(3)) { error("unavailable wrapping key") }
        bad { failed.install(ContinuityKeys.encode(id(3), secret)) }
        verify(Files.notExists(root.resolve(id(3)).resolve("recovery.bin")))
        verify(Files.list(root.resolve(account)).use { it.count() } == 2L)
        println("RecoveryVault: $checks checks passed (software wrapping key)")
    } finally { root.toFile().deleteRecursively() }
}
