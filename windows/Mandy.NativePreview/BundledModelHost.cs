using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text.Json;

namespace Mandy.NativePreview;

/// <summary>Owns the optional, app-distributed local model while this window is open.</summary>
internal sealed class BundledModelHost : IDisposable
{
    private const string CpuModelName = "Qwen3-0.6B-Q8_0.gguf";
    private const string GpuModelName = "Qwen3-8B-Q4_K_M.gguf";
    private Process? process;

    internal static string DirectoryPath => Path.Combine(AppContext.BaseDirectory, "LocalModel");
    private static bool HasGpuModel => File.Exists(Path.Combine(DirectoryPath, "ggml-cuda.dll")) &&
        File.Exists(Path.Combine(DirectoryPath, GpuModelName));
    private static string? AvailableModel => File.Exists(Path.Combine(DirectoryPath, "llama-server.exe"))
        ? HasGpuModel ? GpuModelName
            : File.Exists(Path.Combine(DirectoryPath, CpuModelName)) ? CpuModelName : null
        : null;
    internal static bool IsAvailable => AvailableModel != null;
    internal static string DisplayName => HasGpuModel ? "Qwen3-8B on GPU" : "Qwen3-0.6B on CPU";

    internal bool IsRunning => process is { HasExited: false };
    internal int Port { get; private set; }
    internal string ApiKey { get; private set; } = "";

    internal async Task StartAsync(CancellationToken cancellationToken)
    {
        if (IsRunning) return;
        Dispose();
        var model = AvailableModel ?? throw new FileNotFoundException("Bundled local model files are missing.");
        cancellationToken.ThrowIfCancellationRequested();

        using (var listener = new TcpListener(IPAddress.Loopback, 0))
        {
            listener.Start();
            Port = ((IPEndPoint)listener.LocalEndpoint).Port;
        }
        ApiKey = Convert.ToHexString(RandomNumberGenerator.GetBytes(24));
        var start = new ProcessStartInfo(Path.Combine(DirectoryPath, "llama-server.exe"))
        {
            WorkingDirectory = DirectoryPath,
            UseShellExecute = false,
            CreateNoWindow = true
        };
        // Keep the short-lived loopback credential out of the process command line.
        start.Environment["LLAMA_API_KEY"] = ApiKey;
        foreach (var argument in new[]
        {
            "--model", Path.Combine(DirectoryPath, model), "--alias", "mandy-local",
            "--host", "127.0.0.1", "--port", Port.ToString(System.Globalization.CultureInfo.InvariantCulture),
            "--ctx-size", model == GpuModelName ? "8192" : "4096", "--parallel", "1",
            "--threads", Math.Clamp(Environment.ProcessorCount / 2, 1, 8).ToString(),
            "--n-gpu-layers", model == GpuModelName ? "99" : "0"
        }) start.ArgumentList.Add(argument);

        try
        {
            process = Process.Start(start) ?? throw new IOException("Could not start bundled local model.");
            using var handler = new SocketsHttpHandler { UseProxy = false, AllowAutoRedirect = false,
                ConnectTimeout = TimeSpan.FromSeconds(1) };
            using var client = new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(2) };
            using var deadline = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            deadline.CancelAfter(TimeSpan.FromSeconds(45));
            var health = new Uri($"http://127.0.0.1:{Port}/health");
            while (true)
            {
                deadline.Token.ThrowIfCancellationRequested();
                if (!IsRunning) throw new IOException("Bundled local model exited during startup.");
                try
                {
                    using var request = new HttpRequestMessage(HttpMethod.Get, health);
                    request.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", ApiKey);
                    using var response = await client.SendAsync(request, deadline.Token);
                    if (response.IsSuccessStatusCode)
                    {
                        using var data = JsonDocument.Parse(await response.Content.ReadAsStreamAsync(deadline.Token));
                        if (data.RootElement.TryGetProperty("status", out var status) && status.GetString() == "ok")
                            return;
                    }
                }
                catch (HttpRequestException) { }
                catch (TaskCanceledException) when (!deadline.IsCancellationRequested) { }
                await Task.Delay(250, deadline.Token);
            }
        }
        catch
        {
            Dispose();
            throw;
        }
    }

    public void Dispose()
    {
        var child = process;
        process = null;
        if (child != null)
        {
            try { if (!child.HasExited) child.Kill(entireProcessTree: true); }
            catch (InvalidOperationException) { }
            catch (System.ComponentModel.Win32Exception) { }
            child.Dispose();
        }
        Port = 0;
        ApiKey = "";
    }
}
