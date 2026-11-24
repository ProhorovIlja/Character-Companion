using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace CharacterCompanion.Windows.Intelligence;

/// <summary>Foreground-only client for an explicitly started trusted local llama.cpp server.</summary>
internal sealed class LocalInferenceClient
{
    internal sealed record Message(string Role, string Content);
    private const int MaxStream = 2 * 1024 * 1024;
    private const int MaxLine = 128 * 1024;
    private const int MaxEvent = 256 * 1024;
    private const int MaxText = 16000;
    private static readonly UTF8Encoding StrictUtf8 = new(false, true);
    private readonly Uri endpoint;
    private readonly string model;
    private readonly string key;

    public LocalInferenceClient(string model, int port, string apiKey)
    {
        if (string.IsNullOrWhiteSpace(model) || model.Length > 256 || model.Any(char.IsControl))
            throw new ArgumentException("Invalid local model alias.");
        _ = StrictUtf8.GetByteCount(model);
        if (port is < 1 or > 65535)
            throw new ArgumentOutOfRangeException(nameof(port));
        if (apiKey is null || !Regex.IsMatch(apiKey, @"\A[A-Za-z0-9_-]{32,128}\z"))
            throw new ArgumentException("Invalid local API key.");
        endpoint = new UriBuilder("http", "127.0.0.1", port, "/v1/chat/completions").Uri;
        this.model = model;
        key = apiKey;
    }

