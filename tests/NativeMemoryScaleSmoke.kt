package dev.caicompanion.app

import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.util.UUID

fun main() {
    val root = Files.createTempDirectory("mandy-memory-scale")
    fun id(n: Long) = UUID(0, n).toString()
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    try {
        val lib = NativeCharacterMemory(root, id(1))
        val character = id(2)
        val path = root.resolve(id(1)).resolve(character + ".memory")
        fun magic() = DataInputStream(Files.newInputStream(path)).use { it.readInt() }
        // A separately constructed v1 record, rather than using the new writer.
        DataOutputStream(Files.newOutputStream(path)).use {
            it.writeInt(0x4d4d4331); it.writeUTF(character); it.writeLong(7)
            it.writeUTF("FRIENDS"); it.writeUTF("Shared history"); it.writeUTF("Pinned")
            it.writeInt(1); it.writeUTF("Original memory")
        }
        val old = lib.get(character)
        verify(old.revision == 7L && old.memories == listOf("Original memory"))
        val many = old.memories + List(127) { "界".repeat(395) + it.toString().padStart(3, '0') }
        val large = lib.edit(character, old.copy(memories = many))
        verify(large.revision == 8L && large.memories.size == NativeCharacterMemory.MAX_MEMORIES)
        verify(magic() == 0x4d4d4332)
        verify(Files.size(path) > 50000)
        verify(NativeCharacterMemory(root, id(1)).get(character) == large)
        verify(large.stage == old.stage && large.relationship == old.relationship && large.pinned == old.pinned)
        bad { large.copy(memories = large.memories + "extra") }
        bad { lib.edit(character, old) }
        val small = lib.edit(character, large.copy(memories = listOf("Original memory")))
        verify(magic() == 0x4d4d4331 && small.revision == 9L)
        verify(NativeCharacterMemory(root, id(1)).get(character) == small)
        val ranking = NativeCharacterMemory.rankedMemories(listOf("Music practice", "Music concert", "Music guitar", "Rare violin"), "music violin")
        verify(ranking.first() == "Rare violin" && ranking.size == 4)
        verify(NativeCharacterMemory.rankedMemories(listOf("User said: music", "The guitar"), "the user said") .isEmpty())
        verify(NativeCharacterMemory.rankedMemories(listOf("Ｃａｆé", "tea"), "cafe\u0301") == listOf("Ｃａｆé"))
        verify(NativeCharacterMemory.rankedMemories(listOf("Люблю музыку", "Люблю игры"), "МУЗЫКУ") == listOf("Люблю музыку"))
        verify(NativeCharacterMemory.rankedMemories(listOf("guitar A", "guitar B"), "guitar") == listOf("guitar A", "guitar B"))
        lib.edit(character, small.copy(memories = List(128) { "guitar memory $it" }))
        val ctx = lib.compose(character, "Identity", "guitar")
        verify(Regex("Relevant memory:").findAll(ctx).count() == 4)
        verify(ctx.length <= 8000 && ctx.startsWith("Identity"))
        verify(lib.compose(character, "a".repeat(7999), "guitar") == "a".repeat(7999))
        verify(lib.get(id(3)).memories.isEmpty())
        // A v1 record must not be interpreted as v2 merely because the cap grew.
        DataOutputStream(Files.newOutputStream(path)).use {
            it.writeInt(0x4d4d4331); it.writeUTF(character); it.writeLong(10)
            it.writeUTF("FRIENDS"); it.writeUTF(""); it.writeUTF(""); it.writeInt(33)
            repeat(33) { n -> it.writeUTF("Note $n") }
        }
        bad { lib.get(character) }
        println("NativeMemoryScale: $checks checks passed")
    } finally { root.toFile().deleteRecursively() }
}
