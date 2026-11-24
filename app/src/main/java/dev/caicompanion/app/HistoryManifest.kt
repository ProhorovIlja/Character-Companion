package dev.caicompanion.app

import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Authenticated ordered descriptors, separate from encrypted history payloads. */
object HistoryManifest {
    const val MAX_CHUNKS = 4096
    const val MAX_BYTES = 80 + 36 * MAX_CHUNKS
    private val magic = "MANDYM01".toByteArray(Charsets.US_ASCII)
    private val chunkMagic = "MANDYH01".toByteArray(Charsets.US_ASCII)
    private fun binding(key: ByteArray, account: String): ByteArray {
        require(key.size == 32)
        NativeConversation.canonicalId(account)
        return account.toByteArray(Charsets.US_ASCII)
    }
    private fun mac(key: ByteArray, body: ByteArray, account: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256")); update(body); doFinal(account)
    }
    private fun descriptor(chunk: ByteArray): ByteArray {
        require(chunk.size in 37..EncryptedHistoryEnvelope.MAX_ENVELOPE && chunk.copyOfRange(0, 8).contentEquals(chunkMagic))
        return ByteBuffer.allocate(36).putInt(chunk.size).put(MessageDigest.getInstance("SHA-256").digest(chunk)).array()
    }
    fun create(key: ByteArray, account: String, snapshot: String, chunks: Sequence<ByteArray>): ByteArray {
        val binding = binding(key, account)
        NativeConversation.canonicalId(snapshot)
        val rows = mutableListOf<ByteArray>()
        for (chunk in chunks) { require(rows.size < MAX_CHUNKS); rows.add(descriptor(chunk)) }
        val body = ByteBuffer.allocate(48 + rows.size * 36).put(magic).put(snapshot.toByteArray(Charsets.US_ASCII)).putInt(rows.size)
        rows.forEach { body.put(it) }
        return body.array() + mac(key, body.array(), binding)
    }
    /** No mutation: verifies the full ordered sequence before reporting success. */
    fun verify(key: ByteArray, account: String, manifest: ByteArray, chunks: Sequence<ByteArray>): String {
        val binding = binding(key, account)
        require(manifest.size in 80..MAX_BYTES && manifest.copyOfRange(0, 8).contentEquals(magic))
        val body = manifest.copyOfRange(0, manifest.size - 32)
        require(MessageDigest.isEqual(manifest.copyOfRange(body.size, manifest.size), mac(key, body, binding))) { "Manifest authentication failed" }
        val snapshot = body.copyOfRange(8, 44).toString(Charsets.US_ASCII)
        NativeConversation.canonicalId(snapshot)
        val buf = ByteBuffer.wrap(body); buf.position(44)
        val count = buf.int
        require(count in 0..MAX_CHUNKS && body.size == 48 + 36 * count)
        val input = chunks.iterator()
        repeat(count) {
            val expected = ByteArray(36).also { buf.get(it) }
            require(ByteBuffer.wrap(expected).int in 37..EncryptedHistoryEnvelope.MAX_ENVELOPE)
            require(input.hasNext() && MessageDigest.isEqual(expected, descriptor(input.next()))) { "Missing or modified chunk" }
        }
        require(!input.hasNext()) { "Extra chunk" }
        return snapshot
    }
}
