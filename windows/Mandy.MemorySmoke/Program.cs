using Mandy.NativePreview;
using System.IO;

var root = Path.Combine(Path.GetTempPath(), "mandy-memory-smoke-" + Guid.NewGuid().ToString("N"));
Directory.CreateDirectory(root);
int checks = 0;
void Check(bool condition, string message)
{
    if (!condition) throw new Exception(message);
    checks++;
}
void Reject(Action action, string message)
{
    bool rejected = false;
    try { action(); }
    catch (Exception ex) when (ex is ArgumentException or InvalidDataException or InvalidOperationException) { rejected = true; }
    Check(rejected, message);
}
try
{
    string account = Guid.NewGuid().ToString(), a = Guid.NewGuid().ToString(), b = Guid.NewGuid().ToString();
    string db = Path.Combine(root, "history.db");
    var store = new CharacterMemory(db, account);
    var blank = store.Read(a);
    Check(blank.Revision == 0 && blank.Stage == "STRANGERS" && blank.Memories.Length == 0, "Default state");
    var state = blank with { Stage = "FRIENDS", Relationship = "Mutual respect.", Pinned = "Prefers tea.",
        Memories = new[] { "Enjoys jazz music", "Jazz concerts and music", "Visited Киото", "Музыка вечером" } };
    store.Save(a, state);
    var reopened = new CharacterMemory(db, account).Read(a);
    Check(reopened.Revision == 1 && reopened.Stage == "FRIENDS" && reopened.Memories.SequenceEqual(state.Memories), "Reopen");
    Check(store.Read(b).Memories.Length == 0, "Other character isolation");
    Check(new CharacterMemory(db, Guid.NewGuid().ToString()).Read(a).Memories.Length == 0, "Other account isolation");
    Reject(() => store.Save(a, state), "Stale revision");
    Reject(() => store.Save(a, reopened with { Memories = new[] { "duplicate", "duplicate" } }), "Duplicate notes");
    Reject(() => store.Save(a, reopened with { Memories = new[] { "line\nbreak" } }), "Multiline notes");
    Reject(() => store.Save(a, reopened with { Stage = "UNKNOWN" }), "Unknown stage");
    string prompt = CharacterMemory.Compose(reopened, "Core identity.", "КИОТО and ＪＡＺＺ");
    Check(prompt.StartsWith("Core identity.", StringComparison.Ordinal), "Core identity preserved");
    Check(prompt.Contains("Pinned memory: Prefers tea."), "Pinned note");
    Check(prompt.Contains("Relevant memory: Visited Киото"), "Cyrillic retrieval");
    Check(prompt.Contains("Relevant memory: Enjoys jazz music"), "Unicode normalized retrieval");
    Check(prompt.IndexOf("Relevant memory: Visited Киото", StringComparison.Ordinal) <
        prompt.IndexOf("Relevant memory: Enjoys jazz music", StringComparison.Ordinal), "Rare term first");
    string constrained = CharacterMemory.Compose(reopened, new string('I', 7999), "query");
    Check(constrained == new string('I', 7999), "No identity truncation");
    var many = reopened with { Memories = Enumerable.Range(0, 128).Select(i => "music note " + i).ToArray() };
    store.Save(a, many);
    Check(store.Read(a).Memories.Length == 128, "128 note capacity");
    string ranked = CharacterMemory.Compose(store.Read(a), "Core identity.", "music");
    Check(ranked.Split("Relevant memory: ").Length - 1 == 4, "Four selected notes maximum");
    Reject(() => store.Save(a, store.Read(a) with { Memories = Enumerable.Range(0, 129).Select(i => "note " + i).ToArray() }), "Capacity limit");
    Console.WriteLine($"PASS: {checks} character memory checks.");
}
finally { Directory.Delete(root, recursive: true); }
