using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;
using Velvet.Desktop.Models;

namespace Velvet.Desktop.Services;

public sealed class SettingsService
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        NumberHandling = JsonNumberHandling.AllowNamedFloatingPointLiterals
    };

    public SettingsService()
    {
        DataDirectory = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Velvet");
        CoversDirectory = Path.Combine(DataDirectory, "covers");
        Directory.CreateDirectory(DataDirectory);
        Directory.CreateDirectory(CoversDirectory);
    }

    public string DataDirectory { get; }
    public string CoversDirectory { get; }
    private string SettingsPath => Path.Combine(DataDirectory, "settings.json");
    private string LibraryPath => Path.Combine(DataDirectory, "library.json");

    public AppSettings Load()
    {
        try
        {
            if (!File.Exists(SettingsPath)) return new AppSettings();
            return JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(SettingsPath), JsonOptions)
                ?? new AppSettings();
        }
        catch
        {
            return new AppSettings();
        }
    }

    public void Save(AppSettings settings)
    {
        try
        {
            WriteAtomic(SettingsPath, JsonSerializer.Serialize(settings, JsonOptions));
        }
        catch
        {
            // Settings are a convenience; a locked file must never break playback.
        }
    }

    public async Task<List<Track>> LoadLibraryAsync()
    {
        try
        {
            if (!File.Exists(LibraryPath)) return new List<Track>();
            await using var stream = File.OpenRead(LibraryPath);
            return await JsonSerializer.DeserializeAsync<List<Track>>(stream, JsonOptions)
                ?? new List<Track>();
        }
        catch
        {
            return new List<Track>();
        }
    }

    public async Task SaveLibraryAsync(IReadOnlyList<Track> tracks)
    {
        try
        {
            var json = JsonSerializer.Serialize(tracks, JsonOptions);
            await WriteAtomicAsync(LibraryPath, json);
        }
        catch
        {
            // The library cache can be rebuilt by scanning again.
        }
    }

    private static void WriteAtomic(string path, string contents)
    {
        var temp = path + ".tmp";
        File.WriteAllText(temp, contents);
        File.Move(temp, path, true);
    }

    private static async Task WriteAtomicAsync(string path, string contents)
    {
        var temp = path + ".tmp";
        await File.WriteAllTextAsync(temp, contents);
        File.Move(temp, path, true);
    }
}
