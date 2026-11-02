using System.IO;
using System.Text.Json.Serialization;
using System.Windows.Media;

namespace Velvet.Desktop.Models;

public sealed class Track
{
    public string Id { get; set; } = "";
    public string Path { get; set; } = "";
    public string Title { get; set; } = "Untitled";
    public string Artist { get; set; } = "Unknown artist";
    public string Album { get; set; } = "Unknown album";
    public long DurationMs { get; set; }
    public int TrackNumber { get; set; }
    public int Year { get; set; }
    public string? CoverPath { get; set; }
    public uint ColorArgb { get; set; } = 0xFF8B5CF6;
    public long SizeBytes { get; set; }
    public long ModifiedTicks { get; set; }
    public long AddedAt { get; set; }

    [JsonIgnore]
    public string DurationText =>
        TimeSpan.FromMilliseconds(DurationMs).ToString(DurationMs >= 3_600_000 ? @"h\:mm\:ss" : @"m\:ss");

    [JsonIgnore]
    public string Subtitle => string.IsNullOrWhiteSpace(Artist) ? Album : $"{Artist}  ·  {Album}";

    [JsonIgnore]
    public string NumberText => TrackNumber > 0 ? TrackNumber.ToString("00") : "··";
}

public sealed class CloudTrack : System.ComponentModel.INotifyPropertyChanged
{
    private bool _isSelected = true;
    private double _progress;
    private string _status = "Ready";

    public string Name { get; init; } = "";
    public string SizeText { get; init; } = "";
    public string Url { get; init; } = "";
    public IReadOnlyList<string> Mirrors { get; init; } = Array.Empty<string>();
    public string LocalPath { get; init; } = "";
    public long SizeBytes { get; init; }

    public bool IsSelected
    {
        get => _isSelected;
        set => Set(ref _isSelected, value, nameof(IsSelected));
    }

    public double Progress
    {
        get => _progress;
        set => Set(ref _progress, value, nameof(Progress));
    }

    public string Status
    {
        get => _status;
        set => Set(ref _status, value, nameof(Status));
    }

    public event System.ComponentModel.PropertyChangedEventHandler? PropertyChanged;

    private void Set<T>(ref T field, T value, string name)
    {
        if (EqualityComparer<T>.Default.Equals(field, value)) return;
        field = value;
        PropertyChanged?.Invoke(this, new System.ComponentModel.PropertyChangedEventArgs(name));
    }
}

public sealed class AlbumGroup
{
    public string Name { get; init; } = "";
    public string Artist { get; init; } = "";
    public IReadOnlyList<Track> Tracks { get; init; } = Array.Empty<Track>();
    public string? CoverPath { get; init; }
    public string MetaText => $"{Tracks.Count} track{(Tracks.Count == 1 ? "" : "s")}  ·  {Artist}";

    public static List<AlbumGroup> Build(IEnumerable<Track> tracks) => tracks
        .GroupBy(track => string.IsNullOrWhiteSpace(track.Album) ? "Unknown album" : track.Album.ToLowerInvariant())
        .Select(group =>
        {
            var list = group
                .OrderBy(track => track.TrackNumber > 0 ? track.TrackNumber : int.MaxValue)
                .ThenBy(track => track.Title, StringComparer.OrdinalIgnoreCase)
                .ToList();
            var artists = list.Select(track => track.Artist).Distinct().ToList();
            return new AlbumGroup
            {
                Name = list[0].Album,
                Artist = artists.Count > 1 ? "Various artists" : artists[0],
                Tracks = list,
                CoverPath = list.FirstOrDefault(track => track.CoverPath is not null)?.CoverPath
            };
        })
        .OrderBy(album => album.Name, StringComparer.OrdinalIgnoreCase)
        .ToList();
}

public sealed class AppSettings
{
    public string? Folder { get; set; }
    public string Accent { get; set; } = "Amethyst";
    public double Volume { get; set; } = 0.82;
    public bool Shuffle { get; set; }
    public RepeatMode Repeat { get; set; } = RepeatMode.Off;
    public string? LastTrackId { get; set; }
    public double VisualizerSensitivity { get; set; } = 1.35;
    public bool Disco { get; set; }
    public bool IslandEnabled { get; set; }
    public double IslandLeft { get; set; } = double.NaN;
    public double IslandTop { get; set; } = double.NaN;
    public double IslandScale { get; set; } = 1;
    public double IslandScaleX { get; set; }
    public double IslandScaleY { get; set; }
    public double IslandOpacity { get; set; } = 0.92;
    public bool IslandVertical { get; set; }
    public bool IslandMinimal { get; set; }
    public double IslandMinimalWidth { get; set; } = 296;
    public string? CloudFolder { get; set; }
}

public enum RepeatMode
{
    Off,
    All,
    One
}

public sealed record AccentPalette(
    string Key,
    string Label,
    Color Primary,
    Color Bright,
    Color Deep,
    Color Glow,
    Color GradientStart,
    Color GradientEnd);

public static class Accents
{
    public static readonly IReadOnlyList<AccentPalette> All = new[]
    {
        new AccentPalette(
            "Amethyst", "Amethyst",
            Hex(0xA78BFA), Hex(0xC9BCFF), Hex(0x6D28D9), Hex(0x7C3AED),
            Hex(0xB39DFF), Hex(0x6D28D9)),
        new AccentPalette(
            "Gold", "Gold",
            Hex(0xE8C97A), Hex(0xF8E7AE), Hex(0xA87F2E), Hex(0xD4AF37),
            Hex(0xF3DCA0), Hex(0xA87A25)),
        new AccentPalette(
            "Crimson", "Crimson",
            Hex(0xC21E2C), Hex(0xFF5B5B), Hex(0x5A0710), Hex(0x8E101C),
            Hex(0xE23A3E), Hex(0x47060D)),
        new AccentPalette(
            "Azure", "Azure",
            Hex(0x6EA8FF), Hex(0xA9C9FF), Hex(0x1D4ED8), Hex(0x3B82F6),
            Hex(0x8CBDFF), Hex(0x1D4ED8))
    };

    public static AccentPalette For(string? key) =>
        All.FirstOrDefault(palette => string.Equals(palette.Key, key, StringComparison.OrdinalIgnoreCase))
        ?? All[0];

    private static Color Hex(uint rgb) => Color.FromRgb(
        (byte)((rgb >> 16) & 0xFF),
        (byte)((rgb >> 8) & 0xFF),
        (byte)(rgb & 0xFF));
}
