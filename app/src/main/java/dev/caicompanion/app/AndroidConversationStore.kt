package dev.caicompanion.app

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.IOException

/** Private local database, independent of legacy browser sessions and draft stores. */
class AndroidConversationStore(context: Context, private val account: String) :
    SQLiteOpenHelper(context.applicationContext, "mandy_history_v1.db", null, 1), NativeConversation.History {
    init {
        NativeConversation.canonicalId(account)
        setWriteAheadLoggingEnabled(true)
    }
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE vault (singleton INTEGER PRIMARY KEY CHECK(singleton=1), account TEXT NOT NULL, clock INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE chats (id TEXT PRIMARY KEY, character TEXT NOT NULL, UNIQUE(id, character))")
        db.execSQL("CREATE TABLE messages (id TEXT PRIMARY KEY, device TEXT NOT NULL, character TEXT NOT NULL, chat TEXT NOT NULL, clock INTEGER NOT NULL, role TEXT NOT NULL, mode TEXT NOT NULL, text TEXT NOT NULL, FOREIGN KEY(chat, character) REFERENCES chats(id, character))")
        db.execSQL("CREATE INDEX messages_chat_order ON messages(chat, character, clock, id)")
        db.execSQL("CREATE INDEX messages_export_order ON messages(clock, id)")
        db.execSQL("INSERT INTO vault VALUES (1, ?, 0)", arrayOf(account))
    }
    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.rawQuery("SELECT account FROM vault WHERE singleton=1", null).use {
            check(it.moveToFirst() && it.getString(0) == account) { "History account mismatch" }
        }
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        error("Unsupported history schema migration")
    }
    private fun <T> write(action: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        try {
            val result = action(db)
            db.setTransactionSuccessful()
            return result
        } finally { db.endTransaction() }
    }
    private fun event(c: Cursor) = NativeConversation.Event(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4), c.getString(5), c.getString(6), c.getString(7))
    private val columns = "id, device, character, chat, clock, role, mode, text"
    private fun find(db: SQLiteDatabase, id: String): NativeConversation.Event? =
        db.rawQuery("SELECT $columns FROM messages WHERE id=?", arrayOf(id)).use { if (it.moveToFirst()) event(it) else null }
    override fun find(id: String): NativeConversation.Event? {
        NativeConversation.canonicalId(id)
        return find(readableDatabase, id)
    }
    private fun clock(db: SQLiteDatabase): Long = db.rawQuery("SELECT clock FROM vault WHERE singleton=1", null).use {
        check(it.moveToFirst())
        val last = it.getLong(0)
        check(last >= 0 && last < NativeConversation.MAX_CLOCK) { "Invalid or exhausted clock" }
        last + 1
    }
    private fun insert(db: SQLiteDatabase, event: NativeConversation.Event): NativeConversation.Event {
        val existing = find(db, event.id)
        if (existing != null) {
            require(existing == event) { "Conflicting message ID" }
            return existing
        }
        val owner = db.rawQuery("SELECT character FROM chats WHERE id=?", arrayOf(event.chat)).use { if (it.moveToFirst()) it.getString(0) else null }
        if (owner == null) db.execSQL("INSERT INTO chats VALUES (?, ?)", arrayOf(event.chat, event.character))
        else require(owner == event.character) { "Conflicting chat owner" }
        db.execSQL("INSERT INTO messages ($columns) VALUES (?,?,?,?,?,?,?,?)", arrayOf(event.id, event.device, event.character, event.chat, event.clock, event.role, event.mode, event.text))
        db.execSQL("UPDATE vault SET clock=max(clock, ?) WHERE singleton=1", arrayOf(event.clock))
        return event
    }
    override fun appendUser(id: String, device: String, character: String, chat: String, mode: String, text: String): NativeConversation.Event = write { db ->
        val existing = find(db, id)
        insert(db, NativeConversation.Event(id, device, character, chat, existing?.clock ?: clock(db), "user", mode, text))
    }
    private fun page(db: SQLiteDatabase, character: String, chat: String, limit: Int, before: Pair<Long, String>?): List<NativeConversation.Event> {
        NativeConversation.canonicalId(character)
        NativeConversation.canonicalId(chat)
        require(limit in 1..200)
        val args = mutableListOf(chat, character)
        var sql = "SELECT $columns FROM messages WHERE chat=? AND character=?"
        if (before != null) {
            require(before.first in 1..NativeConversation.MAX_CLOCK)
            NativeConversation.canonicalId(before.second)
            sql += " AND (clock < ? OR (clock = ? AND id < ?))"
            args.addAll(listOf(before.first.toString(), before.first.toString(), before.second))
        }
        args.add(limit.toString())
        return db.rawQuery("$sql ORDER BY clock DESC,id DESC LIMIT ?", args.toTypedArray()).use { c ->
            buildList { while (c.moveToNext()) add(event(c)) }
        }
    }
    fun page(character: String, chat: String, limit: Int = 50, before: Pair<Long, String>? = null) = page(readableDatabase, character, chat, limit, before)
    override fun recent(character: String, chat: String, limit: Int) = page(character, chat, limit)
    /** Authenticate and validate a whole batch before the atomic database write. */
    fun importEncrypted(key: ByteArray, envelope: ByteArray): Int {
        val events = HistoryWire.decode(EncryptedHistoryEnvelope.open(key, account, envelope))
        return write { db ->
            var count = 0
            for (event in events) {
                val existing = find(db, event.id)
                if (existing == null) { insert(db, event); count++ }
                else require(existing == event) { "Conflicting message ID" }
            }
            count
        }
    }

    data class EncryptedPage(val envelope: ByteArray, val last: Pair<Long, String>)

    /** A single bounded page, not a complete authenticated snapshot.
     * Do not use a previous export cursor as a permanent sync watermark: imported older
     * events may precede it. Caller must serialize export with writes for complete traversal.
     */
    private fun exportRows(db: SQLiteDatabase, after: Pair<Long, String>?): List<NativeConversation.Event> {
        val args = mutableListOf<String>()
        var sql = "SELECT $columns FROM messages"
        if (after != null) {
            require(after.first in 1..NativeConversation.MAX_CLOCK)
            NativeConversation.canonicalId(after.second)
            sql += " WHERE (clock > ? OR (clock = ? AND id > ?))"
            args.addAll(listOf(after.first.toString(), after.first.toString(), after.second))
        }
        // 64 x maximum 16,000 UTF-16 units plus JSON overhead remains under 4 MiB.
        sql += " ORDER BY clock ASC,id ASC LIMIT 64"
        return db.rawQuery(sql, args.toTypedArray()).use { c ->
            buildList { while (c.moveToNext()) add(event(c)) }
        }
    }

    fun exportEncryptedPage(key: ByteArray, after: Pair<Long, String>? = null): EncryptedPage? {
        val rows = exportRows(readableDatabase, after)
        val last = rows.lastOrNull() ?: return null
        return EncryptedPage(EncryptedHistoryEnvelope.seal(key, account, HistoryWire.encode(rows)), last.clock to last.id)
    }

    data class SnapshotImport(val snapshot: String, val inserted: Int)

    /** Foreground, local staged ciphertext only. All pages merge or the transaction rolls back. */
    fun importSnapshot(historyKey: ByteArray, manifestKey: ByteArray, manifest: ByteArray,
                       chunks: Sequence<ByteArray>): SnapshotImport = write { db ->
        var count = 0
        val snapshot = HistorySnapshot.import(historyKey, manifestKey, account, manifest, chunks) { rows ->
            for (row in rows) {
                val existing = find(db, row.id)
                if (existing == null) { insert(db, row); count++ }
                else require(existing == row) { "Conflicting message ID" }
            }
        }
        SnapshotImport(snapshot, count)
    }

    /** Hold one transaction over the traversal. stage writes only unpublished local ciphertext.
     * Caller must discard staging on any failure and publish only after this method returns.
     * No network I/O or reentrant database operations in stage.
     */
    fun exportSnapshot(historyKey: ByteArray, manifestKey: ByteArray, snapshot: String,
                       stage: (ByteArray) -> Unit): ByteArray = write { db ->
        val pages = sequence {
            var after: Pair<Long, String>? = null
            while (true) {
                val rows = exportRows(db, after)
                val last = rows.lastOrNull() ?: break
                yield(rows)
                after = last.clock to last.id
            }
        }
        HistorySnapshot.export(historyKey, manifestKey, account, snapshot, pages, stage)
    }

    override fun commitReply(user: NativeConversation.Event, id: String, text: String): NativeConversation.Event = write { db ->
        require(user.role == "user" && id == NativeConversation.replyId(user.id))
        require(find(db, user.id) == user)
        val existing = find(db, id)
        if (existing != null) {
            require(existing.text == text) { "Conflicting concurrent reply" }
            NativeConversation.validateReply(user, existing)
        } else {
            if (page(db, user.character, user.chat, 1, null).firstOrNull() != user) throw IOException("Conversation advanced")
            insert(db, NativeConversation.Event(id, user.device, user.character, user.chat, clock(db), "character", user.mode, text))
        }
    }
}
