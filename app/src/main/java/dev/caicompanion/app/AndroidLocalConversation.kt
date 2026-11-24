package dev.caicompanion.app

/** Foreground integration entry point. Only call with an explicitly trusted local model endpoint. */
object AndroidLocalConversation {
    fun send(store: AndroidConversationStore, model: String, port: Int, id: String, device: String,
             character: String, chat: String, text: String, identity: String, mode: String = "text",
             contextUnits: Int = 12000, cancelled: () -> Boolean = { false }, onText: (String) -> Unit): NativeConversation.Event =
        NativeConversation.send(store, id, device, character, chat, text, identity, mode, contextUnits, cancelled, onText) { messages, stop, emit ->
            LocalChatProvider.generate(model, port, messages.map { LocalChatProvider.Message(it.role, it.content) }, cancelled = stop, onText = emit)
        }
}
