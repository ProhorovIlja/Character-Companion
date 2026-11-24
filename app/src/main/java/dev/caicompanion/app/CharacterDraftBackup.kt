package dev.caicompanion.app

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Explicit portable backup, not a standard character card or an authenticated signature. */
object CharacterDraftBackup {
    private const val MAGIC = 0x4343424b // CCBK
    private const val VERSION = 1
    private const val DIGEST_BYTES = 32

    fun isBackup(input: BufferedInputStream): Boolean {
        input.mark(4)
        return try { DataInputStream(input).readInt() == MAGIC }
        catch (_: java.io.EOFException) { false }
        finally { input.reset() }
    }

    fun encode(draft: CharacterDraftPolicy.Draft): ByteArray {
        val payload = ByteArrayOutputStream().also { CharacterDraftCodec.write(draft, it) }.toByteArray()
        require(payload.size.toLong() in 1..CharacterDraftCodec.MAX_FILE_BYTES)
        return ByteArrayOutputStream(payload.size + 12 + DIGEST_BYTES).also { out ->
            DataOutputStream(out).apply {
                writeInt(MAGIC)
                writeInt(VERSION)
                writeInt(payload.size)
                write(MessageDigest.getInstance("SHA-256").digest(payload))
                write(payload)
                flush()
            }
        }.toByteArray()
    }

    fun decode(input: InputStream): CharacterDraftPolicy.Draft? = try {
        val stream = DataInputStream(input)
        require(stream.readInt() == MAGIC && stream.readInt() == VERSION)
        val size = stream.readInt()
        require(size.toLong() in 1..CharacterDraftCodec.MAX_FILE_BYTES)
        val digest = ByteArray(DIGEST_BYTES).also { stream.readFully(it) }
        val payload = ByteArray(size).also { stream.readFully(it) }
        require(stream.read() == -1)
        require(MessageDigest.isEqual(digest, MessageDigest.getInstance("SHA-256").digest(payload)))
        CharacterDraftCodec.read(payload.inputStream())
    } catch (_: Exception) { null }
}
