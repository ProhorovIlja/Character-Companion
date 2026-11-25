package dev.caicompanion.app

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

fun main() {
    val root = Files.createTempDirectory("mandy-picker-test-")
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun fixture(name: String): ByteArray {
        val out = ByteArrayOutputStream()
        fun n(v: Long, bytes: Int) { out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array(), 0, bytes) }
        fun s(v: String) { val b = v.toByteArray(); n(b.size.toLong(), 8); out.write(b) }
        out.write(byteArrayOf(71,71,85,70)); n(3,4); n(1,8); n(3,8)
        s("general.name"); n(8,4); s(name)
        s("general.architecture"); n(8,4); s("qwen3")
        s("general.file_type"); n(4,4); n(15,4)
        return out.toByteArray()
    }
    try {
        val models = (0..32).map { i ->
            val path = root.resolve("$i.gguf")
            val bytes = fixture("Модель $i")
            Files.write(path, bytes)
            NativeModelLibrary.Model(i.toString(16).padStart(64,'0'), bytes.size.toLong(), path)
        }
        val first = ModelPickerPage.read(models)
        verify(first.entries.size == 16 && first.hasNext && !first.hasPrevious)
        verify(first.entries.first().label.contains("Модель 0"))
        verify(first.entries.first().label.contains("qwen3"))
        verify(first.entries.first().label.contains(models[0].sha256))
        verify(first.entries.map { it.model } == models.take(16))
        val second = ModelPickerPage.read(models,16)
        verify(second.hasPrevious && second.hasNext && second.entries.map { it.model } == models.subList(16,32))
        val third = ModelPickerPage.read(models,32)
        verify(third.hasPrevious && !third.hasNext && third.entries.single().model == models.last())
        verify(ModelPickerPage.read(emptyList()).entries.isEmpty())
        for (bad in listOf(-16,1,48)) verify(runCatching { ModelPickerPage.read(models,bad) }.isFailure)
        Files.delete(models[0].path)
        verify(ModelPickerPage.read(models).entries.first().label.startsWith("Unnamed GGUF model"))
        Files.write(models[1].path, byteArrayOf(0,1,2))
        verify(ModelPickerPage.read(models).entries[1].label.contains("Architecture: Unknown"))
        verify(ModelPickerPage.clampOffset(32, 17) == 16)
        verify(ModelPickerPage.clampOffset(32, 0) == 0)
        verify(ModelPickerPage.clampOffset(16, 33) == 16)
        verify(ModelPickerPage.clampOffset(0, 1) == 0)
        verify(runCatching { ModelPickerPage.clampOffset(1, 33) }.isFailure)
        verify(runCatching { ModelPickerPage.clampOffset(0, -1) }.isFailure)
        val speech = ModelPickerPage.read(models, 32, speech = true)
        verify(speech.entries.single().model == models.last() && speech.entries.single().label.startsWith("Whisper GGML model"))
        verify(!speech.entries.single().label.contains("qwen3"))
        verify(speech.entries.single().label.contains(models.last().sha256))
        verify(first.entries.first().label.contains("GGUF v3"))
        verify(first.entries.first().label.contains("File type ID: 15"))
        println("PASS: $checks model picker assertions")
    } finally { root.toFile().deleteRecursively() }
}
