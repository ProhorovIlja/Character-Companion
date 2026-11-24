package dev.caicompanion.app

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Transport envelope only. Caller must validate decrypted history before importing it. */
object EncryptedHistoryEnvelope {
    const val MAX_PLAINTEXT = 4 * 1024 * 1024
    const val MAX_ENVELOPE = MAX_PLAINTEXT + 36
    private val magic = "MANDYH01".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()
    private fun binding(key: ByteArray, account: String): ByteArray {
        require(key.size == 32)
        NativeConversation.canonicalId(account)
        return account.toByteArray(Charsets.US_ASCII)
    }
    fun seal(key: ByteArray, account: String, data: ByteArray): ByteArray {
        val accountBytes = binding(key, account)
        require(data.size in 1..MAX_PLAINTEXT)
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = magic + nonce
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header + accountBytes)
        return header + cipher.doFinal(data)
    }
    fun open(key: ByteArray, account: String, envelope: ByteArray): ByteArray {
        val accountBytes = binding(key, account)
        require(envelope.size in 37..MAX_ENVELOPE && envelope.copyOfRange(0, 8).contentEquals(magic))
        val header = envelope.copyOfRange(0, 20)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, header.copyOfRange(8, 20)))
        cipher.updateAAD(header + accountBytes)
        return cipher.doFinal(envelope, 20, envelope.size - 20)
    }
}
