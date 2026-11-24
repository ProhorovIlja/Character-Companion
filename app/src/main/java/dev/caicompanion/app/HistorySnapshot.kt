package dev.caicompanion.app

/** Local staged bytes only. No network or key persistence. */
object HistorySnapshot {
    private fun keys(historyKey: ByteArray, manifestKey: ByteArray) {
        require(historyKey.size == 32 && manifestKey.size == 32 && !historyKey.contentEquals(manifestKey))
    }
    /** Stage encrypted pages only. Publish the staging area only after successful return. */
    fun export(historyKey: ByteArray, manifestKey: ByteArray, account: String, snapshot: String,
               pages: Sequence<List<NativeConversation.Event>>, stage: (ByteArray) -> Unit): ByteArray {
        keys(historyKey, manifestKey)
        val chunks = pages.mapIndexed { index, rows ->
            require(index < HistoryManifest.MAX_CHUNKS)
            require(rows.isNotEmpty() && rows.size <= 64)
            val chunk = EncryptedHistoryEnvelope.seal(historyKey, account, HistoryWire.encode(rows))
            stage(chunk.copyOf())
            chunk
        }
        return HistoryManifest.create(manifestKey, account, snapshot, chunks)
    }
    /** Must run inside a single caller-owned transaction covering all callbacks. */
    fun import(historyKey: ByteArray, manifestKey: ByteArray, account: String, manifest: ByteArray,
               chunks: Sequence<ByteArray>, merge: (List<NativeConversation.Event>) -> Unit): String {
        keys(historyKey, manifestKey)
        return HistoryManifest.consumeVerified(manifestKey, account, manifest, chunks) { chunk ->
            val data = EncryptedHistoryEnvelope.open(historyKey, account, chunk)
            merge(HistoryWire.decode(data))
        }
    }
}
