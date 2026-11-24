using System.IO;
using System.Text.Json;
using CharacterCompanion.Windows.Intelligence;

namespace Mandy.NativePreview;

/// <summary>Explicitly created single-character preview workspace. Local trusted folder only.</summary>
internal sealed class Workspace : IDisposable
{
    internal sealed record Profile(int Version, string Account, string Device, string Character, string Chat, string Name, string Identity);
    internal Profile Character { get; private set; }
    private readonly List<Profile> profiles;
    private readonly string catalogPath;
    internal IReadOnlyList<Profile> Profiles => profiles.AsReadOnly();
    internal string Database { get; }
    private readonly FileStream lease;
    private Workspace(Profile profile, string database, FileStream lease, string catalogPath, List<Profile> profiles)
    {
        Character = profile; Database = database; this.lease = lease; this.catalogPath = catalogPath; this.profiles = profiles;
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
        if (create && Directory.EnumerateFileSystemEntries(root).Any())
            throw new IOException("Creation requires an empty folder.");
        if (!create && (!File.Exists(meta) || !File.Exists(db)))
            throw new IOException("Incomplete workspace; existing files have not been reset.");
        foreach (var path in new[] { meta, db, lockPath, db + "-wal", db + "-shm", catalog })
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
            return new Workspace(p, db, lease, catalog, profiles);
        }
        catch { lease.Dispose(); throw; }
    }
    // Preserve original workspace.json and all existing event IDs. Additional profiles are
    // stored separately; this preview keeps one conversation per character.
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
            return p;
        }
        finally { if (File.Exists(temp)) File.Delete(temp); }
    }
    internal void SelectCharacter(string id)
    {
        LocalHistory.Id(id);
        Character = profiles.Single(p => p.Character == id);
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
