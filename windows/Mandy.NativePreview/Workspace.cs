using System.IO;
using System.Text.Json;
using CharacterCompanion.Windows.Intelligence;

namespace Mandy.NativePreview;

/// <summary>Explicitly created single-character preview workspace. Local trusted folder only.</summary>
internal sealed class Workspace : IDisposable
{
    internal sealed record Profile(int Version, string Account, string Device, string Character, string Chat, string Name, string Identity);
    internal Profile Character { get; }
    internal string Database { get; }
    private readonly FileStream lease;
    private Workspace(Profile profile, string database, FileStream lease)
    {
        Character = profile; Database = database; this.lease = lease;
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
        if (create && Directory.EnumerateFileSystemEntries(root).Any())
            throw new IOException("Creation requires an empty folder.");
        if (!create && (!File.Exists(meta) || !File.Exists(db)))
            throw new IOException("Incomplete workspace; existing files have not been reset.");
        foreach (var path in new[] { meta, db, lockPath, db + "-wal", db + "-shm" })
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
            return new Workspace(p, db, lease);
        }
        catch { lease.Dispose(); throw; }
    }
    internal List<LocalHistory.Event> Recent()
    {
        using var store = new LocalHistory(Database, Character.Account);
        return store.Page(Character.Character, Character.Chat, 50);
    }
    public void Dispose() => lease.Dispose();
}
