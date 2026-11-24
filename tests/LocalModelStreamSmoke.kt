package dev.caicompanion.app

import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress

fun main() {
    var tests = 0
    val request = "{}".toByteArray()
    fun run(payload: ByteArray, status: Int = 200, mime: String = "text/event-stream", cancel: Boolean = false, expected: String? = null) {
        var calls = 0
        var sent = false
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            calls++
            check(exchange.requestMethod == "POST")
            check(exchange.requestBody.readBytes().contentEquals(request))
            exchange.responseHeaders.add("Content-Type", mime)
            exchange.responseHeaders.add("Location", "/redirect")
            exchange.sendResponseHeaders(status, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
        server.createContext("/redirect") { exchange ->
            calls++
            exchange.sendResponseHeaders(500, -1)
            exchange.close()
        }
        server.start()
        try {
            val fragments = StringBuilder()
            val result = runCatching {
                LocalModelStream.read(server.address.port, request, { event ->
                    when (event) {
                        "usage" -> null
                        "stop" -> LocalModelStream.Delta(finished = true)
                        else -> LocalModelStream.Delta(event)
                    }
                }, { cancel && sent }) { fragments.append(it); sent = true }
            }
            if (expected == null) check(result.exceptionOrNull() is IOException)
            else { check(result.getOrThrow() == expected); check(fragments.toString() == expected) }
            check(calls == 1)
            tests++
        } finally { server.stop(0) }
    }
    fun bytes(s: String) = s.toByteArray(Charsets.UTF_8)
    val end = "data: stop\n\ndata: [DONE]\n\n"
    run(bytes(": ping\r\n\r\ndata: Привет 🌙\r\n\r\ndata: usage\n\n" + end), expected = "Привет 🌙")
    run(bytes("data: one\ndata: two\n\n" + end), expected = "one\ntwo")
    run(bytes("data: partial\n\n"))
    run(bytes("data: [DONE]\n\n"))
    run(bytes(end))
    run(bytes("data: partial\n\n" + end), cancel = true)
    run(bytes("data: " + "x".repeat(16001) + "\n\n" + end))
    run(bytes("data: " + "x".repeat(131073) + "\n\n" + end))
    run(bytes("data: x\n\ndata: stop\n\ndata: extra\n\ndata: [DONE]\n\n"))
    run(bytes("data: x\n\n" + end), status = 302)
    run(bytes("data: x\n\n" + end), mime = "application/json")
    run(bytes("data: ") + byteArrayOf(0xc3.toByte(), 0x28) + bytes("\n\n" + end))
    check(runCatching { LocalModelStream.read(0, request, { null }) {} }.exceptionOrNull() is IllegalArgumentException)
    tests++
    println("LocalModelStream: $tests checks passed")
}
