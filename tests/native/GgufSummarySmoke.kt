package dev.caicompanion.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

fun main() {
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    class Fixture(version: Int = 3, count: Long = 4) {
        val out = ByteArrayOutputStream()
        fun u32(n: Int) { out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(n).array()) }
        fun u64(n: Long) { out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(n).array()) }
        fun str(s: String) { val b = s.toByteArray(); u64(b.size.toLong()); out.write(b) }
        fun entry(key: String, value: String) { str(key); u32(8); str(value) }
        init { out.write(byteArrayOf(71,71,85,70)); u32(version); u64(1); u64(count) }
        fun info() = GgufSummary.read(ByteArrayInputStream(out.toByteArray()))
    }
    fun sample(version: Int = 3) = Fixture(version).apply {
        entry("general.name", "Модель 🌙"); entry("general.architecture", "qwen3"); entry("general.type", "model"); str("general.file_type"); u32(4); u32(15)
    }
    val info = sample().info()
    verify(info == GgufSummary.Info(3, "Модель 🌙", "qwen3", "model", 15))
    verify(sample(2).info()?.version == 2)
    verify(sample(1).info() == null)
    verify(Fixture(count = 0).info() == GgufSummary.Info(3,null,null,null,null))
    verify(Fixture(count = 1).apply { entry("general.name", "x\u0000y") }.info()?.name == null)
    verify(Fixture(count = 1).apply { entry("general.name", "x\u202Ey") }.info()?.name == null)
    verify(Fixture(count = 2).apply { entry("general.name", "x"); entry("general.name", "y") }.info() == null)
    verify(Fixture(count = 1).apply { u64(-1) }.info() == null)
    verify(Fixture(count = 1).apply { u64(1000000) }.info() == null)
    verify(Fixture(count = 1).apply { str("unknown"); u32(999) }.info() == null)
    verify(Fixture(count = 1).apply { str("array"); u32(9); u32(12); u64(Long.MAX_VALUE) }.info() == null)
    verify(Fixture(count = 2).apply { str("array"); u32(9); u32(4); u64(2); u32(1); u32(2); entry("general.name", "valid") }.info()?.name == "valid")
    verify(Fixture(count = 2).apply { str("array"); u32(9); u32(8); u64(2); str("a"); str("b"); entry("general.architecture", "llama") }.info()?.architecture == "llama")
    verify(Fixture(count = 1).apply { str("general.name"); u32(8); u64(2); out.write(byteArrayOf(0xc3.toByte(),0x28)) }.info() == null)
    verify(GgufSummary.read(ByteArrayInputStream(sample().out.toByteArray().dropLast(1).toByteArray())) == null)
    verify(Fixture(count = 1).apply { str("large"); u32(8); u64(GgufSummary.MAX_BYTES.toLong()); out.write(ByteArray(GgufSummary.MAX_BYTES)) }.info() == null)
    verify(Fixture(count = 4).apply { entry("general.name", "valid"); entry("general.architecture", "llama"); str("general.file_type"); u32(4); u32(15); str("large"); u32(8); u64(Long.MAX_VALUE) }.info()?.name == "valid")
    println("PASS: $checks GGUF summary assertions")
}
