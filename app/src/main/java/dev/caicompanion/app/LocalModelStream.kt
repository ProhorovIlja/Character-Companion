package dev.caicompanion.app

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Foreground local model transport. Caller owns worker, authorization and persistence. */
object LocalModelStream {
    const val MAX_REQUEST = 256 * 1024
    private const val MAX_RESPONSE = 2 * 1024 * 1024
    private const val MAX_LINE = 128 * 1024
    private const val MAX_TEXT = 16000
    data class Delta(val text: String = "", val finished: Boolean = false)

    /** Decoder handles one SSE JSON data event; null means an ignorable usage frame. */
    fun read(port: Int, request: ByteArray, decode: (String) -> Delta?,
             cancelled: () -> Boolean = { false }, onText: (String) -> Unit): String {
        require(port in 1..65535 && request.size in 1..MAX_REQUEST)
        val start = System.nanoTime()
        fun checkActive() {
            if (cancelled() || Thread.currentThread().isInterrupted) throw IOException("Generation cancelled")
            if (System.nanoTime() - start > 120_000_000_000L) throw IOException("Generation deadline exceeded")
        }
        checkActive()
        val conn = URL("http://127.0.0.1:$port/v1/chat/completions").openConnection(Proxy.NO_PROXY) as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 5000
            conn.readTimeout = 15000
            conn.useCaches = false
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "text/event-stream")
            conn.setRequestProperty("Accept-Encoding", "identity")
            conn.setFixedLengthStreamingMode(request.size)
            conn.outputStream.use { it.write(request) }
            checkActive()
            if (conn.responseCode != 200 || conn.contentType?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT) != "text/event-stream") {
                throw IOException("Local model did not return an SSE response")
            }
            val answer = StringBuilder()
            var finished = false
            var total = 0
            val data = StringBuilder()
            BufferedInputStream(conn.inputStream).use { input ->
                while (true) {
                    checkActive()
                    val line = ByteArrayOutputStream()
                    while (true) {
                        val b = input.read()
                        if (b < 0) throw IOException("Incomplete local model response")
                        if (++total > MAX_RESPONSE) throw IOException("Response limit exceeded")
                        if (b == 10) break
                        if (line.size() >= MAX_LINE) throw IOException("Line limit exceeded")
                        line.write(b)
                        if (total % 1024 == 0) checkActive()
                    }
                    val value = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(line.toByteArray())).toString().removeSuffix("\r")
                    if (value.startsWith("data:")) {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(value.substring(5).removePrefix(" "))
                    }
                    if (value.isNotEmpty() || data.isEmpty()) continue
                    val event = data.toString()
                    data.setLength(0)
                    if (event == "[DONE]") {
                        checkActive()
                        if (!finished || answer.isBlank()) throw IOException("Missing complete reply")
                        return answer.toString()
                    }
                    val delta = decode(event) ?: continue
                    if (finished) throw IOException("Unexpected data after completion")
                    if (delta.text.length > MAX_TEXT - answer.length) throw IOException("Reply limit exceeded")
                    if (delta.text.any { it.code < 32 && it !in "\t\r\n" }) throw IOException("Invalid control character")
                    if (delta.text.isNotEmpty()) {
                        checkActive()
                        answer.append(delta.text)
                        onText(delta.text)
                    }
                    finished = delta.finished
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