    /// <summary>Returns only a complete reply. Optional fragments are previews, not saved history.</summary>
    public async Task<string> CompleteAsync(IReadOnlyList<Message> messages, int outputTokens = 512,
        int timeoutSeconds = 120, Action<string>? onText = null, CancellationToken cancellationToken = default)
    {
        if (outputTokens is < 1 or > 1024 || timeoutSeconds is < 1 or > 120)
            throw new ArgumentOutOfRangeException(nameof(outputTokens), "Invalid request limits.");
        ArgumentNullException.ThrowIfNull(messages);
        // Snapshot caller data before I/O. No caller-controlled URL, request headers or tools.
        if (messages.Count is < 2 or > 201) throw new ArgumentException("Invalid message count.");
        var snapshot = messages.ToArray();
        if (snapshot.Length is < 2 or > 201 || snapshot[0]?.Role != "system" || snapshot[^1]?.Role != "user")
            throw new ArgumentException("Invalid conversation boundaries.");
        long units = 0;
        for (int i = 0; i < snapshot.Length; i++)
        {
            var message = snapshot[i];
            if (message is null || (i > 0 && message.Role is not ("user" or "assistant")))
                throw new ArgumentException("Invalid message role.");
            ValidateText(message.Content);
            units += message.Content.Length;
            if (units > 64000) throw new ArgumentException("Prompt exceeds text budget.");
        }
        var body = JsonSerializer.SerializeToUtf8Bytes(new
        {
            model,
            messages = snapshot.Select(m => new { role = m.Role, content = m.Content }),
            stream = true,
            max_tokens = outputTokens
        });
        if (body.Length > 256 * 1024) throw new ArgumentException("Prompt exceeds byte budget.");

        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        deadline.CancelAfter(TimeSpan.FromSeconds(timeoutSeconds));
        var stop = deadline.Token;
        stop.ThrowIfCancellationRequested();
        using var handler = new SocketsHttpHandler
        {
            UseProxy = false,
            AllowAutoRedirect = false,
            UseCookies = false,
            AutomaticDecompression = DecompressionMethods.None,
            MaxResponseHeadersLength = 16,
            ConnectTimeout = TimeSpan.FromSeconds(5)
        };
        using var client = new HttpClient(handler) { Timeout = Timeout.InfiniteTimeSpan };
        using var request = new HttpRequestMessage(HttpMethod.Post, endpoint)
        {
            Content = new ByteArrayContent(body),
            Version = HttpVersion.Version11,
            VersionPolicy = HttpVersionPolicy.RequestVersionExact
        };
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", key);
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("text/event-stream"));
        request.Content.Headers.ContentType = new MediaTypeHeaderValue("application/json") { CharSet = "utf-8" };
        using var response = await client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, stop).ConfigureAwait(false);
        if (response.StatusCode != HttpStatusCode.OK ||
            !string.Equals(response.Content.Headers.ContentType?.MediaType, "text/event-stream", StringComparison.OrdinalIgnoreCase) ||
            response.Content.Headers.ContentEncoding.Count != 0 ||
            response.Content.Headers.ContentLength is > MaxStream)
            throw new IOException("Local provider rejected streaming request.");
        using var stream = await response.Content.ReadAsStreamAsync(stop).ConfigureAwait(false);
        return await ReadCompletionAsync(stream, onText, stop).ConfigureAwait(false);
    }

    // Also exercised directly by the standalone smoke project. All bytes and events are bounded.
    internal static async Task<string> ReadCompletionAsync(Stream stream, Action<string>? onText, CancellationToken stop)
    {
        var buffer = new byte[8192];
        using var line = new MemoryStream();
        using var data = new MemoryStream();
        var answer = new StringBuilder();
        var total = 0;
        var finished = false;
        var hasData = false;
        while (true)
        {
            stop.ThrowIfCancellationRequested();
            var read = await stream.ReadAsync(buffer.AsMemory(), stop).ConfigureAwait(false);
            if (read == 0 || read > MaxStream - total)
                throw new IOException("Incomplete or oversized completion stream.");
            total += read;
            for (int i = 0; i < read; i++)
            {
                if (buffer[i] != 10)
                {
                    if (line.Length >= MaxLine) throw new IOException("Oversized SSE line.");
                    line.WriteByte(buffer[i]);
                    continue;
                }
                stop.ThrowIfCancellationRequested();
                var bytes = line.ToArray();
                line.SetLength(0);
                var length = bytes.Length;
                if (length > 0 && bytes[length - 1] == 13) length--;
                if (length == 0)
                {
                    if (!hasData) continue;
                    var json = StrictUtf8.GetString(data.ToArray());
                    data.SetLength(0);
                    hasData = false;
                    if (json == "[DONE]")
                    {
                        if (!finished) throw new IOException("Missing normal completion status.");
                        var result = answer.ToString();
                        ValidateText(result);
                        stop.ThrowIfCancellationRequested();
                        return result;
                    }
                    Consume(json, ref finished, answer, onText);
                    continue;
                }
                // SSE comments and other fields are ignored. No event data after a missing delimiter.
                if (length < 5 || bytes[0] != (byte)'d' || bytes[1] != (byte)'a' ||
                    bytes[2] != (byte)'t' || bytes[3] != (byte)'a' || bytes[4] != (byte)':') continue;
                int start = length > 5 && bytes[5] == 32 ? 6 : 5;
                if (data.Length + (hasData ? 1 : 0) + length - start > MaxEvent)
                    throw new IOException("Oversized SSE event.");
                if (hasData) data.WriteByte(10);
                data.Write(bytes, start, length - start);
                hasData = true;
            }
        }
    }

    private static void Consume(string json, ref bool finished, StringBuilder answer, Action<string>? onText)
    {
        using var document = JsonDocument.Parse(json, new JsonDocumentOptions { MaxDepth = 32 });
        var root = document.RootElement;
        UniqueKeys(root);
        if (root.ValueKind != JsonValueKind.Object ||
            (root.TryGetProperty("error", out var error) && error.ValueKind != JsonValueKind.Null) ||
            !root.TryGetProperty("choices", out var choices) || choices.ValueKind != JsonValueKind.Array)
            throw new IOException("Invalid local completion frame.");
        if (choices.GetArrayLength() == 0) return; // usage-only frame
        if (finished || choices.GetArrayLength() != 1)
            throw new IOException("Unexpected completion choice.");
        var choice = choices[0];
        if (choice.ValueKind != JsonValueKind.Object ||
            (choice.TryGetProperty("index", out var index) &&
                (index.ValueKind != JsonValueKind.Number || !index.TryGetInt32(out int n) || n != 0)) ||
            !choice.TryGetProperty("delta", out var delta) || delta.ValueKind != JsonValueKind.Object)
            throw new IOException("Invalid completion delta.");
        foreach (var field in new[] { "tool_calls", "function_call" })
            if (delta.TryGetProperty(field, out var tool) && tool.ValueKind != JsonValueKind.Null)
                throw new IOException("Tool output is unsupported.");
        if (choice.TryGetProperty("finish_reason", out var reason) && reason.ValueKind != JsonValueKind.Null)
        {
            if (reason.ValueKind != JsonValueKind.String || reason.GetString() != "stop")
                throw new IOException("Generation did not finish normally.");
            finished = true;
        }
        if (!delta.TryGetProperty("content", out var content) || content.ValueKind == JsonValueKind.Null) return;
        if (content.ValueKind != JsonValueKind.String) throw new IOException("Invalid response content.");
        var fragment = content.GetString() ?? throw new IOException("Invalid response text.");
        if (fragment.Length > MaxText - answer.Length) throw new IOException("Reply exceeds text budget.");
        ValidateCharacters(fragment);
        answer.Append(fragment);
        if (fragment.Length != 0) onText?.Invoke(fragment);
    }

    private static void UniqueKeys(JsonElement element)
    {
        if (element.ValueKind == JsonValueKind.Object)
        {
            var seen = new HashSet<string>(StringComparer.Ordinal);
            foreach (var property in element.EnumerateObject())
            {
                if (!seen.Add(property.Name)) throw new IOException("Duplicate completion property.");
                UniqueKeys(property.Value);
            }
        }
        else if (element.ValueKind == JsonValueKind.Array)
            foreach (var child in element.EnumerateArray()) UniqueKeys(child);
    }

    private static void ValidateCharacters(string text)
    {
        _ = StrictUtf8.GetByteCount(text);
        if (text.Any(c => c < 32 && c is not ('\r' or '\n' or '\t')))
            throw new IOException("Invalid control character.");
    }

    private static void ValidateText(string text)
    {
        if (string.IsNullOrWhiteSpace(text) || text.Length > MaxText)
            throw new IOException("Invalid message text.");
        ValidateCharacters(text);
    }
}
