using System.Security.Cryptography;
using System.Text;
using Microsoft.Data.Sqlite;

namespace CharacterCompanion.Windows.Intelligence;

/// <summary>Local, single-caller v1 history store; use an explicit worker, never the UI thread.</summary>
internal sealed class LocalHistory : IDisposable
{
    internal const long MaxClock = 9007199254740991L;
    internal sealed record Event(string Id, string Device, string Character, string Chat, long Clock, string Role, string Mode, string Text);
    private const string Columns = "id,device,character,chat,clock,role,mode,text";
    private readonly SqliteConnection db;
    private static readonly UTF8Encoding Utf8 = new(false, true);

    internal LocalHistory(string path, string account)
    {
        Id(account);
        if (!Path.IsPathFullyQualified(path)) throw new ArgumentException("Choose an absolute local database path.");
        db = new SqliteConnection(new SqliteConnectionStringBuilder
        {
            DataSource = path, Mode = SqliteOpenMode.ReadWriteCreate,
            Cache = SqliteCacheMode.Private, Pooling = false, ForeignKeys = true, DefaultTimeout = 5
        }.ToString());
        try
        {
            db.Open();
            if (!string.Equals(Scalar(null, "PRAGMA journal_mode=WAL") as string, "wal", StringComparison.OrdinalIgnoreCase))
                throw new IOException("WAL unavailable.");
            Exec(null, "PRAGMA synchronous=FULL");
            if (Convert.ToInt64(Scalar(null, "PRAGMA synchronous")) != 2 ||
                Convert.ToInt64(Scalar(null, "PRAGMA foreign_keys")) != 1)
                throw new IOException("Database configuration unavailable.");
            using var tx = db.BeginTransaction(deferred: false);
            long version = Convert.ToInt64(Scalar(tx, "PRAGMA user_version"));
            if (version == 0)
            {
                if (Scalar(tx, "SELECT 1 FROM sqlite_master WHERE name NOT GLOB 'sqlite_*' LIMIT 1") != null)
                    throw new InvalidDataException("Nonempty unversioned database.");
                Exec(tx, "CREATE TABLE vault (singleton INTEGER PRIMARY KEY CHECK(singleton=1), account TEXT NOT NULL, clock INTEGER NOT NULL)");
                Exec(tx, "CREATE TABLE chats (id TEXT PRIMARY KEY, character TEXT NOT NULL, UNIQUE(id, character))");
                Exec(tx, "CREATE TABLE messages (id TEXT PRIMARY KEY, device TEXT NOT NULL, character TEXT NOT NULL, chat TEXT NOT NULL, clock INTEGER NOT NULL, role TEXT NOT NULL, mode TEXT NOT NULL, text TEXT NOT NULL, FOREIGN KEY(chat, character) REFERENCES chats(id, character))");
                Exec(tx, "CREATE INDEX messages_chat_order ON messages(chat, character, clock, id)");
                Exec(tx, "CREATE INDEX messages_export_order ON messages(clock, id)");
                Exec(tx, "INSERT INTO vault VALUES (1,$account,0)", ("$account",account));
                Exec(tx, "PRAGMA user_version=1");
            }
            else if (version != 1) throw new InvalidDataException("Unsupported history schema.");
            if (!string.Equals(Scalar(tx, "SELECT account FROM vault WHERE singleton=1") as string, account, StringComparison.Ordinal))
                throw new InvalidDataException("History account mismatch.");
            tx.Commit();
        }
        catch { db.Dispose(); throw; }
    }

