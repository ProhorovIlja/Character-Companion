using System.Text;
using CharacterCompanion.Windows.Intelligence;

int checks = 0;
void Check(bool condition)
{
    if (!condition) throw new InvalidOperationException("Inference smoke check failed.");
    checks++;
}
async Task Reject(Func<Task> action)
{
    try { await action(); }
    catch (Exception ex) when (ex is IOException or ArgumentException or System.Text.Json.JsonException or OperationCanceledException)
    {
        checks++;
        return;
    }
    throw new InvalidOperationException("Expected refusal.");
}
Task<string> Parse(string value, Action<string>? preview = null, CancellationToken stop = default)
    => LocalInferenceClient.ReadCompletionAsync(new MemoryStream(Encoding.UTF8.GetBytes(value)), preview, stop);
const string frame = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Привет 🌙\"},\"finish_reason\":null}]}\n\n";
const string finish = "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n";
const string done = "data: [DONE]\n\n";
var preview = new StringBuilder();
Check(await Parse(frame + finish + done, s => preview.Append(s)) == "Привет 🌙");
Check(preview.ToString() == "Привет 🌙");
Check(await Parse((frame + finish + done).Replace("\n", "\r\n")) == "Привет 🌙");
Check(await Parse(":comment\n\ndata: {\"choices\":\ndata: [{\"delta\":{\"content\":\"Hello\"},\"finish_reason\":\"stop\"}]}\n\n" + done) == "Hello");
Check(await Parse(frame + finish + "data: {\"choices\":[]}\n\n" + done) == "Привет 🌙");
await Reject(() => Parse(frame + done));
await Reject(() => Parse(frame + finish));
await Reject(() => Parse(frame + finish + frame + done));
await Reject(() => Parse(finish + done));
await Reject(() => Parse(frame + finish.Replace("stop", "length") + done));
await Reject(() => Parse("data: {\"choices\":[],\"choices\":[]}\n\n" + done));
await Reject(() => Parse("data: {\"choices\":[{\"delta\":{\"content\":\"one\",\"content\":\"two\"},\"finish_reason\":\"stop\"}]}\n\n" + done));
await Reject(() => Parse("data: {\"choices\":[{\"index\":1,\"delta\":{\"content\":\"x\"}}]}\n\n" + finish + done));
await Reject(() => Parse("data: {\"choices\":[{\"delta\":{\"tool_calls\":[]}}]}\n\n" + finish + done));
await Reject(() => Parse("data: {\"choices\":[{\"delta\":{\"content\":123}}]}\n\n" + finish + done));
await Reject(() => Parse("data: {\"choices\":[{\"delta\":{\"content\":\"\\u0000\"}}]}\n\n" + finish + done));
await Reject(() => Parse("data: " + new string('x', 128 * 1024) + "\n\n"));
await Reject(() => Parse(string.Concat(Enumerable.Repeat(":" + new string('x', 1023) + "\n", 2050))));
await Reject(() => Parse(string.Concat(Enumerable.Repeat("data: " + new string('x', 100000) + "\n", 3)) + "\n"));
await Reject(() => Parse(string.Concat(Enumerable.Repeat(frame, 3000)) + finish + done));
await Reject(() => LocalInferenceClient.ReadCompletionAsync(new MemoryStream(new byte[] { 100,97,116,97,58,32,0xff,10,10 }), null, default));
using (var cancellation = new CancellationTokenSource())
{
    cancellation.Cancel();
    await Reject(() => Parse(frame + finish + done, stop: cancellation.Token));
}
await Reject(() => Task.Run(() => new LocalInferenceClient("mandy-local", 0, new string('a', 48))));
await Reject(() => Task.Run(() => new LocalInferenceClient("mandy-local", 8080, "invalid\r\nkey")));
var client = new LocalInferenceClient("mandy-local", 8080, new string('a', 48));
await Reject(() => client.CompleteAsync([new("system", "Mandy")]));
await Reject(() => client.CompleteAsync([new("system", "Mandy"), new("user", "hello")], outputTokens: 0));
await Reject(() => client.CompleteAsync([new("system", "Mandy"), new("tool", "bad"), new("user", "hello")]));
await Reject(() => client.CompleteAsync([new("system", "Mandy"), new("user", new string('a', 16001))]));
using (var cancellation = new CancellationTokenSource())
{
    cancellation.Cancel();
    await Reject(() => client.CompleteAsync([new("system", "Mandy"), new("user", "hello")], cancellationToken: cancellation.Token));
}
Console.WriteLine($"Windows local inference: {checks} checks passed (synthetic streams, no real model).");
