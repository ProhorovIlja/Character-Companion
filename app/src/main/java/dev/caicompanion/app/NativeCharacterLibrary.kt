package dev.caicompanion.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Account-local character profiles; stable IDs independent of name and model. */
class NativeCharacterLibrary(root: Path, account: String) {
    data class Chat(val id: String, val title: String) {
        init { NativeConversation.canonicalId(id); validate(title, 120) }
    }
    data class Profile(val id: String, val name: String, val identity: String, val chats: List<Chat>) {
        init {
            NativeConversation.canonicalId(id)
            validate(name, 120); validate(identity, 8000)
            require(chats.size in 1..256 && chats.map { it.id }.distinct().size == chats.size)
        }
    }
    companion object {
        private fun validate(value: String, limit: Int) {
            NativeConversation.validateText(value)
            require(value.length <= limit)
        }
    }
    private val dir = root.resolve(NativeConversation.canonicalId(account).toString())
    init { Files.createDirectories(dir) }
    private fun file(id: String) = dir.resolve(NativeConversation.canonicalId(id).toString() + ".profile")
    @Synchronized fun get(id: String): Profile {
        val path = file(id)
        require(Files.size(path) in 1..160_000)
        return DataInputStream(Files.newInputStream(path).buffered()).use { input ->
            require(input.readInt() == 0x4d435031)
            require(input.readUTF() == id)
            val name = input.readUTF(); val identity = input.readUTF()
            val count = input.readInt()
            require(count in 1..256)
            val chats = List(count) { Chat(input.readUTF(), input.readUTF()) }
            require(input.read() == -1)
            Profile(id, name, identity, chats)
        }
    }
    @Synchronized fun list(): List<Profile> = Files.newDirectoryStream(dir, "*.profile").use { entries ->
        entries.map { get(it.fileName.toString().removeSuffix(".profile")) }
            .sortedWith(compareBy<Profile> { it.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.id })
    }
    private fun save(p: Profile) {
        val target = file(p.id)
        val temp = Files.createTempFile(dir, ".profile-", ".tmp")
        try {
            FileOutputStream(temp.toFile()).use { stream ->
                val out = DataOutputStream(stream.buffered())
                out.writeInt(0x4d435031); out.writeUTF(p.id); out.writeUTF(p.name); out.writeUTF(p.identity)
                out.writeInt(p.chats.size)
                p.chats.forEach { out.writeUTF(it.id); out.writeUTF(it.title) }
                out.flush(); stream.fd.sync()
            }
            require(Files.size(temp) <= 160_000)
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }
    @Synchronized fun create(name: String, identity: String, chatTitle: String): Profile {
        val id = UUID.randomUUID().toString()
        check(!Files.exists(file(id)))
        return Profile(id, name, identity, listOf(Chat(UUID.randomUUID().toString(), chatTitle))).also(::save)
    }
    @Synchronized fun edit(id: String, name: String, identity: String): Profile =
        get(id).copy(name = name, identity = identity).also(::save)
    @Synchronized fun newChat(id: String, title: String): Profile {
        val old = get(id)
        return old.copy(chats = old.chats + Chat(UUID.randomUUID().toString(), title)).also(::save)
    }
    @Synchronized fun resolve(key: NativeChatWorkspace.Key): Profile = get(key.character).also {
        require(it.chats.any { chat -> chat.id == key.chat }) { "Unknown conversation" }
    }
}
