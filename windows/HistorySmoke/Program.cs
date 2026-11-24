using CharacterCompanion.Windows.Intelligence;
using Microsoft.Data.Sqlite;

var dir = Path.Combine(Path.GetTempPath(), "mandy-history-smoke-" + Guid.NewGuid().ToString("N"));
Directory.CreateDirectory(dir);
var path = Path.Combine(dir, "history.db");
string account = Guid.NewGuid().ToString(), device = Guid.NewGuid().ToString(),
    character = Guid.NewGuid().ToString(), chat = Guid.NewGuid().ToString(), id = Guid.NewGuid().ToString();
int checks = 0;
void Check(bool ok) { if (!ok) throw new Exception("History check failed."); checks++; }
void Reject<T>(Action action) where T : Exception
{
    try { action(); }
    catch (T) { checks++; return; }
    throw new Exception("Expected rejection.");
}
try
{
    LocalHistory.Event reply;
    using (var h = new LocalHistory(path, account))
    {
        var user = h.AppendUser(id, device, character, chat, "dictation", "Привет 🌙");
        Check(user.Clock == 1);
        Check(h.AppendUser(id, device, character, chat, "dictation", "Привет 🌙") == user);
        Reject<InvalidDataException>(() => h.AppendUser(id, device, character, chat, "dictation", "Different"));
        reply = h.CommitReply(user, "Hello");
        Check(reply.Clock == 2 && reply.Id == LocalHistory.ReplyId(id));
        Check(h.CommitReply(user, "Hello") == reply);
        Reject<InvalidDataException>(() => h.CommitReply(user, "Different"));
        Check(h.Page(character, chat, 1).Single() == reply);
        Check(h.Page(character, chat, 1, (reply.Clock,reply.Id)).Single() == user);
        Check(h.Page(Guid.NewGuid().ToString(), chat).Count == 0);
        var later = h.AppendUser(Guid.NewGuid().ToString(),device,character,chat,"text","Later");
        h.AppendUser(Guid.NewGuid().ToString(),device,character,chat,"text","Newest");
        Reject<InvalidDataException>(() => h.CommitReply(later,"Late"));
        Reject<InvalidDataException>(() => h.AppendUser(Guid.NewGuid().ToString(),device,Guid.NewGuid().ToString(),chat,"text","Wrong owner"));
        Reject<ArgumentException>(() => h.AppendUser(Guid.NewGuid().ToString(),device,character,chat,"text","\0"));
        Check(h.Page(character,chat).Count == 4);
    }
    using (var h = new LocalHistory(path,account)) Check(h.Find(reply.Id) == reply);
    Reject<InvalidDataException>(() => { using var wrong = new LocalHistory(path,Guid.NewGuid().ToString()); });
    using (var db = new SqliteConnection("Data Source=" + path + ";Pooling=False"))
    {
        db.Open();
        using var c = db.CreateCommand();
        c.CommandText = "PRAGMA user_version=99";
        c.ExecuteNonQuery();
    }
    Reject<InvalidDataException>(() => { using var wrong = new LocalHistory(path,account); });
    // Exercise real SQLite + injected synthetic inference. No network or real model.
    var turnPath = Path.Combine(dir,"turn.db");
    var request = new LocalChatTurn.Request(account,device,character,chat,id,"Привет","Bilingual character");
    int calls = 0;
    var turn = new LocalChatTurn(turnPath, (messages, preview, stop) =>
    {
        Interlocked.Increment(ref calls);
        Check(messages[0].Role == "system" && messages[^1].Content == "Привет");
        preview?.Invoke("Hello");
        return Task.FromResult("Hello 🌙");
    });
    var previews = new List<string>();
    var result = await turn.SendAsync(request, previews.Add);
    Check(result.Text == "Hello 🌙" && result.Clock == 2 && previews.SequenceEqual(new[] { "Hello" }));
    Check(await turn.SendAsync(request) == result && calls == 1);
    var next = request with { MessageId = Guid.NewGuid().ToString(), Text = "Again" };
    var failed = new LocalChatTurn(turnPath, (_, _, _) => Task.FromException<string>(new IOException("fixture failure")));
    try { await failed.SendAsync(next); Check(false); } catch (IOException) { Check(true); }
    using (var h = new LocalHistory(turnPath, account))
        Check(h.Find(next.MessageId)?.Text == "Again" && h.Find(LocalHistory.ReplyId(next.MessageId)) == null);
    using (var stop = new CancellationTokenSource())
    {
        var cancelled = new LocalChatTurn(turnPath, (_, _, _) => { stop.Cancel(); return Task.FromResult("Discard"); });
        try { await cancelled.SendAsync(next, cancellationToken: stop.Token); Check(false); }
        catch (OperationCanceledException) { Check(true); }
    }
    using (var h = new LocalHistory(turnPath, account))
        Check(h.Find(LocalHistory.ReplyId(next.MessageId)) == null);
    var advanced = new LocalChatTurn(turnPath, (_, _, _) =>
    {
        using var h = new LocalHistory(turnPath, account);
        h.AppendUser(Guid.NewGuid().ToString(),device,character,chat,"text","Concurrent");
        return Task.FromResult("Stale");
    });
    try { await advanced.SendAsync(next); Check(false); } catch (InvalidDataException) { Check(true); }
    using (var h = new LocalHistory(turnPath, account))
        Check(h.Find(LocalHistory.ReplyId(next.MessageId)) == null);
    Console.WriteLine($"PASS {checks} native history and turn checks");
}
finally { Directory.Delete(dir, true); }
