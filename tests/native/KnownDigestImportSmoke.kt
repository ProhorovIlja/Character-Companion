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
        val defaultSpeech = NativeModelLibrary(speechRoot, format = NativeModelLibrary.Format.WHISPER)
        checkIt(defaultSpeech.verify(speechModel.sha256) == speechModel)
        checkIt(defaultSpeech.import(ByteArrayInputStream(speech)) == speechModel)
        checkIt(NativeModelLibrary(speechRoot, NativeModelLibrary.Format.WHISPER).list() == listOf(speechModel))
        reject { NativeModelLibrary(speechRoot, NativeModelLibrary.Format.WHISPER.ceiling + 1, NativeModelLibrary.Format.WHISPER) }
        reject { NativeModelLibrary(speechRoot, 0, NativeModelLibrary.Format.WHISPER) }
        reject { NativeModelLibrary(speechRoot, NativeModelLibrary.Format.WHISPER.headerBytes.toLong(), NativeModelLibrary.Format.WHISPER) }
        reject { defaultSpeech.import(ByteArrayInputStream(speech), expectedBytes = NativeModelLibrary.Format.WHISPER.ceiling + 1) }
        val limited = NativeModelLibrary(root.resolve("limited"), maxBytes = 99, format = NativeModelLibrary.Format.WHISPER)
        reject { limited.import(ByteArrayInputStream(speech)) }
        checkIt(limited.list().isEmpty())
        // Simulate another importer publishing after the final existence check.
        // For this fixed 300000-byte input the eighth cancellation check is just
        // before publication. Use a deliberately old timestamp to detect replacement.
        for (corrupt in listOf(false, true)) {
            val raceRoot = Files.createDirectory(root.resolve("race-$corrupt"))
            val race = NativeModelLibrary(raceRoot)
            val destination = raceRoot.resolve("$sha.gguf")
            val competing = if (corrupt) wrong else bytes
            val competingStamp = java.nio.file.attribute.FileTime.fromMillis(1000000L)
            var calls = 0
            val result = runCatching {
                race.import(ByteArrayInputStream(bytes), cancelled = {
                    if (++calls == 8) {
                        check(!Files.exists(destination))
                        Files.write(destination, competing)
                        Files.setLastModifiedTime(destination, competingStamp)
                    }
                    false
                })
            }
            checkIt(if (corrupt) result.exceptionOrNull() is IllegalArgumentException else result.getOrNull()?.sha256 == sha)
            checkIt(Files.readAllBytes(destination).contentEquals(competing))
            checkIt(Files.getLastModifiedTime(destination) == competingStamp)
            checkIt(Files.newDirectoryStream(raceRoot, "*.part").use { !it.iterator().hasNext() })
        }
        // Head dimensions must divide their corresponding state exactly.
        val validDimensions = speech.clone().also {
            ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(12, 384).putInt(16, 6).putInt(28, 384).putInt(32, 6)
        }
        val dimensionRoot = Files.createDirectory(root.resolve("dimensions"))
        val dimensionLib = NativeModelLibrary(dimensionRoot, format = NativeModelLibrary.Format.WHISPER)
        val dimensionModel = dimensionLib.import(ByteArrayInputStream(validDimensions))
        checkIt(dimensionLib.verify(dimensionModel.sha256) == dimensionModel)
        for ((offset, invalid) in listOf(16 to 5, 32 to 5, 16 to 385, 32 to 385)) {
            val malformed = validDimensions.clone().also {
                ByteBuffer.wrap(it).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, invalid)
            }
            reject { dimensionLib.import(ByteArrayInputStream(malformed)) }
            checkIt(dimensionLib.list() == listOf(dimensionModel))
            checkIt(Files.newDirectoryStream(dimensionRoot, "*.part").use { !it.iterator().hasNext() })
        }
        println("PASS $checks known-digest import checks")
    } finally { root.toFile().deleteRecursively() }
}
