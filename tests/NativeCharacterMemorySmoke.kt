package dev.caicompanion.app

import java.nio.file.Files
import java.util.UUID

fun main() {
    val root = Files.createTempDirectory("mandy-memory")
    fun id(n: Long) = UUID(0, n).toString()
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun bad(block: () -> Unit) = verify(runCatching(block).isFailure)
    try {
        val memory = NativeCharacterMemory(root, id(1))
        verify(memory.get(id(2)) == NativeCharacterMemory.State())
        verify(memory.compose(id(2), "Identity", "hello").contains("strangers"))
        val state = memory.edit(id(2), NativeCharacterMemory.State(stage = NativeCharacterMemory.Stage.FRIENDS,
            relationship = "Met at a concert", pinned = "Enjoys music 🌙", memories = listOf("Guitar concert together", "Guitar practice", "Visited Berlin", "Любит музыку")))
        verify(state.revision == 1L)
        val context = memory.compose(id(2), "Identity", "guitar concert")
        verify(context.startsWith("Identity\n") && context.contains("friends"))
        verify(context.contains("Met at a concert") && context.contains("Enjoys music 🌙"))
        verify(context.indexOf("Guitar concert together") < context.indexOf("Guitar practice") && context.contains("Guitar practice"))
        verify(!context.contains("Visited Berlin"))
        verify(memory.compose(id(2), "Identity", "МУЗЫКУ").contains("Любит музыку"))
        verify(!memory.compose(id(3), "Other", "guitar").contains("Guitar"))
        verify(NativeCharacterMemory(root, id(4)).get(id(2)).revision == 0L)
        verify(NativeCharacterMemory(root, id(1)).get(id(2)) == state)
        bad { memory.edit(id(2), state.copy(revision = 0)) }
        verify(memory.get(id(2)) == state)
        val next = memory.edit(id(2), state.copy(stage = NativeCharacterMemory.Stage.ESTRANGED, memories = emptyList()))
        verify(next.revision == 2L && memory.compose(id(2), "Identity", "guitar").contains("estranged"))
        verify(!memory.compose(id(2), "Identity", "guitar").contains("Guitar"))
        val longIdentity = "a".repeat(7999)
        verify(memory.compose(id(2), longIdentity, "hello") == longIdentity)
        verify(memory.compose(id(2), "Identity", "x".repeat(11995)) == "Identity")
        bad { state.copy(memories = listOf("same", "same")) }
        bad { state.copy(pinned = "x".repeat(1201)) }
        bad { state.copy(relationship = "\uD800") }
        bad { memory.get("../escape") }
        Files.write(root.resolve(id(1)).resolve(id(2) + ".memory"), byteArrayOf(0,1))
        bad { memory.get(id(2)) }
        verify(memory.get(id(3)).revision == 0L)
        println("NativeCharacterMemory: $checks checks passed")
    } finally { root.toFile().deleteRecursively() }
}
