using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using Velvet.Desktop.Models;

namespace Velvet.Desktop.Services;

public static class CloudLibraryService
{
    public const string ManifestUrl =
        "https://github.com/VantaStealth/Velvet-LocalMusicStreamingPlayer/releases/download/velvet-cloud/manifest.json?v=10";

    // If the release asset is unavailable, the same manifest is mirrored in the
    // repository itself so clients can still discover backup track sources.
    private static readonly string[] ManifestSources =
    {
        ManifestUrl,
        "https://raw.githubusercontent.com/VantaStealth/Velvet-LocalMusicStreamingPlayer/main/tools/velvet-cloud-manifest.json?v=10",
        "https://cdn.jsdelivr.net/gh/VantaStealth/Velvet-LocalMusicStreamingPlayer@main/tools/velvet-cloud-manifest.json?v=10"
    };

    private const string Owner = "VantaStealth";
    private const string Repo = "Velvet-LocalMusicStreamingPlayer";
    private const string ReleaseTag = "velvet-cloud";

    private static readonly HashSet<string> AudioExtensions = new(StringComparer.OrdinalIgnoreCase)
    {
        ".mp3", ".m4a", ".aac", ".flac", ".wav", ".ogg", ".oga", ".opus", ".wma"
    };

    private static readonly HttpClient Http = new()
    {
        Timeout = TimeSpan.FromMinutes(30)
    };

    private static IReadOnlyList<CloudEntry>? _cache;
    private static DateTime _cacheAt;

    public sealed record CloudEntry(string Name, string Url, long Size)
    {
        public IReadOnlyList<string> Mirrors { get; init; } = Array.Empty<string>();
        public string? Lyrics { get; init; }

        public IEnumerable<string> Sources =>
            new[] { Url }.Concat(Mirrors).Where(source => !string.IsNullOrWhiteSpace(source));
    }

    public static async Task<IReadOnlyList<CloudEntry>> GetCloudAsync(bool force, CancellationToken token)
    {
        if (!force && _cache is not null && DateTime.UtcNow - _cacheAt < TimeSpan.FromMinutes(2))
            return _cache;

        JsonDocument? document = null;
        Exception? lastError = null;
        foreach (var source in ManifestSources)
        {
            try
            {
                using var response = await Http.GetAsync(source, HttpCompletionOption.ResponseHeadersRead, token);
                response.EnsureSuccessStatusCode();
                await using var stream = await response.Content.ReadAsStreamAsync(token);
                document = await JsonDocument.ParseAsync(stream, cancellationToken: token);
                break;
            }
            catch (Exception exception) when (exception is not OperationCanceledException)
            {
                lastError = exception;
            }
        }

        if (document is null)
            throw lastError ?? new InvalidOperationException("Velvet Cloud could not be reached.");

        using (document)
        {
            var tracks = new List<CloudEntry>();
            if (document.RootElement.TryGetProperty("tracks", out var array))
            {
                foreach (var item in array.EnumerateArray())
                {
                    var name = item.GetProperty("name").GetString();
                    var url = item.GetProperty("url").GetString();
                    if (string.IsNullOrWhiteSpace(name) || string.IsNullOrWhiteSpace(url)) continue;
                    var size = item.TryGetProperty("size", out var sizeElement) ? sizeElement.GetInt64() : 0L;

                    var mirrors = new List<string>();
                    if (item.TryGetProperty("mirrors", out var mirrorArray) &&
                        mirrorArray.ValueKind == JsonValueKind.Array)
                    {
                        foreach (var mirror in mirrorArray.EnumerateArray())
                        {
                            var value = mirror.GetString();
                            if (!string.IsNullOrWhiteSpace(value)) mirrors.Add(value);
                        }
                    }

                    tracks.Add(new CloudEntry(name, url, size)
                    {
                        Mirrors = mirrors,
                        Lyrics = item.TryGetProperty("lyrics", out var lyricsElement)
                            ? lyricsElement.GetString()
                            : null
                    });
                }
            }

            _cache = tracks;
            _cacheAt = DateTime.UtcNow;
            return tracks;
        }
    }

    public static IReadOnlyList<CloudEntry> MissingLocally(IReadOnlyList<CloudEntry> cloud, string folder)
    {
        if (!Directory.Exists(folder)) return cloud;
        var existing = new HashSet<string>(StringComparer.Ordinal);
        foreach (var file in Directory.EnumerateFiles(folder, "*", SearchOption.TopDirectoryOnly))
            existing.Add(NameKey(Path.GetFileName(file)));
        return cloud.Where(entry => !existing.Contains(NameKey(entry.Name))).ToList();
    }

