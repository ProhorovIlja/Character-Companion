package dev.caicompanion.app

/** Explicitly initiated generation from a durable pending message; no remote endpoint. */
object EmbeddedChatTurn {
    fun send(workspace: NativeChatWorkspace, ticket: NativeChatWorkspace.Ticket,
             characters: NativeCharacterLibrary, history: NativeConversation.History,
             models: NativeModelLibrary, trustedSha: String,
             cancelled: () -> Boolean = { false }, onText: (NativeChatWorkspace.Ticket, String) -> Unit): NativeConversation.Event {
        return sendWithMemory(workspace, ticket, characters, history, models, trustedSha, null, cancelled, onText = onText)
    }
    fun sendWithMemory(workspace: NativeChatWorkspace, ticket: NativeChatWorkspace.Ticket,
                       characters: NativeCharacterLibrary, history: NativeConversation.History,
                       models: NativeModelLibrary, trustedSha: String, memory: NativeCharacterMemory?,
                       cancelled: () -> Boolean = { false }, admit: (NativeModelLibrary.Model) -> Unit = {},
                       preset: InferencePreset = InferencePreset.BALANCED,
                       onText: (NativeChatWorkspace.Ticket, String) -> Unit): NativeConversation.Event {
        require(trustedSha.matches(Regex("[0-9a-f]{64}")))
        val profile = characters.resolve(ticket.key)
        require(workspace.visible(ticket)) { "Stale session" }
        val pending = checkNotNull(workspace.state(ticket.key).pending)
        val identity = memory?.compose(profile.id, profile.identity, pending.text) ?: profile.identity
        return workspace.sendPending(ticket, history, identity, cancelled, onText) { messages, stop, emit ->
            // Open only if inference is needed. A completed retry needs no model load.
            EmbeddedLlama.open(models, trustedSha, stop, admit).use { engine ->
                engine.generate(messages, contextTokens = preset.contextTokens, outputTokens = preset.outputTokens,
                    cancelled = stop, threads = preset.threads, onText = emit)
            }
        }
    }
}
