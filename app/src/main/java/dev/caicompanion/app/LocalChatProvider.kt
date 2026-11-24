package dev.caicompanion.app

import java.io.IOException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Android platform JSON binding for the local model stream; not yet connected to a UI. */
object LocalChatProvider {
    data class Message(val role: String, val content: String)

    fun generate(model: String, port: Int, messages: List<Message>, maxTokens: Int = 1024,
                 cancelled: () -> Boolean = { false }, onText: (String) -> Unit): String {
        require(model.isNotBlank() && model.length <= 256 && maxTokens in 1..4096)
        require(messages.size in 2..201 && messages.first().role == "system" && messages.last().role == "user")
        require(messages.drop(1).all { it.role == "user" || it.role == "assistant" })
        require(messages.all { it.content.isNotBlank() && it.content.length <= 64000 })
        require(messages.sumOf { it.content.length.toLong() } <= 64000)
        val array = JSONArray()
        messages.forEach { array.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val request = JSONObject().put("model", model).put("messages", array)
            .put("max_tokens", maxTokens).put("stream", true).toString().toByteArray(Charsets.UTF_8)
        return LocalModelStream.read(port, request, ::decode, cancelled, onText)
    }

    private fun decode(event: String): LocalModelStream.Delta? {
        try {
            val root = JSONObject(event)
            if (!root.isNull("error")) throw IOException("Local model reported an error")
            val choices = root.getJSONArray("choices")
            if (choices.length() == 0 && root.optJSONObject("usage") != null) return null
            if (choices.length() != 1) throw IOException("Unexpected choice count")
            val choice = choices.getJSONObject(0)
            if (choice.opt("index") != 0) throw IOException("Unexpected choice index")
            val delta = choice.getJSONObject("delta")
            if (!delta.isNull("tool_calls") || !delta.isNull("function_call")) throw IOException("Tool calls are not supported")
            val text = if (delta.isNull("content")) "" else delta.opt("content") as? String
                ?: throw IOException("Invalid response content")
            val finish = if (choice.isNull("finish_reason")) null else choice.opt("finish_reason")
            if (finish != null && finish != "stop") throw IOException("Generation did not complete normally")
            return LocalModelStream.Delta(text, finish == "stop")
        } catch (e: JSONException) {
            throw IOException("Invalid local model event", e)
        }
    }
}
