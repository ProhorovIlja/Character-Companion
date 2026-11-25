package dev.caicompanion.app

import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest

fun main() {
    val root = Files.createTempDirectory("mandy-model-dedup-")
    var checks = 0
    fun checkIt(ok: Boolean) { check(ok); checks++ }
    fun reject(block: () -> Unit) {
        try { block() } catch (_: IllegalArgumentException) { checks++; return }
        catch (_: IOException) { checks++; return }
        error("Expected rejection")
    }
    try {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).put(byteArrayOf(71,71,85,70)).putInt(3).putLong(1).putLong(0)
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        val lib = NativeModelLibrary(root)
        fun noTemp() = Files.newDirectoryStream(root, "*.part").use { !it.iterator().hasNext() }
        val original = lib.import(ByteArrayInputStream(bytes), bytes.size.toLong(), sha)
        checkIt(original.sha256 == sha && Files.readAllBytes(original.path).contentEquals(bytes))
        checkIt(noTemp())
        val stamp = Files.getLastModifiedTime(original.path)
        var reads = 0
        val reused = lib.import(ByteArrayInputStream(bytes), bytes.size.toLong(), sha, progress = {
            check(noTemp()); reads++
        })
        checkIt(reused == original && reads >= 2)
        checkIt(Files.getLastModifiedTime(original.path) == stamp)
        checkIt(lib.list() == listOf(original))
        val wrong = bytes.clone().also { it[it.lastIndex] = 42 }
        reject { lib.import(ByteArrayInputStream(wrong), wrong.size.toLong(), sha) }
        reject { lib.import(ByteArrayInputStream(bytes), bytes.size.toLong() - 1, sha) }
        reject { lib.import(ByteArrayInputStream(bytes), bytes.size.toLong(), sha, cancelled = { true }) }
        var stop = false
        reject { lib.import(ByteArrayInputStream(bytes), bytes.size.toLong(), sha, cancelled = { stop }, progress = { stop = true }) }
        checkIt(noTemp() && Files.readAllBytes(original.path).contentEquals(bytes))
        Files.write(original.path, wrong)
        reject { lib.import(ByteArrayInputStream(bytes), bytes.size.toLong(), sha) }
        checkIt(Files.readAllBytes(original.path).contentEquals(wrong) && noTemp())
        Files.write(original.path, bytes)
        var staged = false
        checkIt(lib.import(ByteArrayInputStream(bytes), progress = { staged = staged || !noTemp() }) == original && staged)
        checkIt(noTemp())
        val speechRoot = Files.createDirectory(root.resolve("speech"))
        val speech = ByteBuffer.allocate(100).order(ByteOrder.LITTLE_ENDIAN).putInt(0x67676d6c).apply { repeat(10) { putInt(1) }; putInt(0) }.array()
        val speechLib = NativeModelLibrary(speechRoot, NativeModelLibrary.Format.WHISPER.ceiling, NativeModelLibrary.Format.WHISPER)
        val speechModel = speechLib.import(ByteArrayInputStream(speech))
        checkIt(speechLib.import(ByteArrayInputStream(speech), expectedSha = speechModel.sha256, progress = {
            check(Files.newDirectoryStream(speechRoot, "*.part").use { !it.iterator().hasNext() })
        }) == speechModel)
        println("PASS $checks known-digest import checks")
    } finally { root.toFile().deleteRecursively() }
}
