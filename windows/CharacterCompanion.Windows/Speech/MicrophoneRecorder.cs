using NAudio.Wave;

namespace CharacterCompanion.Windows.Speech;

/// <summary>Explicit microphone recording to temporary 16-kHz mono PCM WAV.</summary>
public sealed class MicrophoneRecorder : IDisposable
{
    private const int MaxPcmBytes = 16_000 * 2 * 180; // maximum three minutes
    private WaveInEvent? _mic;
    private WaveFileWriter? _writer;
    private TaskCompletionSource<string>? _stopped;
    private int _bytes;
    public string FilePath { get; private set; } = "";
    public bool IsRecording { get; private set; }

    public void Start()
    {
        if (IsRecording) throw new InvalidOperationException("Already recording.");
        var folder = Path.Combine(Path.GetTempPath(), "CharacterCompanionAudio");
        Directory.CreateDirectory(folder);
        FilePath = Path.Combine(folder, $"cc-{Guid.NewGuid():N}.wav");
        _bytes = 0;
        _stopped = new TaskCompletionSource<string>(TaskCreationOptions.RunContinuationsAsynchronously);
        try
        {
            _writer = new WaveFileWriter(FilePath, new WaveFormat(16_000, 16, 1));
            _mic = new WaveInEvent { WaveFormat = new WaveFormat(16_000, 16, 1), BufferMilliseconds = 100 };
            _mic.DataAvailable += OnDataAvailable;
            _mic.RecordingStopped += OnRecordingStopped;
            _mic.StartRecording();
            IsRecording = true;
        }
        catch
        {
            Dispose();
            throw;
        }
    }

    private void OnDataAvailable(object? sender, WaveInEventArgs e)
    {
        var n = Math.Min(e.BytesRecorded, MaxPcmBytes - _bytes);
        if (n > 0)
        {
            _writer?.Write(e.Buffer, 0, n);
            _bytes += n;
        }
        if (_bytes >= MaxPcmBytes) _mic?.StopRecording();
    }

    private void OnRecordingStopped(object? sender, StoppedEventArgs e)
    {
        IsRecording = false;
        _writer?.Dispose();
        _writer = null;
        _mic?.Dispose();
        _mic = null;
        if (e.Exception is not null) _stopped?.TrySetException(e.Exception);
        else if (_bytes == 0) _stopped?.TrySetException(new InvalidOperationException("Empty recording."));
        else _stopped?.TrySetResult(FilePath);
    }

    public async Task<string> StopAsync()
    {
        if (_stopped is null) throw new InvalidOperationException("Recording was not started.");
        if (IsRecording) _mic?.StopRecording();
        return await _stopped.Task.WaitAsync(TimeSpan.FromSeconds(15));
    }

    public void Dispose()
    {
        IsRecording = false;
        _mic?.Dispose(); _mic = null;
        _writer?.Dispose(); _writer = null;
        _stopped?.TrySetCanceled();
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
