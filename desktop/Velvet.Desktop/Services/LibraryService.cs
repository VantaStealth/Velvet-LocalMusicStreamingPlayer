using System.IO;
using System.Text.RegularExpressions;
using Velvet.Desktop.Models;

namespace Velvet.Desktop.Services;

public sealed class LibraryService
{
    private static readonly HashSet<string> AudioExtensions = new(StringComparer.OrdinalIgnoreCase)
    {
        ".mp3", ".m4a", ".aac", ".flac", ".wav", ".ogg", ".oga", ".opus",
        ".m4b", ".mka", ".mp4a", ".wma"
    };

    private readonly CoverService _covers;

    public LibraryService(CoverService covers)
    {
        _covers = covers;
    }

    public static bool IsAudioFile(string path) =>
        AudioExtensions.Contains(Path.GetExtension(path));

    public async Task<List<Track>> ScanAsync(
        string folder,
        IReadOnlyList<Track> cached,
        IProgress<(int Done, int Total, string Name)>? progress,
        CancellationToken cancellationToken)
    {
        if (!Directory.Exists(folder))
            throw new DirectoryNotFoundException("That folder can't be opened any more.");

        var files = await Task.Run(() => EnumerateAudio(folder).ToArray(), cancellationToken);
        if (files.Length == 0) return new List<Track>();

        var cachedByPath = cached
            .GroupBy(track => track.Path, StringComparer.OrdinalIgnoreCase)
            .ToDictionary(group => group.Key, group => group.First(), StringComparer.OrdinalIgnoreCase);

        var results = new Track?[files.Length];
        var done = 0;
        var parallelism = Math.Clamp(Environment.ProcessorCount / 2, 2, 4);
        var options = new ParallelOptions
        {
            MaxDegreeOfParallelism = parallelism,
            CancellationToken = cancellationToken
        };

        await Parallel.ForEachAsync(Enumerable.Range(0, files.Length), options, (index, token) =>
        {
            token.ThrowIfCancellationRequested();
            var path = files[index];
            try
            {
                var info = new FileInfo(path);
                if (cachedByPath.TryGetValue(path, out var previous)
                    && previous.SizeBytes == info.Length
                    && previous.ModifiedTicks == info.LastWriteTimeUtc.Ticks
                    && CoverStillMatches(previous))
                {
                    results[index] = previous;
                }
                else
                {
                    results[index] = ReadTrack(info, previous);
                }
            }
            catch
            {
                // A single unreadable file should never hide the rest of the library.
            }

            var count = Interlocked.Increment(ref done);
            progress?.Report((count, files.Length, Path.GetFileName(path)));
            return ValueTask.CompletedTask;
        });

        return results
            .Where(track => track is not null)
            .Select(track => track!)
            .OrderBy(track => track.Title, StringComparer.OrdinalIgnoreCase)
            .ThenBy(track => track.Artist, StringComparer.OrdinalIgnoreCase)
            .ToList();
    }

    private static string CoverSeed(string title, string artist) =>
        "track:" + title.Trim().ToLowerInvariant() + "|" + artist.Trim().ToLowerInvariant();

    private bool CoverStillMatches(Track previous)
    {
        if (previous.CoverPath is null || !File.Exists(previous.CoverPath)) return false;
        var name = Path.GetFileName(previous.CoverPath);
        if (name.StartsWith("art_", StringComparison.OrdinalIgnoreCase)) return true;
        var expected = _covers.GeneratedFileName(CoverSeed(previous.Title, previous.Artist));
        return string.Equals(name, expected, StringComparison.OrdinalIgnoreCase);
    }

    private Track ReadTrack(FileInfo info, Track? previous)
    {
        var rawName = Path.GetFileNameWithoutExtension(info.Name).Trim();
        var decorated = Regex.Replace(rawName, @"^\d{1,3}[\s._-]+", "").Trim();
        var segments = decorated.Split(" - ")
            .Select(part => part.Trim())
            .Where(part => part.Length > 0)
            .ToList();
        var fallbackTitle = segments.Count >= 2 ? segments[^1] : (decorated.Length > 0 ? decorated : rawName);
        var fallbackArtist = segments.Count >= 2 ? segments[0] : "Unknown artist";
        var folderAlbum = info.Directory?.Name?.Trim();
        if (string.IsNullOrWhiteSpace(folderAlbum)) folderAlbum = "Unknown album";

        var title = fallbackTitle;
        var artist = fallbackArtist;
        var album = folderAlbum;
        long duration = 0;
        var trackNumber = 0;
        var year = 0;
        byte[]? embedded = null;

        try
        {
            using var tagFile = TagLib.File.Create(info.FullName);
            var tag = tagFile.Tag;
            if (!string.IsNullOrWhiteSpace(tag.Title)) title = tag.Title.Trim();
            var performer = tag.FirstPerformer ?? tag.FirstAlbumArtist;
            if (!string.IsNullOrWhiteSpace(performer)) artist = performer.Trim();
            if (!string.IsNullOrWhiteSpace(tag.Album)) album = tag.Album.Trim();
            duration = (long)tagFile.Properties.Duration.TotalMilliseconds;
            if (tag.Track > 0) trackNumber = (int)tag.Track;
            if (tag.Year > 0) year = (int)tag.Year;

            var picture = tag.Pictures?
                .FirstOrDefault(candidate => candidate.Data?.Data is { Length: > 0 });
            embedded = picture?.Data?.Data;
        }
        catch
        {
            // Unreadable tags fall back to the file name.
        }

        if (string.IsNullOrWhiteSpace(artist)) artist = fallbackArtist;
        if (string.IsNullOrWhiteSpace(album)) album = folderAlbum;
        if (title.Length == 0) title = rawName;

        var seed = CoverSeed(title, artist);

        string? coverPath = null;
        var color = CoverService.PaletteColor(seed);

        if (embedded is { Length: > 0 })
        {
            var saved = _covers.SaveEmbeddedArtwork(embedded, info.FullName);
            if (saved is not null)
            {
                coverPath = saved.Value.Path;
                color = saved.Value.Color;
            }
        }

        if (coverPath is null)
        {
            var generated = _covers.EnsureGeneratedCover(
                seed,
                CoverService.Monogram(title),
                color);
            coverPath = generated.Path;
            color = generated.Color;
        }

        return new Track
        {
            Id = info.FullName,
            Path = info.FullName,
            Title = title,
            Artist = artist,
            Album = album,
            DurationMs = duration,
            TrackNumber = trackNumber,
            Year = year,
            CoverPath = coverPath,
            ColorArgb = color,
            SizeBytes = info.Length,
            ModifiedTicks = info.LastWriteTimeUtc.Ticks,
            AddedAt = previous?.AddedAt ?? DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        };
    }

    private static IEnumerable<string> EnumerateAudio(string root)
    {
        var stack = new Stack<(string Directory, int Depth)>();
        stack.Push((root, 0));
        while (stack.Count > 0)
        {
            var (directory, depth) = stack.Pop();
            if (depth > 24) continue;

            string[] files;
            try
            {
                files = Directory.GetFiles(directory);
            }
            catch
            {
                continue;
            }

            foreach (var file in files)
            {
                if (AudioExtensions.Contains(Path.GetExtension(file))) yield return file;
            }

            string[] children;
            try
            {
                children = Directory.GetDirectories(directory);
            }
            catch
            {
                continue;
            }

            foreach (var child in children)
            {
                try
                {
                    if ((File.GetAttributes(child) & FileAttributes.ReparsePoint) != 0) continue;
                }
                catch
                {
                    continue;
                }
                stack.Push((child, depth + 1));
            }
        }
    }
}
