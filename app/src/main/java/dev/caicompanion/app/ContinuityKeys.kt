package dev.caicompanion.app

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** High-entropy recovery secret only, never a user password. No persistent key storage. */
object ContinuityKeys {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()
    private const val PREFIX = "MANDY1."
    class Recovery internal constructor(val account: String, val secret: ByteArray) : AutoCloseable {
        override fun close() { secret.fill(0) }
    }
    class Keys internal constructor(val history: ByteArray, val manifest: ByteArray) : AutoCloseable {
        override fun close() { history.fill(0); manifest.fill(0) }
    }
    fun generate(account: String): Recovery {
        NativeConversation.canonicalId(account)
        return Recovery(account, ByteArray(32).also(random::nextBytes))
    }
    /** The returned string is a secret. Never log, upload or place on a shared clipboard. */
    fun encode(account: String, secret: ByteArray): String {
        NativeConversation.canonicalId(account)
        require(secret.size == 32)
        return PREFIX + account + "." + encoder.encodeToString(secret)
    }
    fun decode(code: String): Recovery {
        require(code.length == 87 && code.startsWith(PREFIX) && code[43] == '.') { "Invalid recovery format" }
        val account = code.substring(7, 43)
        NativeConversation.canonicalId(account)
        val encoded = code.substring(44)
        require(encoded.matches(Regex("[A-Za-z0-9_-]{43}"))) { "Invalid recovery secret" }
        val secret = decoder.decode(encoded)
        if (secret.size != 32 || encoder.encodeToString(secret) != encoded) {
            secret.fill(0); error("Noncanonical recovery secret")
        }
        return Recovery(account, secret)
    }
    private fun hmac(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); doFinal(data)
    }
    /** RFC 5869 extract and single-block expand; separate labels and account binding. */
    fun derive(account: String, secret: ByteArray): Keys {
        NativeConversation.canonicalId(account)
        require(secret.size == 32)
        val salt = ("MandyAI continuity v1:" + account).toByteArray(Charsets.US_ASCII)
        val prk = hmac(salt, secret)
        try {
            fun expand(label: String) = hmac(prk, label.toByteArray(Charsets.US_ASCII) + byteArrayOf(1))
            return Keys(expand("history-aes-256-gcm"), expand("manifest-hmac-sha256"))
        } finally { prk.fill(0) }
    }
}
