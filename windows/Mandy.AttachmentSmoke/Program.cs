using Mandy.NativePreview;
using System.Text;

int count = 0;
void Check(bool ok) { if (!ok) throw new Exception("Attachment assertion failed"); count++; }
async Task Reject(Func<Task> run)
{
    bool rejected = false;
    try { await run(); }
    catch (Exception ex) when (ex is ArgumentException or InvalidDataException or OperationCanceledException) { rejected = true; }
    Check(rejected);
}
async Task<string> Read(string s)
{
    using var stream = new MemoryStream(Encoding.UTF8.GetBytes(s));
    return await TextAttachment.ReadAsync(stream, CancellationToken.None);
}
Check(await Read("Hello\nПривет 🌙") == "Hello\nПривет 🌙");
Check(await Read("\uFEFFText") == "Text");
Check((await Read(new string('a', 6000))).Length == 6000);
await Reject(async () => { await Read(new string('a', 6001)); });
await Reject(async () => { await Read(new string('a', 65537)); });
await Reject(async () => { await Read(" \n\t"); });
await Reject(async () => { await Read("a\u0000b"); });
await Reject(async () => { await Read("a\u007Fb"); });
await Reject(async () =>
{
    using var stream = new MemoryStream(new byte[] { 0xC3, 0x28 });
    await TextAttachment.ReadAsync(stream, CancellationToken.None);
});
await Reject(async () =>
{
    using var stream = new MemoryStream(new byte[] { 65 });
    await TextAttachment.ReadAsync(stream, new CancellationToken(true));
});
var edit = TextAttachment.Insert("before SELECT after", 7, 13, "Привет");
Check(edit.Text.StartsWith("before ") && edit.Text.EndsWith(" after"));
Check(edit.Text.Contains("Привет") && !edit.Text.Contains("SELECT"));
Check(edit.Text[edit.Caret..] == " after");
await Reject(() => { TextAttachment.Insert("a", 2, 2, "text"); return Task.CompletedTask; });
await Reject(() => { TextAttachment.Insert(new string('x', 16000), 0, 0, "text"); return Task.CompletedTask; });
await Reject(() => { TextAttachment.Insert("🌙", 1, 1, "text"); return Task.CompletedTask; });
Console.WriteLine($"PASS: {count} Windows attachment assertions");
