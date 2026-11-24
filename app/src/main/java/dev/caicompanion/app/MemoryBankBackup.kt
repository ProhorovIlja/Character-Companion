package dev.caicompanion.app

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.security.MessageDigest

/** Portable plaintext snapshot; integrity check, not encryption or authentication. */
object MemoryBankBackup {
    private const val MAGIC = 0x43434d58 // CCMX
    private const val VERSION = 1
    const val MAX_PAYLOAD = 12 + MemoryBank.MAX_ENTRIES *
        (12 + 3 * (MemoryBank.MAX_PROFILE + MemoryBank.MAX_TITLE + MemoryBank.MAX_BODY))
    data class Merge(val entries: List<MemoryBank.Entry>, val added: Int, val duplicates: Int, val conflicts: Int)

    fun encode(entries: List<MemoryBank.Entry>): ByteArray {
        val payload = ByteArrayOutputStream().also { MemoryBank.write(entries, it) }.toByteArray()
        require(payload.size in 12..MAX_PAYLOAD)
        return ByteArrayOutputStream(payload.size + 44).also { buffer ->
            DataOutputStream(buffer).apply {
                writeInt(MAGIC); writeInt(VERSION); writeInt(payload.size)
                write(MessageDigest.getInstance("SHA-256").digest(payload))
                write(payload); flush()
            }
        }.toByteArray()
    }

    fun decode(input: InputStream): List<MemoryBank.Entry>? = try {
        val stream = DataInputStream(input)
        require(stream.readInt() == MAGIC && stream.readInt() == VERSION)
        val size = stream.readInt()
        require(size in 12..MAX_PAYLOAD)
        val digest = ByteArray(32).also { stream.readFully(it) }
        val payload = ByteArray(size).also { stream.readFully(it) }
        require(stream.read() == -1)
        require(MessageDigest.isEqual(digest, MessageDigest.getInstance("SHA-256").digest(payload)))
        MemoryBank.read(payload.inputStream())
    } catch (_: Exception) { null }

    /** Existing entries always win. Overflow rejects the entire merge. */
    fun merge(current: List<MemoryBank.Entry>, incoming: List<MemoryBank.Entry>): Merge? {
        if (!MemoryBank.validBank(current) || !MemoryBank.validBank(incoming)) return null
        val existing = current.associateBy(MemoryBank::key)
        val additions = mutableListOf<MemoryBank.Entry>()
        var duplicates = 0
        var conflicts = 0
        for (entry in incoming) {
            val old = existing[MemoryBank.key(entry)]
            when {
                old == null -> additions.add(entry)
                old.body == entry.body -> duplicates++
                else -> conflicts++
            }
        }
        if (current.size + additions.size > MemoryBank.MAX_ENTRIES) return null
        return Merge(current + additions, additions.size, duplicates, conflicts)
    }
}
