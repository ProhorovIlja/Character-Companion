package dev.caicompanion.app

import java.nio.file.Files
import java.util.UUID

fun main() {
    val root = Files.createTempDirectory("mandy-memory-priority-")
    val account = UUID.randomUUID().toString()
    val character = UUID.randomUUID().toString()
    var checks = 0
    fun verify(condition: Boolean) { check(condition); checks++ }
    try {
        val store = NativeCharacterMemory(root, account)
        val initial = store.get(character)
        val pinned = "P".repeat(600)
        val relationship = "R".repeat(600)
        val saved = store.edit(character, initial.copy(stage = NativeCharacterMemory.Stage.FRIENDS,
            pinned = pinned, relationship = relationship,
            memories = listOf("Enjoys jazz", "Visited Киото", "Jazz concerts", "Jazz records", "Jazz piano", "Jazz history")))
        val identity = "I".repeat(7200)
        val tight = store.compose(character, identity, "jazz")
        verify(tight.startsWith(identity))
        verify(tight.length <= 8000)
        verify(tight.contains("Pinned memory: " + pinned))
        verify(!tight.contains("Relationship note: " + relationship))
        val full = store.compose(character, "Core identity", "jazz Киото")
        verify(full.indexOf("Pinned memory:") in 0 until full.indexOf("Relationship note:"))
        verify(full.contains("Relevant memory: Visited Киото"))
        verify(Regex("Relevant memory: ").findAll(full).count() == 4)
        verify(store.compose(character, "I".repeat(8000), "jazz") == "I".repeat(8000))
        verify(store.compose(character, "Core identity", "x".repeat(11800)).length <= 200)
        verify(NativeCharacterMemory(root, account).get(character) == saved)
        verify(store.get(UUID.randomUUID().toString()) == NativeCharacterMemory.State())
        verify(runCatching { store.edit(character, initial) }.isFailure)
        verify(store.get(character) == saved)
        println("PASS: $checks memory-priority assertions with real temporary files")
    } finally { root.toFile().deleteRecursively() }
}
