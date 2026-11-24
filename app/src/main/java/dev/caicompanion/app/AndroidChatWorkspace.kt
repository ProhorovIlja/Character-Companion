package dev.caicompanion.app

import android.content.Context

/** Own one instance per active account/device; invoke send only from an explicit UI action. */
class AndroidChatWorkspace(context: Context, private val account: String, device: String) {
    private val app = context.applicationContext
    val sessions = NativeChatWorkspace(app.noBackupFilesDir.toPath().resolve("mandy-workspace-v1"), account, device)

    /** Caller must authorize and trust the local endpoint. No engine is started here. */
    fun send(ticket: NativeChatWorkspace.Ticket, model: String, port: Int, identity: String,
             cancelled: () -> Boolean = { false }, onText: (NativeChatWorkspace.Ticket, String) -> Unit): NativeConversation.Event =
        AndroidConversationStore(app, account).use { history ->
            sessions.sendPending(ticket, history, identity, cancelled, onText) { messages, stop, emit ->
                LocalChatProvider.generate(model, port, messages.map { LocalChatProvider.Message(it.role, it.content) }, cancelled = stop, onText = emit)
            }
        }
}
