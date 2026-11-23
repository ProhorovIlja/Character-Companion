using NAudio.Wave;

namespace CharacterCompanion.Windows.Speech;

/// <summary>
/// Records with a device-supported PCM16 format and normalizes it locally to
/// 16-kHz mono after stop, avoiding the assumption every microphone supports
/// direct 16-kHz recording. All temporary audio remains on this computer.
/// </summary>
public sealed class MicrophoneRecorder : IDisposable
{
    private static readonly (int Rate, int Channels)[] CaptureFormats =
    {
        (48_000, 1), (44_100, 1), (16_000, 1), (48_000, 2), (44_100, 2)
    };
    private WaveInEvent? _mic;
    private WaveFileWriter? _writer;
    private TaskCompletionSource<string>? _stopped;
    private string _nativePath = "";
    private int _maxPcmBytes;
    private int _bytes;
    public string FilePath { get; private set; } = "";
    public bool IsRecording { get; private set; }

    public void Start()
    {
        if (IsRecording) throw new InvalidOperationException("Already recording.");
        var folder = Path.Combine(Path.GetTempPath(), "CharacterCompanionAudio");
        Directory.CreateDirectory(folder);
        var id = Guid.NewGuid().ToString("N");
        FilePath = Path.Combine(folder, $"cc-{id}-16k.wav");
        _nativePath = Path.Combine(folder, $"cc-{id}-capture.wav");
        _bytes = 0;
        Exception? lastError = null;
        foreach (var (rate, channels) in CaptureFormats)
        {
            // Each failed capture attempt must have an independent completion source.
            // Some drivers invoke RecordingStopped even when StartRecording fails.
            _stopped = new TaskCompletionSource<string>(TaskCreationOptions.RunContinuationsAsynchronously);
            var format = new WaveFormat(rate, 16, channels);
            try
            {
                _writer = new WaveFileWriter(_nativePath, format);
                _mic = new WaveInEvent { WaveFormat = format, BufferMilliseconds = 100 };
                _mic.DataAvailable += OnDataAvailable;
                _mic.RecordingStopped += OnRecordingStopped;
                _maxPcmBytes = checked(format.AverageBytesPerSecond * 180);
                IsRecording = true;
                _mic.StartRecording();
                return;
            }
            catch (Exception ex)
            {
                lastError = ex;
                IsRecording = false;
                _mic?.Dispose(); _mic = null;
                _writer?.Dispose(); _writer = null;
                EraseTemporaryRecording(_nativePath);
            }
            catch
            {
                Dispose();
                throw;
            }
        }
        Dispose();
        throw new InvalidOperationException("Microphone cannot record any supported PCM format.", lastError);
    }

    private void OnDataAvailable(object? sender, WaveInEventArgs e)
    {
        if (!ReferenceEquals(sender, _mic) || _writer is null || _mic is null) return;
        var blockAlign = _writer.WaveFormat.BlockAlign;
        var remaining = _maxPcmBytes - _bytes;
        var n = Math.Min(e.BytesRecorded, remaining);
        n -= n % blockAlign; // never truncate a frame between two stereo channels
        if (n > 0)
        {
            _writer.Write(e.Buffer, 0, n);
            _bytes += n;
        }
        if (_bytes >= _maxPcmBytes) _mic.StopRecording();
    }

    private void OnRecordingStopped(object? sender, StoppedEventArgs e)
    {
        if (!ReferenceEquals(sender, _mic)) return;
        IsRecording = false;
        _writer?.Dispose(); _writer = null;
        _mic?.Dispose(); _mic = null;
        if (e.Exception is not null) _stopped?.TrySetException(e.Exception);
        else if (_bytes == 0) _stopped?.TrySetException(new InvalidOperationException("Empty recording."));
        else _stopped?.TrySetResult(_nativePath);
    }

    public async Task<string> StopAsync()
    {
        if (_stopped is null) throw new InvalidOperationException("Recording was not started.");
        if (IsRecording) _mic?.StopRecording();
        try
        {
            var native = await _stopped.Task.WaitAsync(TimeSpan.FromSeconds(15));
            using (var source = new WaveFileReader(native))
            {
                var format = source.WaveFormat;
                if (format.Encoding != WaveFormatEncoding.Pcm || format.BitsPerSample != 16)
                    throw new InvalidDataException("Unsupported source WAV format.");
                using var output = new WaveFileWriter(FilePath, new WaveFormat(16_000, 16, 1));
                Pcm16Resampler.Convert(source, output, format.SampleRate, format.Channels, source.Length);
            }
            return FilePath;
        }
        catch
        {
            EraseTemporaryRecording(FilePath);
            throw;
        }
        finally
        {
            EraseTemporaryRecording(_nativePath);
        }
    }

    /// <summary>
    /// Abandons a recording without PCM conversion or any speech recognition.
    /// Unlike StopAsync this never creates a normalized output WAV.
    /// </summary>
    public async Task CancelAsync()
    {
        try
        {
            if (IsRecording) _mic?.StopRecording();
            if (_stopped is not null)
            {
                try { await _stopped.Task.WaitAsync(TimeSpan.FromSeconds(5)); }
                catch (Exception) { /* Cancellation must still clean up on driver failure. */ }
            }
        }
        finally
        {
            Dispose();
            EraseTemporaryRecording(FilePath);
        }
    }

    public void Dispose()
    {
        IsRecording = false;
        _mic?.Dispose(); _mic = null;
        _writer?.Dispose(); _writer = null;
        _stopped?.TrySetCanceled();
        EraseTemporaryRecording(_nativePath);
    }

    public static void EraseTemporaryRecording(string? path)
    {
        if (!string.IsNullOrWhiteSpace(path))
        {
            try { if (File.Exists(path)) File.Delete(path); } catch (IOException) { }
            catch (UnauthorizedAccessException) { }
        }
    }
}
