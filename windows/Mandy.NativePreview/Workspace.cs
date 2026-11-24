using System.IO;
using System.Text.Json;
using CharacterCompanion.Windows.Intelligence;

namespace Mandy.NativePreview;

/// <summary>Local trusted workspace with stable character and conversation IDs.</summary>
internal sealed class Workspace : IDisposable
{
    internal sealed record Profile(int Version, string Account, string Device, string Character, string Chat, string Name, string Identity);
    internal Profile Character { get; private set; }
    private readonly List<Profile> profiles;
    private readonly string catalogPath;
    private readonly string conversationsPath;
    private readonly string selectionPath;
    internal sealed record Conversation(string Character, string Chat, string Title);
    private readonly List<Conversation> conversations;
    internal IReadOnlyList<Conversation> Conversations => conversations.Where(c => c.Character == Character.Character).ToArray();
    internal string ConversationTitle => conversations.Single(c => c.Chat == Character.Chat).Title;
    internal IReadOnlyList<Profile> Profiles => profiles.AsReadOnly();
    internal string Database { get; }
    private readonly FileStream lease;
    private Workspace(Profile profile, string database, FileStream lease, string catalogPath, List<Profile> profiles, string conversationsPath, string selectionPath, List<Conversation> conversations)
    {
        Character = profile; Database = database; this.lease = lease; this.catalogPath = catalogPath; this.profiles = profiles;
        this.conversationsPath = conversationsPath; this.selectionPath = selectionPath; this.conversations = conversations;
    }
    private static void Validate(Profile p)
    {
        if (p.Version != 1) throw new InvalidDataException("Unsupported workspace.");
        foreach (var id in new[] { p.Account, p.Device, p.Character, p.Chat }) LocalHistory.Id(id);
        LocalHistory.ValidateText(p.Name); LocalHistory.ValidateText(p.Identity);
        if (p.Name.Length > 120 || p.Identity.Length > 8000) throw new InvalidDataException("Invalid character profile.");
    }
    internal static Workspace Open(string directory, bool create, string name, string identity)
    {
        if (!Path.IsPathFullyQualified(directory) || !Directory.Exists(directory))
            throw new ArgumentException("Choose an existing local folder.");
        string root = Path.GetFullPath(directory);
        if ((File.GetAttributes(root) & FileAttributes.ReparsePoint) != 0)
            throw new IOException("Linked workspace folders are not supported.");
        string meta = Path.Combine(root, "workspace.json");
        string db = Path.Combine(root, "history.db");
        string lockPath = Path.Combine(root, "workspace.lock");
        string catalog = Path.Combine(root, "characters.json");
        string conversationFile = Path.Combine(root, "conversations.json");
        string selectionFile = Path.Combine(root, "selection.json");
        if (create && Directory.EnumerateFileSystemEntries(root).Any())
            throw new IOException("Creation requires an empty folder.");
        if (!create && (!File.Exists(meta) || !File.Exists(db)))
            throw new IOException("Incomplete workspace; existing files have not been reset.");
        foreach (var path in new[] { meta, db, lockPath, db + "-wal", db + "-shm", catalog, conversationFile, selectionFile })
            if (File.Exists(path) && (File.GetAttributes(path) & FileAttributes.ReparsePoint) != 0)
                throw new IOException("Linked workspace files are not supported.");
        var lease = new FileStream(lockPath, FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
        try
        {
            Profile p;
            if (create)
            {
                if (Directory.EnumerateFileSystemEntries(root).Any(f => !string.Equals(f, lockPath, StringComparison.OrdinalIgnoreCase)))
                    throw new IOException("Workspace folder is not empty.");
                p = new Profile(1, Guid.NewGuid().ToString(), Guid.NewGuid().ToString(),
                    Guid.NewGuid().ToString(), Guid.NewGuid().ToString(), name, identity);
                Validate(p);
                using (var store = new LocalHistory(db, p.Account)) { }
                // Publish only after creating the database; interruption may leave an incomplete
                // folder, which is refused on reopen rather than silently reset.
                byte[] data = JsonSerializer.SerializeToUtf8Bytes(p);
                using var file = new FileStream(meta, FileMode.CreateNew, FileAccess.Write, FileShare.None);
                file.Write(data); file.Flush(flushToDisk: true);
            }
            else
            {
                using var file = new FileStream(meta, FileMode.Open, FileAccess.Read, FileShare.Read);
                if (file.Length is < 1 or > 65536) throw new InvalidDataException("Invalid workspace metadata size.");
                byte[] data = new byte[(int)file.Length];
                file.ReadExactly(data);
                p = JsonSerializer.Deserialize<Profile>(data) ?? throw new InvalidDataException("Invalid workspace metadata.");
                Validate(p);
                using var store = new LocalHistory(db, p.Account);
            }
            var profiles = new List<Profile> { p };
            if (File.Exists(catalog))
            {
                using var stream = new FileStream(catalog, FileMode.Open, FileAccess.Read, FileShare.Read);
                if (stream.Length is < 1 or > 4_194_304) throw new InvalidDataException("Invalid character catalog size.");
                var additional = JsonSerializer.Deserialize<List<Profile>>(stream) ?? throw new InvalidDataException("Invalid catalog.");
                if (additional.Count > 63) throw new InvalidDataException("Too many characters.");
                foreach (var entry in additional)
                {
                    if (entry == null) throw new InvalidDataException("Invalid character.");
                    Validate(entry);
                    if (entry.Account != p.Account || entry.Device != p.Device ||
                        profiles.Any(x => x.Character == entry.Character || x.Chat == entry.Chat))
                        throw new InvalidDataException("Character catalog scope conflict.");
                    profiles.Add(entry);
                }
            }
            var chats = profiles.Select(x => new Conversation(x.Character, x.Chat, "Conversation 1")).ToList();
            if (File.Exists(conversationFile))
            {
                var additional = ReadJson<List<Conversation>>(conversationFile, 1_048_576);
                if (additional.Count > 1024) throw new InvalidDataException("Conversation limit exceeded.");
                foreach (var entry in additional)
                {
                    if (entry == null) throw new InvalidDataException("Invalid conversation.");
                    LocalHistory.Id(entry.Character); LocalHistory.Id(entry.Chat); LocalHistory.ValidateText(entry.Title);
                    if (entry.Title.Length > 120 || !profiles.Any(x => x.Character == entry.Character) ||
                        chats.Any(x => x.Chat == entry.Chat)) throw new InvalidDataException("Conversation catalog scope conflict.");
                    chats.Add(entry);
                }
            }
            var result = new Workspace(p, db, lease, catalog, profiles, conversationFile, selectionFile, chats);
            // Selection is optional UI metadata. Invalid selection never resets history.
            if (File.Exists(selectionFile))
            {
                try
                {
                    var saved = ReadJson<Conversation>(selectionFile, 4096);
                    if (chats.Any(c => c.Character == saved.Character && c.Chat == saved.Chat))
                        result.SelectConversation(saved.Character, saved.Chat);
                }
                catch (Exception ex) when (ex is IOException or JsonException or ArgumentException or InvalidOperationException) { }
            }
            return result;
        }
        catch { lease.Dispose(); throw; }
    }
    // Preserve original workspace.json and all existing event IDs. Additional profiles and
    // conversations are stored separately.
    internal Profile AddCharacter(string name, string identity)
    {
        if (profiles.Count >= 64) throw new InvalidOperationException("Character limit reached.");
        var p = new Profile(1, Character.Account, Character.Device, Guid.NewGuid().ToString(),
            Guid.NewGuid().ToString(), name, identity);
        Validate(p);
        var next = profiles.Skip(1).Append(p).ToArray();
        byte[] data = JsonSerializer.SerializeToUtf8Bytes(next);
        if (data.Length > 4_194_304) throw new InvalidDataException("Character catalog exceeds limit.");
        string temp = catalogPath + "." + Guid.NewGuid().ToString("N") + ".tmp";
        try
        {
            using (var file = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None))
            { file.Write(data); file.Flush(flushToDisk: true); }
            if (File.Exists(catalogPath))
            {
                if ((File.GetAttributes(catalogPath) & FileAttributes.ReparsePoint) != 0)
                    throw new IOException("Linked catalog not supported.");
                File.Replace(temp, catalogPath, null);
            }
            else File.Move(temp, catalogPath);
            profiles.Add(p);
            conversations.Add(new Conversation(p.Character, p.Chat, "Conversation 1"));
            return p;
        }
        finally { if (File.Exists(temp)) File.Delete(temp); }
    }
    private static T ReadJson<T>(string path, long limit) where T : class
    {
        using var file = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.Read);
        if (file.Length < 1 || file.Length > limit) throw new InvalidDataException("Invalid metadata size.");
        return JsonSerializer.Deserialize<T>(file) ?? throw new InvalidDataException("Invalid metadata.");
    }
    private static void WriteJson<T>(string path, T value, int limit)
    {
        var data = JsonSerializer.SerializeToUtf8Bytes(value);
        if (data.Length > limit) throw new InvalidDataException("Metadata limit exceeded.");
        string temp = path + "." + Guid.NewGuid().ToString("N") + ".tmp";
        try
        {
            using (var file = new FileStream(temp, FileMode.CreateNew, FileAccess.Write, FileShare.None))
            { file.Write(data); file.Flush(flushToDisk: true); }
            if (File.Exists(path))
            {
                if ((File.GetAttributes(path) & FileAttributes.ReparsePoint) != 0)
                    throw new IOException("Linked metadata not supported.");
                File.Replace(temp, path, null);
            }
            else File.Move(temp, path);
        }
        finally { if (File.Exists(temp)) File.Delete(temp); }
    }
    internal Conversation AddConversation(string title)
    {
        LocalHistory.ValidateText(title);
        if (title.Length > 120 || conversations.Count - profiles.Count >= 1024)
            throw new InvalidOperationException("Conversation limit exceeded.");
        var entry = new Conversation(Character.Character, Guid.NewGuid().ToString(), title);
        var additional = conversations.Where(c => !profiles.Any(p => p.Chat == c.Chat)).Append(entry).ToArray();
        WriteJson(conversationsPath, additional, 1_048_576);
        conversations.Add(entry);
        return entry;
    }
    internal void SelectCharacter(string id)
    {
        LocalHistory.Id(id);
        var profile = profiles.Single(p => p.Character == id);
        SelectConversation(profile.Character, profile.Chat);
    }
    internal void SelectConversation(string character, string chat)
    {
        LocalHistory.Id(character); LocalHistory.Id(chat);
        var profile = profiles.Single(p => p.Character == character);
        if (!conversations.Any(c => c.Character == character && c.Chat == chat))
            throw new InvalidDataException("Unknown conversation.");
        Character = profile with { Chat = chat };
    }
    internal void SaveSelection()
    {
        WriteJson(selectionPath, conversations.Single(c => c.Character == Character.Character && c.Chat == Character.Chat), 4096);
    }
    internal List<LocalHistory.Event> Recent() => Page(null);

    // Stable cursor, no OFFSET and no unbounded history materialization.
    internal List<LocalHistory.Event> Page((long Clock, string Id)? before)
    {
        using var store = new LocalHistory(Database, Character.Account);
        return store.Page(Character.Character, Character.Chat, 50, before);
    }
    internal string ReadDraft()
    {
        using var store = new LocalHistory(Database, Character.Account);
        return store.ReadDraft(Character.Character, Character.Chat);
    }
    internal void SaveDraft(string text)
    {
        using var store = new LocalHistory(Database, Character.Account);
        store.SaveDraft(Character.Character, Character.Chat, text);
    }
    public void Dispose() => lease.Dispose();
}
