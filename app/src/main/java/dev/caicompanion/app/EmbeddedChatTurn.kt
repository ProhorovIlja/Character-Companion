package dev.caicompanion.app

/** Explicitly initiated generation from a durable pending message; no remote endpoint. */
object EmbeddedChatTurn {
    fun send(workspace: NativeChatWorkspace, ticket: NativeChatWorkspace.Ticket,
             characters: NativeCharacterLibrary, history: NativeConversation.History,
             models: NativeModelLibrary, trustedSha: String,
             cancelled: () -> Boolean = { false }, onText: (NativeChatWorkspace.Ticket, String) -> Unit): NativeConversation.Event {
        require(trustedSha.matches(Regex("[0-9a-f]{64}")))
        val identity = characters.resolve(ticket.key).identity
        return workspace.sendPending(ticket, history, identity, cancelled, onText) { messages, stop, emit ->
            // Open only if inference is needed. A completed retry needs no model load.
            EmbeddedLlama.open(models, trustedSha, stop).use { engine ->
                engine.generate(messages, cancelled = stop, onText = emit)
            }
        }
    }
}
