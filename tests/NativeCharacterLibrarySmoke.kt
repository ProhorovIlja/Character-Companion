package dev.caicompanion.app

import java.nio.file.Files
import java.util.UUID

fun main() {
    val root = Files.createTempDirectory("mandy-library")
    val account = UUID(0, 1).toString()
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    try {
        val lib = NativeCharacterLibrary(root, account)
        verify(lib.list().isEmpty())
        val a = lib.create("Mandy", "Independent fictional identity 🌙", "First meeting")
        val b = lib.create("Mandy", "Другой персонаж", "New chat")
        verify(a.id != b.id && a.chats.single().id != b.chats.single().id)
        val updated = lib.edit(a.id, "Мэнди", "Updated identity")
        verify(updated.id == a.id && updated.chats == a.chats)
        val added = lib.newChat(a.id, "Music")
        verify(added.chats.size == 2 && added.chats.first() == a.chats.first())
        val reopened = NativeCharacterLibrary(root, account)
        verify(reopened.get(a.id) == added && reopened.get(b.id) == b)
        verify(reopened.list().size == 2)
        verify(reopened.resolve(NativeChatWorkspace.Key(a.id, added.chats.last().id)) == added)
        verify(runCatching { reopened.resolve(NativeChatWorkspace.Key(a.id, b.chats.single().id)) }.isFailure)
        verify(NativeCharacterLibrary(root, UUID(0, 2).toString()).list().isEmpty())
        verify(runCatching { lib.edit(a.id, "", "identity") }.isFailure)
        verify(runCatching { lib.create("a", "x".repeat(8001), "chat") }.isFailure)
        verify(runCatching { lib.create("a", "bad\uD800", "chat") }.isFailure)
        verify(runCatching { lib.get("../escape") }.isFailure)
        verify(reopened.get(a.id) == added)
        Files.write(root.resolve(account).resolve(a.id + ".profile"), byteArrayOf(0, 1, 2))
        verify(runCatching { lib.get(a.id) }.isFailure)
        verify(runCatching { lib.list() }.isFailure)
        verify(lib.get(b.id) == b)
        println("NativeCharacterLibrary: $checks checks passed")
    } finally { root.toFile().deleteRecursively() }
}
