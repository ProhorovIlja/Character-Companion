package dev.caicompanion.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Local encrypted history-only container. No network, key storage or automatic backups. */
object HistoryBackup {
    const val MAX_FILE_BYTES = 512L * 1024 * 1024
    private val magic = "MANDYB01".toByteArray(Charsets.US_ASCII)
    private fun headerSize(manifest: ByteArray): Long {
        require(manifest.size in 80..HistoryManifest.MAX_BYTES)
        return 12L + manifest.size
    }

    /** Explicitly replaces target only after the authenticated container is fully written. */
    fun write(target: Path, manifestKey: ByteArray, account: String, manifest: ByteArray,
              chunks: Sequence<ByteArray>): String {
        val path = target.toAbsolutePath()
        val tmp = Files.createTempFile(path.parent, ".mandy-backup-", ".tmp")
        try {
            val snapshot = FileOutputStream(tmp.toFile()).use { stream ->
                val out = DataOutputStream(stream.buffered())
                val descriptor = manifest.copyOf()
                var total = headerSize(descriptor)
                out.write(magic); out.writeInt(descriptor.size); out.write(descriptor)
                val id = HistoryManifest.consumeVerified(manifestKey, account, descriptor, chunks) { chunk ->
                    total += 4L + chunk.size
                    require(total <= MAX_FILE_BYTES) { "Backup exceeds file limit" }
                    out.writeInt(chunk.size); out.write(chunk)
                }
                out.flush(); stream.fd.sync()
                id
            }
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return snapshot
        } finally { Files.deleteIfExists(tmp) }
    }

    /** Stage encrypted pages privately; capture must return only after consistent export succeeds. */
    fun capture(target: Path, privateRoot: Path, manifestKey: ByteArray, account: String,
                export: ((ByteArray) -> Unit) -> ByteArray): String {
        val dir = Files.createTempDirectory(privateRoot, ".mandy-stage-")
        val pages = mutableListOf<Path>()
        var total = 0L
        try {
            val manifest = export { chunk ->
                require(pages.size < HistoryManifest.MAX_CHUNKS && chunk.size in 37..EncryptedHistoryEnvelope.MAX_ENVELOPE)
                total += 4L + chunk.size
                require(total <= MAX_FILE_BYTES) { "Backup exceeds staging limit" }
                val page = dir.resolve(pages.size.toString())
                pages.add(page)
                Files.write(page, chunk)
            }
            return write(target, manifestKey, account, manifest, pages.asSequence().map { page ->
                require(Files.size(page) in 37L..EncryptedHistoryEnvelope.MAX_ENVELOPE.toLong())
                Files.readAllBytes(page)
            })
        } finally {
            pages.forEach { Files.deleteIfExists(it) }
            Files.deleteIfExists(dir)
        }
    }

    /** Caller must use one transaction for all merge callbacks, including late EOF validation. */
    fun restore(source: Path, historyKey: ByteArray, manifestKey: ByteArray, account: String,
                merge: (List<NativeConversation.Event>) -> Unit): String {
        require(Files.isRegularFile(source) && Files.size(source) in 92L..MAX_FILE_BYTES)
        return DataInputStream(Files.newInputStream(source).buffered()).use { input ->
            val marker = ByteArray(8).also(input::readFully)
            require(marker.contentEquals(magic))
            val size = input.readInt()
            require(size in 80..HistoryManifest.MAX_BYTES)
            val manifest = ByteArray(size).also(input::readFully)
            var total = headerSize(manifest)
            val chunks = sequence {
                while (true) {
                    val first = input.read()
                    if (first == -1) break
                    val length = (first shl 24) or (input.readUnsignedByte() shl 16) or
                        (input.readUnsignedByte() shl 8) or input.readUnsignedByte()
                    require(length in 37..EncryptedHistoryEnvelope.MAX_ENVELOPE)
                    total += 4L + length
                    require(total <= MAX_FILE_BYTES)
                    yield(ByteArray(length).also(input::readFully))
                }
            }
            HistorySnapshot.import(historyKey, manifestKey, account, manifest, chunks, merge)
        }
    }
}
