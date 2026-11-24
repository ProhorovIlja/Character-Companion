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
    Console.WriteLine($"PASS {checks} native history checks");
}
finally { Directory.Delete(dir, true); }
