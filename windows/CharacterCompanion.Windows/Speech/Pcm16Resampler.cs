using System.Text;

namespace CharacterCompanion.Windows.Speech;

/// <summary>
/// Streams PCM16 mono/stereo at common capture rates into 16-kHz mono PCM16.
/// An area-weighted box low-pass avoids blindly mislabeling 44.1/48-kHz audio
/// as 16-kHz and needs only constant memory. Not a full studio-grade resampler.
/// The caller is responsible for the WAV container headers.
/// </summary>
public static class Pcm16Resampler
{
    public const int OutputSampleRate = 16_000;
    public static long Convert(Stream input, Stream output, int inputRate, int channels, long inputBytes)
    {
        if (inputRate is < OutputSampleRate or > 96_000 || channels is < 1 or > 2)
            throw new ArgumentOutOfRangeException(nameof(inputRate), "Unsupported PCM capture format.");
        if (!input.CanRead || !output.CanWrite) throw new ArgumentException("Invalid audio streams.");
        var blockAlign = channels * sizeof(short);
        if (inputBytes <= 0 || inputBytes % blockAlign != 0 ||
            inputBytes > 180L * inputRate * blockAlign)
            throw new ArgumentOutOfRangeException(nameof(inputBytes), "Audio duration/format is invalid.");

        // Already-normalized mono PCM needs no arithmetic or per-sample I/O.
        // ReadExactly rejects truncation, and never reads beyond the stated length.
        if (inputRate == OutputSampleRate && channels == 1)
        {
            var buffer = new byte[16 * 1024];
            try
            {
                long remaining = inputBytes;
                while (remaining > 0)
                {
                    int count = (int)Math.Min(remaining, buffer.Length);
                    input.ReadExactly(buffer, 0, count);
                    output.Write(buffer, 0, count);
                    remaining -= count;
                }
                output.Flush();
                return inputBytes / sizeof(short);
            }
            finally { Array.Clear(buffer); }
        }

        using var reader = new BinaryReader(input, Encoding.UTF8, leaveOpen: true);
        using var writer = new BinaryWriter(output, Encoding.UTF8, leaveOpen: true);
        long accumulated = 0;
        long weight = 0;
        long targetIndex = 0;
        long frames = inputBytes / blockAlign;
        for (long frame = 0; frame < frames; frame++)
        {
            var first = reader.ReadInt16();
            var sample = channels == 2 ? (first + (int)reader.ReadInt16()) / 2 : first;
            var current = frame * OutputSampleRate;
            var end = (frame + 1) * OutputSampleRate;
            while (current < end)
            {
                var nextTarget = (targetIndex + 1) * inputRate;
                var overlap = Math.Min(end, nextTarget) - current;
                if (overlap <= 0) throw new InvalidDataException("Invalid resampling boundaries.");
                accumulated += sample * overlap;
                weight += overlap;
                current += overlap;
                if (current == nextTarget)
                {
                    WriteSample(writer, accumulated, weight);
                    targetIndex++;
                    accumulated = weight = 0;
                }
            }
        }
        if (weight > 0)
        {
            WriteSample(writer, accumulated, weight);
            targetIndex++;
        }
        writer.Flush();
        return targetIndex;
    }

    private static void WriteSample(BinaryWriter writer, long sum, long weight)
    {
        if (weight <= 0) throw new InvalidDataException("Invalid empty audio sample.");
        var value = (int)Math.Round((double)sum / weight, MidpointRounding.AwayFromZero);
        writer.Write((short)Math.Clamp(value, short.MinValue, short.MaxValue));
    }
}
