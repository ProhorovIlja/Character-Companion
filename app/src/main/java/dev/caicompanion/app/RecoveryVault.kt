package dev.caicompanion.app

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Internal local root-secret store. Explicit install, never automatic replacement. */
class RecoveryVault(root: Path, private val account: String, private val wrappingKey: (Boolean) -> SecretKey) {
    companion object {
        private val lock = Any()
        private val magic = "MANDYV01".toByteArray(Charsets.US_ASCII)
        private const val RECORD_BYTES = 68
    }
    private val dir = root.resolve(NativeConversation.canonicalId(account).toString())
    private val file = dir.resolve("recovery.bin")
    private val aad = magic + account.toByteArray(Charsets.US_ASCII)
    init { Files.createDirectories(dir) }
    private fun <T> exclusive(action: () -> T): T = synchronized(lock) {
        FileChannel.open(dir.resolve("vault.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            channel.lock().use { action() }
        }
    }
    /** Accept only an explicitly supplied recovery code; never create a root in this method. */
    fun install(code: String) = exclusive {
        check(Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) { "Recovery record already exists or is unavailable" }
        ContinuityKeys.decode(code).use { recovery ->
            require(recovery.account == account) { "Recovery account mismatch" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, wrappingKey(true))
            require(cipher.iv.size == 12)
            cipher.updateAAD(aad)
            val record = magic + cipher.iv + cipher.doFinal(recovery.secret)
            check(record.size == RECORD_BYTES)
            val tmp = Files.createTempFile(dir, ".recovery-", ".tmp")
            try {
                FileOutputStream(tmp.toFile()).use { stream -> stream.write(record); stream.fd.sync() }
                check(Files.notExists(file, LinkOption.NOFOLLOW_LINKS))
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE)
            } finally { Files.deleteIfExists(tmp) }
        }
    }
    /** Missing/corrupt records or wrapping keys fail closed. No key generation on read. */
    fun load(): ContinuityKeys.Recovery = exclusive {
        check(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) == RECORD_BYTES.toLong()) { "Recovery record unavailable" }
        val record = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).use { input ->
            val data = ByteArray(RECORD_BYTES)
            java.io.DataInputStream(input).readFully(data)
            require(input.read() == -1)
            data
        }
        require(record.copyOfRange(0, 8).contentEquals(magic))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(false), GCMParameterSpec(128, record.copyOfRange(8, 20)))
        cipher.updateAAD(aad)
        val secret = cipher.doFinal(record, 20, 48)
        if (secret.size != 32) { secret.fill(0); error("Invalid recovery record") }
        ContinuityKeys.Recovery(account, secret)
    }
}