    public static IReadOnlyList<CloudEntry> MissingRemotely(string folder, IReadOnlyList<CloudEntry> cloud)
    {
        if (!Directory.Exists(folder)) return Array.Empty<CloudEntry>();
        var existing = new HashSet<string>(cloud.Select(entry => NameKey(entry.Name)), StringComparer.Ordinal);
        return Directory.EnumerateFiles(folder, "*", SearchOption.TopDirectoryOnly)
            .Where(IsAudio)
            .Where(file => !existing.Contains(NameKey(Path.GetFileName(file))))
            .OrderBy(Path.GetFileName, StringComparer.OrdinalIgnoreCase)
            .Select(file => new CloudEntry(Path.GetFileName(file), "", new FileInfo(file).Length))
            .ToList();
    }

    private static string NameKey(string name) =>
        new string(name.ToLowerInvariant().Where(char.IsLetterOrDigit).ToArray());

    public static async Task DownloadAsync(CloudEntry entry, string folder, CloudTrack item,
        CancellationToken token)
    {
        Directory.CreateDirectory(folder);
        var destination = Path.Combine(folder, entry.Name);
        var temp = destination + ".part";

        Exception? lastError = null;
        foreach (var source in entry.Sources)
        {
            try
            {
                using var response = await Http.GetAsync(source, HttpCompletionOption.ResponseHeadersRead, token);
                response.EnsureSuccessStatusCode();
                await using var input = await response.Content.ReadAsStreamAsync(token);
                await using (var output = new FileStream(temp, FileMode.Create, FileAccess.Write, FileShare.None,
                                 1 << 20, FileOptions.Asynchronous))
                {
                    var buffer = new byte[1 << 20];
                    long copied = 0;
                    int read;
                    while ((read = await input.ReadAsync(buffer, token)) > 0)
                    {
                        await output.WriteAsync(buffer.AsMemory(0, read), token);
                        copied += read;
                        var total = entry.Size > 0 ? entry.Size : item.SizeBytes;
                        item.Progress = total <= 0 ? 0 : Math.Clamp((double)copied / total, 0, 1);
                    }
                }

                File.Move(temp, destination, true);
                if (!string.IsNullOrWhiteSpace(entry.Lyrics))
                {
                    var lyricName = Path.GetFileNameWithoutExtension(entry.Name) + ".lrc";
                    var lyricPath = Path.Combine(folder, lyricName);
                    try
                    {
                        using var lyricResponse = await Http.GetAsync(entry.Lyrics, token);
                        lyricResponse.EnsureSuccessStatusCode();
                        await File.WriteAllTextAsync(
                            lyricPath,
                            await lyricResponse.Content.ReadAsStringAsync(token),
                            token);
                    }
                    catch (Exception exception) when (exception is not OperationCanceledException)
                    {
                        // The audio is already in place; lyrics can be fetched later.
                    }
                }
                item.Progress = 1;
                return;
            }
            catch (Exception exception) when (exception is not OperationCanceledException)
            {
                lastError = exception;
                try { File.Delete(temp); } catch { /* next source gets a clean file */ }
            }
        }

        throw lastError ?? new InvalidOperationException($"No source is available for {entry.Name}.");
    }

    public static async Task<CloudEntry?> UploadAsync(string localPath, CloudTrack item,
        CancellationToken token)
    {
        var githubToken = ReadGitHubToken()
            ?? throw new InvalidOperationException(
                "Publishing needs GitHub credentials. Sign in with Git on this PC first.");

        var releaseId = await GetReleaseIdAsync(githubToken, token);
        var bytes = await File.ReadAllBytesAsync(localPath, token);
        var name = Path.GetFileName(localPath);
        item.Progress = 0.35;

        var url = await UploadAssetAsync(githubToken, releaseId, name, bytes, token);
        item.Progress = 0.8;

        string? lyricUrl = null;
        var lyricPath = Path.ChangeExtension(localPath, ".lrc");
        if (File.Exists(lyricPath))
        {
            try
            {
                var lyricBytes = await File.ReadAllBytesAsync(lyricPath, token);
                lyricUrl = await UploadAssetAsync(
                    githubToken, releaseId, Path.GetFileName(lyricPath), lyricBytes, token);
            }
            catch (Exception exception) when (exception is not OperationCanceledException)
            {
                // The audio is still published; the sidecar can be retried later.
            }
        }

        return new CloudEntry(name, url, bytes.Length) { Lyrics = lyricUrl };
    }

    private static async Task<string> UploadAssetAsync(
        string githubToken, long releaseId, string name, byte[] bytes, CancellationToken token)
    {
        using var request = new HttpRequestMessage(HttpMethod.Post,
            $"https://uploads.github.com/repos/{Owner}/{Repo}/releases/{releaseId}/assets?name={Uri.EscapeDataString(name)}");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", githubToken);
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/vnd.github+json"));
        request.Headers.UserAgent.ParseAdd("Velvet-Desktop");
        request.Content = new ByteArrayContent(bytes);
        request.Content.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");

        using var response = await Http.SendAsync(request, token);
        var body = await response.Content.ReadAsStringAsync(token);
        if (!response.IsSuccessStatusCode)
            throw new InvalidOperationException($"GitHub rejected {name} ({(int)response.StatusCode}).");

        using var document = JsonDocument.Parse(body);
        return document.RootElement.GetProperty("browser_download_url").GetString() ?? "";
    }

