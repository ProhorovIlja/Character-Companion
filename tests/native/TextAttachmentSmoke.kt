package dev.caicompanion.app

import java.io.ByteArrayInputStream
import java.io.InputStream

fun main() {
    var checks = 0
    fun verify(ok: Boolean) { check(ok); checks++ }
    fun read(s: String) = NativeTextAttachment.read(ByteArrayInputStream(s.toByteArray(Charsets.UTF_8)))
    fun reject(block: () -> Unit) = verify(runCatching(block).isFailure)
    verify(read("Hello\nПривет 🌙") == "Hello\nПривет 🌙")
    verify(read("\uFEFFText") == "Text")
    verify(read("a".repeat(6000)).length == 6000)
    reject { read("a".repeat(6001)) }
    reject { read("a".repeat(65537)) }
    reject { read(" \n\t") }
    reject { read("a\u0000b") }
    reject { read("a\u007Fb") }
    reject { NativeTextAttachment.read(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))) }
    reject { NativeTextAttachment.read(object : InputStream() {
        override fun read() = 0
        override fun read(b: ByteArray, off: Int, len: Int) = 0
    }) }
    val edit = NativeTextAttachment.insert("before SELECT after", 7, 13, "Привет")
    verify(edit.text.startsWith("before ") && edit.text.endsWith(" after"))
    verify(edit.text.contains("Привет") && !edit.text.contains("SELECT"))
    verify(edit.text.substring(edit.caret) == " after")
    reject { NativeTextAttachment.insert("a", 2, 2, "text") }
    reject { NativeTextAttachment.insert("x".repeat(16000), 0, 0, "text") }
    reject { NativeTextAttachment.insert("🌙", 1, 1, "text") }
    println("PASS: $checks text attachment assertions")
}
