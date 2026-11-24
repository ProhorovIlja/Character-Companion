using System.IO;

namespace CharacterCompanion.Windows.Intelligence;

/// <summary>One explicit local turn at a time. Database writes are not held open during inference.</summary>
internal sealed class LocalChatTurn
{
    internal sealed record Request(string Account, string Device, string Character, string Chat,
        string MessageId, string Text, string Identity, string Mode = "text", int ContextUnits = 12000);
    internal delegate Task<string> Generate(IReadOnlyList<LocalInferenceClient.Message> messages,
        Action<string>? preview, CancellationToken cancellationToken);
    private readonly string path;
    private readonly Generate generate;
    private int active;

    internal LocalChatTurn(string path, LocalInferenceClient client)
        : this(path, (messages, preview, stop) => client.CompleteAsync(messages, onText: preview, cancellationToken: stop)) {}
    internal LocalChatTurn(string path, Generate generate)
    {
        if (!Path.IsPathFullyQualified(path)) throw new ArgumentException("Choose an absolute local database path.");
        this.path = path;
        this.generate = generate ?? throw new ArgumentNullException(nameof(generate));
    }

    /// <summary>Preview callbacks execute on a worker. Marshal to UI and reject stale selection there.</summary>
    internal async Task<LocalHistory.Event> SendAsync(Request request, Action<string>? preview = null,
        CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(request);
        foreach (string id in new[] { request.Account, request.Device, request.Character, request.Chat, request.MessageId })
            LocalHistory.Id(id);
        LocalHistory.ValidateText(request.Text);
        LocalHistory.ValidateText(request.Identity);
        if (request.Identity.Length > 8000 || request.ContextUnits is < 256 or > 64000 ||
            request.Text.Length + request.Identity.Length > request.ContextUnits ||
            request.Mode is not ("text" or "dictation" or "call"))
            throw new ArgumentException("Invalid character prompt or turn budget.");
        cancellationToken.ThrowIfCancellationRequested();
        if (Interlocked.CompareExchange(ref active, 1, 0) != 0)
            throw new InvalidOperationException("A local turn is already active.");
        try
        {
            // SQLite async methods are synchronous internally. Offload the explicitly
            // requested operation as a unit, not a recurring or detached worker.
            return await Task.Run(async () =>
            {
                cancellationToken.ThrowIfCancellationRequested();
                using var history = new LocalHistory(path, request.Account);
                cancellationToken.ThrowIfCancellationRequested();
                var user = history.AppendUser(request.MessageId, request.Device, request.Character,
                    request.Chat, request.Mode, request.Text);
                var old = history.Find(LocalHistory.ReplyId(user.Id));
                cancellationToken.ThrowIfCancellationRequested();
                if (old != null)
                {
                    // CommitReply validates existing reply identity/scope/clock and returns it.
                    return history.CommitReply(user, old.Text);
                }
                var rows = history.Page(request.Character, request.Chat, 200);
                if (rows.Count == 0 || rows[0] != user)
                    throw new InvalidDataException("Conversation advanced.");
                var prompt = BuildPrompt(request, rows);
                cancellationToken.ThrowIfCancellationRequested();
                string answer = await generate(prompt, preview, cancellationToken).ConfigureAwait(false);
                LocalHistory.ValidateText(answer);
                cancellationToken.ThrowIfCancellationRequested();
                // The final transaction rechecks the user and latest row. Once commit
                // succeeds, return the durable reply even if cancellation races it.
                return history.CommitReply(user, answer);
            }, cancellationToken).ConfigureAwait(false);
        }
        finally { Volatile.Write(ref active, 0); }
    }

    private static IReadOnlyList<LocalInferenceClient.Message> BuildPrompt(Request request, List<LocalHistory.Event> rows)
    {
        int remaining = request.ContextUnits - request.Identity.Length;
        var messages = new List<LocalInferenceClient.Message>();
        foreach (var row in rows)
        {
            if (row.Text.Length > remaining) break;
            remaining -= row.Text.Length;
            messages.Add(new LocalInferenceClient.Message(row.Role == "user" ? "user" : "assistant", row.Text));
        }
        // Keep a contiguous recent suffix, dropping an orphan leading assistant.
        if (messages.Count > 1 && messages[^1].Role == "assistant") messages.RemoveAt(messages.Count - 1);
        messages.Reverse();
        messages.Insert(0, new LocalInferenceClient.Message("system", request.Identity));
        return messages;
    }
}