    public static async Task PublishManifestAsync(IReadOnlyList<CloudEntry> entries, CancellationToken token)
    {
        var githubToken = ReadGitHubToken()
            ?? throw new InvalidOperationException("Publishing needs GitHub credentials on this PC.");

        var releaseId = await GetReleaseIdAsync(githubToken, token);
        var existingManifestId = await GetManifestAssetIdAsync(githubToken, releaseId, token);
        if (existingManifestId is not null)
        {
            using var delete = new HttpRequestMessage(HttpMethod.Delete,
                $"https://api.github.com/repos/{Owner}/{Repo}/releases/assets/{existingManifestId}");
            delete.Headers.Authorization = new AuthenticationHeaderValue("Bearer", githubToken);
            delete.Headers.UserAgent.ParseAdd("Velvet-Desktop");
            using var deleted = await Http.SendAsync(delete, token);
            if (!deleted.IsSuccessStatusCode && deleted.StatusCode != System.Net.HttpStatusCode.NotFound)
                throw new InvalidOperationException("Could not replace the cloud manifest.");
        }

        var manifest = JsonSerializer.Serialize(new
        {
            name = "Velvet Cloud",
            version = 4,
            updated = DateTime.UtcNow.ToString("yyyy-MM-dd"),
            tracks = entries.Select(entry => new
            {
                name = entry.Name,
                size = entry.Size,
                url = entry.Url,
                mirrors = entry.Mirrors.Count > 0 ? entry.Mirrors.ToArray() : null,
                lyrics = entry.Lyrics
            })
        });

        using var upload = new HttpRequestMessage(HttpMethod.Post,
            $"https://uploads.github.com/repos/{Owner}/{Repo}/releases/{releaseId}/assets?name=manifest.json");
        upload.Headers.Authorization = new AuthenticationHeaderValue("Bearer", githubToken);
        upload.Headers.UserAgent.ParseAdd("Velvet-Desktop");
        upload.Content = new StringContent(manifest, Encoding.UTF8, "application/json");
        using var uploaded = await Http.SendAsync(upload, token);
        if (!uploaded.IsSuccessStatusCode)
            throw new InvalidOperationException("Could not publish the updated cloud manifest.");

        _cache = null;
    }

    private static async Task<long> GetReleaseIdAsync(string githubToken, CancellationToken token)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get,
            $"https://api.github.com/repos/{Owner}/{Repo}/releases/tags/{ReleaseTag}");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", githubToken);
        request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/vnd.github+json"));
        request.Headers.UserAgent.ParseAdd("Velvet-Desktop");
        using var response = await Http.SendAsync(request, token);
        if (!response.IsSuccessStatusCode)
            throw new InvalidOperationException("The Velvet Cloud release could not be found.");
        using var document = JsonDocument.Parse(await response.Content.ReadAsStringAsync(token));
        return document.RootElement.GetProperty("id").GetInt64();
    }

    private static async Task<long?> GetManifestAssetIdAsync(string githubToken, long releaseId,
        CancellationToken token)
    {
        using var request = new HttpRequestMessage(HttpMethod.Get,
            $"https://api.github.com/repos/{Owner}/{Repo}/releases/{releaseId}/assets?per_page=100");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", githubToken);
        request.Headers.UserAgent.ParseAdd("Velvet-Desktop");
        using var response = await Http.SendAsync(request, token);
        if (!response.IsSuccessStatusCode) return null;
        using var document = JsonDocument.Parse(await response.Content.ReadAsStringAsync(token));
        foreach (var asset in document.RootElement.EnumerateArray())
        {
            if (asset.GetProperty("name").GetString() == "manifest.json")
                return asset.GetProperty("id").GetInt64();
        }
        return null;
    }

    private static string? ReadGitHubToken()
    {
        foreach (var executable in new[] { "git", @"C:\Program Files\Git\cmd\git.exe" })
        {
            try
            {
                var start = new ProcessStartInfo(executable, "credential fill")
                {
                    RedirectStandardInput = true,
                    RedirectStandardOutput = true,
                    UseShellExecute = false,
                    CreateNoWindow = true
                };
                using var process = Process.Start(start);
                if (process is null) continue;
                process.StandardInput.Write("protocol=https\nhost=github.com\n\n");
                process.StandardInput.Close();
                var output = process.StandardOutput.ReadToEnd();
                process.WaitForExit(5000);
                var token = output.Split('\n')
                    .FirstOrDefault(line => line.StartsWith("password=", StringComparison.Ordinal));
                if (!string.IsNullOrWhiteSpace(token)) return token[9..].Trim();
            }
            catch
            {
                // Try the next git location.
            }
        }
        return null;
    }

    private static bool IsAudio(string path) => AudioExtensions.Contains(Path.GetExtension(path));
}
