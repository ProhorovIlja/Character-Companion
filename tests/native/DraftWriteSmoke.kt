package dev.caicompanion.app

import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.util.UUID

fun main() {
    val root = Files.createTempDirectory("mandy-draft-write-")
    fun id() = UUID.randomUUID().toString()
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun reject(block: () -> Unit) {
        check(runCatching(block).isFailure); checks++
    }
    try {
        val account = id(); val device = id()
        val w = NativeChatWorkspace(root, account, device)
        val key = NativeChatWorkspace.Key(id(), id())
        val ticket = w.select(key)
        val dir = root.resolve(account).resolve(device)
        val file = dir.resolve("${key.character}_${key.chat}.bin")
        w.edit(ticket, "")
        verify(!Files.exists(file))
        val text = "Привет 🌙\nDraft"
        w.edit(ticket, text)
        verify(w.state(key).draft == text && Files.exists(file))
        val original = Files.readAllBytes(file)
        val sentinel = FileTime.fromMillis(946684800000L)
        Files.setLastModifiedTime(file, sentinel)
        val before = Files.getLastModifiedTime(file)
        repeat(100) { w.edit(ticket, text) }
        verify(Files.getLastModifiedTime(file) == before)
        verify(Files.readAllBytes(file).contentEquals(original))
        verify(NativeChatWorkspace(root, account, device).state(key).draft == text)
        reject { w.edit(ticket, "x".repeat(16001)) }
        verify(Files.getLastModifiedTime(file) == before)
        w.edit(ticket, "Updated")
        verify(w.state(key).draft == "Updated" && Files.getLastModifiedTime(file) != before)
        val pending = w.prepare(ticket)
        Files.setLastModifiedTime(file, sentinel)
        val pendingStamp = Files.getLastModifiedTime(file)
        w.edit(ticket, "")
        verify(w.state(key).pending == pending && Files.getLastModifiedTime(file) == pendingStamp)
        w.edit(ticket, "Another draft")
        verify(w.state(key) == NativeChatWorkspace.State("Another draft", pending))
        val reopened = NativeChatWorkspace(root, account, device)
        verify(reopened.state(key) == w.state(key))
        val other = NativeChatWorkspace.Key(id(), id())
        val otherTicket = w.select(other)
        reject { w.edit(ticket, "Another draft") }
        w.edit(otherTicket, "")
        verify(w.state(other) == NativeChatWorkspace.State())
        val newTicket = w.select(key)
        w.discardPending(newTicket, pending.id)
        verify(w.state(key) == NativeChatWorkspace.State("Another draft"))
        w.edit(newTicket, "")
        verify(w.state(key) == NativeChatWorkspace.State())
        // A no-op edit must still detect corrupt saved state.
        Files.write(file, byteArrayOf(1, 2, 3))
        reject { w.edit(newTicket, "") }
        verify(Files.readAllBytes(file).contentEquals(byteArrayOf(1, 2, 3)))
        verify(Files.newDirectoryStream(dir, "*.tmp").use { !it.iterator().hasNext() })
        println("PASS $checks draft-write checks")
    } finally { root.toFile().deleteRecursively() }
}