    internal static void Id(string value)
    {
        if (!Guid.TryParseExact(value, "D", out var parsed) || parsed.ToString("D") != value)
            throw new ArgumentException("Noncanonical UUID.");
    }
    internal static void ValidateText(string text)
    {
        if (string.IsNullOrWhiteSpace(text) || text.Length > 16000 ||
            text.Any(c => c < 32 && c != '\t' && c != '\r' && c != '\n'))
            throw new ArgumentException("Invalid message text.");
        _ = Utf8.GetByteCount(text);
    }
    private static Event Validate(Event e)
    {
        Id(e.Id); Id(e.Device); Id(e.Character); Id(e.Chat);
        if (e.Clock is < 1 or > MaxClock || e.Role is not ("user" or "character") ||
            e.Mode is not ("text" or "dictation" or "call"))
            throw new ArgumentException("Invalid history event.");
        ValidateText(e.Text);
        return e;
    }
    internal static string ReplyId(string user)
    {
        Id(user);
        byte[] input = Convert.FromHexString(user.Replace("-", "")).Concat(Encoding.ASCII.GetBytes("companion-reply-v1")).ToArray();
        byte[] hash = SHA1.HashData(input); // UUIDv5 identifier, not a security hash.
        hash[6] = (byte)((hash[6] & 15) | 80);
        hash[8] = (byte)((hash[8] & 63) | 128);
        return Guid.ParseExact(Convert.ToHexString(hash.AsSpan(0, 16)), "N").ToString("D");
    }
    private SqliteCommand Command(SqliteTransaction? tx, string sql, params (string Name, object Value)[] args)
    {
        var command = db.CreateCommand();
        command.Transaction = tx;
        command.CommandText = sql;
        foreach (var (name, value) in args) command.Parameters.AddWithValue(name, value);
        return command;
    }
    private object? Scalar(SqliteTransaction? tx, string sql, params (string, object)[] args)
    {
        using var c = Command(tx, sql, args);
        return c.ExecuteScalar();
    }
    private void Exec(SqliteTransaction? tx, string sql, params (string, object)[] args)
    {
        using var c = Command(tx, sql, args);
        c.ExecuteNonQuery();
    }
    private static Event Read(SqliteDataReader r) => Validate(new Event(r.GetString(0),r.GetString(1),r.GetString(2),
        r.GetString(3),r.GetInt64(4),r.GetString(5),r.GetString(6),r.GetString(7)));
    private Event? Find(SqliteTransaction? tx, string id)
    {
        Id(id);
        using var c = Command(tx, "SELECT " + Columns + " FROM messages WHERE id=$id", ("$id",id));
        using var r = c.ExecuteReader();
        return r.Read() ? Read(r) : null;
    }
    internal Event? Find(string id) => Find(null, id);
    private long NextClock(SqliteTransaction tx)
    {
        if (Scalar(tx, "SELECT clock FROM vault WHERE singleton=1") is not long clock || clock < 0 || clock >= MaxClock)
            throw new InvalidDataException("Invalid or exhausted logical clock.");
        return clock + 1;
    }
    private Event Insert(SqliteTransaction tx, Event e)
    {
        Validate(e);
        var prior = Find(tx, e.Id);
        if (prior != null)
        {
            if (prior != e) throw new InvalidDataException("Conflicting message ID.");
            return prior;
        }
        var owner = Scalar(tx, "SELECT character FROM chats WHERE id=$chat", ("$chat",e.Chat));
        if (owner == null) Exec(tx, "INSERT INTO chats VALUES ($chat,$character)", ("$chat",e.Chat),("$character",e.Character));
        else if (!Equals(owner,e.Character)) throw new InvalidDataException("Conflicting chat owner.");
        Exec(tx, "INSERT INTO messages (" + Columns + ") VALUES ($id,$device,$character,$chat,$clock,$role,$mode,$text)",
            ("$id",e.Id),("$device",e.Device),("$character",e.Character),("$chat",e.Chat),("$clock",e.Clock),
            ("$role",e.Role),("$mode",e.Mode),("$text",e.Text));
        Exec(tx, "UPDATE vault SET clock=max(clock,$clock) WHERE singleton=1", ("$clock",e.Clock));
        return e;
    }
    internal Event AppendUser(string id, string device, string character, string chat, string mode, string text)
    {
        using var tx = db.BeginTransaction(deferred: false);
        var e = Insert(tx, new Event(id,device,character,chat,Find(tx,id)?.Clock ?? NextClock(tx),"user",mode,text));
        tx.Commit();
        return e;
    }
    private List<Event> Page(SqliteTransaction? tx, string character, string chat, int limit, (long Clock,string Id)? before)
    {
        Id(character); Id(chat);
        if (limit is < 1 or > 200) throw new ArgumentOutOfRangeException(nameof(limit));
        var args = new List<(string,object)> { ("$chat",chat),("$character",character),("$limit",limit) };
        string sql = "SELECT " + Columns + " FROM messages WHERE chat=$chat AND character=$character";
        if (before is { } cursor)
        {
            Id(cursor.Id);
            if (cursor.Clock is < 1 or > MaxClock) throw new ArgumentException("Invalid cursor.");
            sql += " AND (clock,id) < ($clock,$id)";
            args.Add(("$clock",cursor.Clock)); args.Add(("$id",cursor.Id));
        }
        using var c = Command(tx, sql + " ORDER BY clock DESC,id DESC LIMIT $limit", args.ToArray());
        using var r = c.ExecuteReader();
        var events = new List<Event>(limit);
        while (r.Read()) events.Add(Read(r));
        return events;
    }
    internal List<Event> Page(string character, string chat, int limit = 50, (long Clock,string Id)? before = null)
        => Page(null,character,chat,limit,before);

    internal Event CommitReply(Event user, string text)
    {
        Validate(user); ValidateText(text);
        if (user.Role != "user") throw new ArgumentException("Expected user message.");
        using var tx = db.BeginTransaction(deferred: false);
        if (Find(tx,user.Id) != user) throw new InvalidDataException("User message changed.");
        string id = ReplyId(user.Id);
        var existing = Find(tx,id);
        Event e;
        if (existing != null)
        {
            if (existing != new Event(id,user.Device,user.Character,user.Chat,existing.Clock,"character",user.Mode,text) ||
                existing.Clock <= user.Clock) throw new InvalidDataException("Conflicting reply.");
            e = existing;
        }
        else
        {
            if (Page(tx,user.Character,user.Chat,1,null).FirstOrDefault() != user)
                throw new InvalidDataException("Conversation advanced.");
            e = Insert(tx,new Event(id,user.Device,user.Character,user.Chat,NextClock(tx),"character",user.Mode,text));
        }
        tx.Commit();
        return e;
    }
    public void Dispose() => db.Dispose();
}
