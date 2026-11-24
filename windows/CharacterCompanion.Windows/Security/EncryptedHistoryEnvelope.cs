using System;
using System.Security.Cryptography;
using System.Text;

namespace CharacterCompanion.Windows.Security;

/// <summary>Transport envelope only; validate history before importing decrypted bytes.</summary>
internal static class EncryptedHistoryEnvelope
{
    public const int MaxPlaintext = 4 * 1024 * 1024;
    public const int MaxEnvelope = MaxPlaintext + 36;
    private static readonly byte[] Magic = Encoding.ASCII.GetBytes("MANDYH01");

    private static byte[] Binding(byte[] key, string account, ReadOnlySpan<byte> header)
    {
        if (key.Length != 32 || !Guid.TryParseExact(account, "D", out var id) || id.ToString("D") != account)
            throw new ArgumentException("Expected random 256-bit key and canonical account UUID");
        var aad = new byte[56];
        header.CopyTo(aad);
        Encoding.ASCII.GetBytes(account).CopyTo(aad, 20);
        return aad;
    }

    public static byte[] Seal(byte[] key, string account, byte[] data)
    {
        if (data.Length < 1 || data.Length > MaxPlaintext) throw new ArgumentException("Invalid batch size");
        var result = new byte[data.Length + 36];
        Magic.CopyTo(result, 0);
        RandomNumberGenerator.Fill(result.AsSpan(8, 12));
        var aad = Binding(key, account, result.AsSpan(0, 20));
        using var cipher = new AesGcm(key, 16);
        cipher.Encrypt(result.AsSpan(8, 12), data, result.AsSpan(20, data.Length), result.AsSpan(20 + data.Length, 16), aad);
        return result;
    }

    public static byte[] Open(byte[] key, string account, byte[] envelope)
    {
        if (envelope.Length < 37 || envelope.Length > MaxEnvelope || !envelope.AsSpan(0, 8).SequenceEqual(Magic))
            throw new ArgumentException("Invalid encrypted history envelope");
        var aad = Binding(key, account, envelope.AsSpan(0, 20));
        var data = new byte[envelope.Length - 36];
        using var cipher = new AesGcm(key, 16);
        try
        {
            cipher.Decrypt(envelope.AsSpan(8, 12), envelope.AsSpan(20, data.Length), envelope.AsSpan(20 + data.Length, 16), data, aad);
            return data;
        }
        catch { CryptographicOperations.ZeroMemory(data); throw; }
    }
}
