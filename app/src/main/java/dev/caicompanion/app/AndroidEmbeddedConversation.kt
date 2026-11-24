package dev.caicompanion.app

/** Uses the same durable turn protocol without a network endpoint. Explicit foreground calls only. */
object AndroidEmbeddedConversation {
    fun send(workspace: NativeChatWorkspace, ticket: NativeChatWorkspace.Ticket,
             store: AndroidConversationStore, engine: EmbeddedLlama, identity: String,
             cancelled: () -> Boolean = { false }, onText: (NativeChatWorkspace.Ticket, String) -> Unit): NativeConversation.Event =
        workspace.sendPending(ticket, store, identity, cancelled, onText) { messages, stop, emit ->
            engine.generate(messages, cancelled = stop, onText = emit)
        }
}
