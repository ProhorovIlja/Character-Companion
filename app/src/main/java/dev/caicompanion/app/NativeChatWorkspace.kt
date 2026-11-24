package dev.caicompanion.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/** One instance per private account/device directory. No automatic sending or scheduling. */
class NativeChatWorkspace(root: Path, account: String, private val device: String) {
    data class Key(val character: String, val chat: String) {
        init { NativeConversation.canonicalId(character); NativeConversation.canonicalId(chat) }
    }
    data class Ticket(val key: Key, val nonce: UUID)
    data class Pending(val id: String, val text: String, val mode: String) {
        init {
            NativeConversation.canonicalId(id)
            NativeConversation.validateText(text)
            require(mode in setOf("text", "dictation", "call"))
        }
    }
    data class State(val draft: String = "", val pending: Pending? = null)
    private val dir = root.resolve(NativeConversation.canonicalId(account).toString())
        .resolve(NativeConversation.canonicalId(device).toString())
    private var active: Ticket? = null
    private val running = mutableSetOf<Key>()
    init {
        Files.createDirectories(dir)
        val file = dir.resolve("selected.bin")
        if (Files.exists(file)) active = read(file) { Ticket(Key(it.readUTF(), it.readUTF()), UUID.randomUUID()) }
    }
    private fun file(key: Key) = dir.resolve("${key.character}_${key.chat}.bin")
    private fun <T> read(path: Path, body: (DataInputStream) -> T): T {
        require(Files.size(path) in 1..100_000)
        return DataInputStream(Files.newInputStream(path).buffered()).use {
            require(it.readInt() == 0x4d414e31) { "Unsupported workspace record" }
            val result = body(it)
            require(it.read() == -1) { "Trailing workspace data" }
            result
        }
    }
    private fun write(path: Path, body: (DataOutputStream) -> Unit) {
        val temp = Files.createTempFile(dir, ".workspace-", ".tmp")
        try {
            java.io.FileOutputStream(temp.toFile()).use { stream ->
                val out = DataOutputStream(stream.buffered())
                out.writeInt(0x4d414e31)
                body(out)
                out.flush()
                stream.fd.sync()
            }
            require(Files.size(temp) <= 100_000)
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }
    private fun validateDraft(text: String) {
        require(text.length <= 16000)
        if (text.isNotBlank()) NativeConversation.validateText(text)
        else require(text.none { it.code < 32 && it !in "\t\r\n" })
    }
    private fun load(key: Key): State {
        val path = file(key)
        if (!Files.exists(path)) return State()
        return read(path) {
            require(Key(it.readUTF(), it.readUTF()) == key)
            val draft = it.readUTF().also(::validateDraft)
            val pending = when (it.readUnsignedByte()) {
                0 -> null
                1 -> Pending(it.readUTF(), it.readUTF(), it.readUTF())
                else -> error("Invalid pending marker")
            }
            State(draft, pending)
        }
    }
    private fun save(key: Key, state: State) {
        validateDraft(state.draft)
        write(file(key)) {
            it.writeUTF(key.character); it.writeUTF(key.chat); it.writeUTF(state.draft)
            val p = state.pending
            it.writeByte(if (p == null) 0 else 1)
            if (p != null) { it.writeUTF(p.id); it.writeUTF(p.text); it.writeUTF(p.mode) }
        }
    }
    @Synchronized fun selected(): Ticket? = active
    @Synchronized fun visible(ticket: Ticket): Boolean = active == ticket
    @Synchronized fun select(key: Key): Ticket {
        load(key) // Fail before switching if a saved draft is corrupt.
        write(dir.resolve("selected.bin")) { it.writeUTF(key.character); it.writeUTF(key.chat) }
        return Ticket(key, UUID.randomUUID()).also { active = it }
    }
    @Synchronized fun state(key: Key): State = load(key)
    @Synchronized fun edit(ticket: Ticket, text: String) {
        require(visible(ticket)) { "Stale session" }
        save(ticket.key, load(ticket.key).copy(draft = text))
    }
    @Synchronized fun prepare(ticket: Ticket, mode: String = "text"): Pending {
        require(visible(ticket) && ticket.key !in running)
        val state = load(ticket.key)
        check(state.pending == null) { "Resolve existing pending message first" }
        val pending = Pending(UUID.randomUUID().toString(), state.draft, mode)
        save(ticket.key, State(pending = pending))
        return pending
    }
    /** Discard retry state only; already persisted chat messages are not deleted. */
    @Synchronized fun discardPending(ticket: Ticket, id: String) {
        require(visible(ticket) && ticket.key !in running)
        val state = load(ticket.key)
        require(state.pending?.id == id)
        save(ticket.key, state.copy(pending = null))
    }
    /** Explicit send/retry only. UI must recheck ticket on its own render thread. */
    fun sendPending(ticket: Ticket, history: NativeConversation.History, identity: String,
                    cancelled: () -> Boolean = { false }, onText: (Ticket, String) -> Unit,
                    generate: (List<NativeConversation.Message>, () -> Boolean, (String) -> Unit) -> String): NativeConversation.Event {
        val pending = synchronized(this) {
            require(visible(ticket)) { "Stale session" }
            val p = checkNotNull(load(ticket.key).pending) { "No pending message" }
            check(running.isEmpty()) { "Another turn is running" }
            running.add(ticket.key)
            p
        }
        try {
            val stop = { cancelled() || !visible(ticket) }
            val event = NativeConversation.send(history, pending.id, device, ticket.key.character, ticket.key.chat,
                pending.text, identity, pending.mode, cancelled = stop, onText = { fragment ->
                    if (!visible(ticket)) throw IOException("Session changed")
                    onText(ticket, fragment)
                }, generate = generate)
            synchronized(this) {
                val state = load(ticket.key)
                check(state.pending == pending)
                save(ticket.key, state.copy(pending = null))
            }
            return event
        } finally { synchronized(this) { running.remove(ticket.key) } }
    }
}
