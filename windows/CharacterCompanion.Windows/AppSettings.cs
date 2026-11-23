using System.Text.Json;

namespace CharacterCompanion.Windows;

public sealed class AppSettings
{
    public string WhisperExecutable { get; set; } = "";
    public string WhisperModel { get; set; } = "";
    public string Language { get; set; } = "auto";
    public bool FilterTrackers { get; set; } = true;

    public static string UserFolder => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "CharacterCompanion");
    private static string FilePath => Path.Combine(UserFolder, "settings.json");

    public static AppSettings Load()
    {
        try
        {
            var info = new FileInfo(FilePath);
            if (!info.Exists || info.Length > 32_768) return new AppSettings();
            return JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(FilePath)) ?? new AppSettings();
        }
        catch (Exception) { return new AppSettings(); }
    }

    public void Save()
    {
        Directory.CreateDirectory(UserFolder);
        var tmp = FilePath + ".tmp";
        File.WriteAllText(tmp, JsonSerializer.Serialize(this, new JsonSerializerOptions { WriteIndented = true }));
        File.Move(tmp, FilePath, true);
    }
}
