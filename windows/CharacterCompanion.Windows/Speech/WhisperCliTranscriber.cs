using System.Diagnostics;
using System.IO;
using System.Text;

namespace CharacterCompanion.Windows.Speech;

/// <summary>Runs user-selected whisper-cli.exe and local model; no network or cloud STT.</summary>
public static class WhisperCliTranscriber
{
    public static async Task<string> TranscribeAsync(string executable, string model, string wavPath,
        string language, CancellationToken cancellationToken = default)
    {
        if (!File.Exists(executable) || !Path.GetFileName(executable).Equals("whisper-cli.exe", StringComparison.OrdinalIgnoreCase))
            throw new FileNotFoundException("Choose a trusted whisper-cli.exe from the official whisper.cpp release.");
        if (!File.Exists(model) || !Path.GetExtension(model).Equals(".bin", StringComparison.OrdinalIgnoreCase))
            throw new FileNotFoundException("Choose a local GGML .bin model.");
        if (!File.Exists(wavPath)) throw new FileNotFoundException("Recording file missing.");
        if (new FileInfo(wavPath).Length > 6_000_000) throw new InvalidOperationException("Audio exceeds three-minute safety limit.");

        var tempPrefix = Path.Combine(Path.GetTempPath(), "cc-whisper-" + Guid.NewGuid().ToString("N"));
        var resultPath = tempPrefix + ".txt";
        var arguments = new[] { "-m", model, "-f", wavPath, "-l", language is "ru" or "en" ? language : "auto",
                                "-t", Math.Clamp(Environment.ProcessorCount / 2, 1, 8).ToString(),
                                "-otxt", "-of", tempPrefix, "-np" };
        using var process = new Process();
        process.StartInfo = new ProcessStartInfo(executable)
        {
            WorkingDirectory = Path.GetDirectoryName(executable)!,
            UseShellExecute = false,
            CreateNoWindow = true,
            RedirectStandardError = true,
            RedirectStandardOutput = true,
        };
        foreach (var argument in arguments) process.StartInfo.ArgumentList.Add(argument);
        try
        {
            // Important if the window was closed after WAV conversion but before
            // starting the model; do not spawn an already-cancelled transcription.
            cancellationToken.ThrowIfCancellationRequested();
            if (!process.Start()) throw new InvalidOperationException("Cannot start whisper.cpp.");
            using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
            timeout.CancelAfter(TimeSpan.FromMinutes(4));
            // Drain the two child-process pipes concurrently using fixed-size buffers.
            // ReadToEndAsync would retain an unbounded amount of model/debug output in RAM,
            // even though recognition reads the separately written .txt file instead.
            await Task.WhenAll(
                process.WaitForExitAsync(timeout.Token),
                DrainOutputAsync(process.StandardOutput, timeout.Token),
                DrainOutputAsync(process.StandardError, timeout.Token));
            if (process.ExitCode != 0) throw new InvalidOperationException($"whisper.cpp exited with code {process.ExitCode}.");
            if (!File.Exists(resultPath)) throw new IOException("Whisper text output was not produced.");
            if (new FileInfo(resultPath).Length > 100_000) throw new IOException("Transcription output exceeds limit.");
            var text = (await File.ReadAllTextAsync(resultPath, Encoding.UTF8, cancellationToken)).Trim();
            if (string.IsNullOrWhiteSpace(text)) throw new InvalidOperationException("No recognized speech.");
            return text;
        }
        catch
        {
            try
            {
                if (!process.HasExited)
                {
                    process.Kill(entireProcessTree: true);
                    // Do not race temporary-text cleanup against a still-running child.
                    await process.WaitForExitAsync(CancellationToken.None)
                        .WaitAsync(TimeSpan.FromSeconds(5));
                }
            }
            catch (InvalidOperationException) { }
            catch (System.ComponentModel.Win32Exception) { }
            catch (TimeoutException) { /* A locked file may remain; report via the original error. */ }
            throw;
        }
        finally
        {
            try { if (File.Exists(resultPath)) File.Delete(resultPath); } catch (IOException) { }
            catch (UnauthorizedAccessException) { }
        }
    }

    private static async Task DrainOutputAsync(StreamReader reader, CancellationToken cancellationToken)
    {
        var buffer = new char[4 * 1024];
        while (await reader.ReadAsync(buffer.AsMemory(), cancellationToken).ConfigureAwait(false) != 0)
        {
            // No log/transcript retention: output is intentionally discarded.
        }
    }
}
