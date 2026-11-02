using System.IO;
using System.Windows;
using Velvet.Desktop.Models;
using Windows.Media;
using Windows.Media.Core;
using Windows.Media.Playback;
using Windows.Storage;
using Windows.Storage.Streams;

namespace Velvet.Desktop.Services;

public sealed class PlaybackEngine : IDisposable
{
    private readonly MediaPlayer _player;
    private readonly SystemMediaTransportControls? _smtc;
    private readonly Random _random = new();
    private readonly List<Track> _queue = new();
    private readonly List<int> _order = new();
    private int _orderIndex;
    private TimeSpan? _pendingSeek;
    private bool _disposed;

    public PlaybackEngine()
    {
        _player = new MediaPlayer { AutoPlay = false };
        _player.CommandManager.IsEnabled = false;
        _smtc = _player.SystemMediaTransportControls;

        if (_smtc is not null)
        {
            _smtc.IsEnabled = true;
            _smtc.IsPlayEnabled = true;
            _smtc.IsPauseEnabled = true;
            _smtc.IsNextEnabled = false;
            _smtc.IsPreviousEnabled = false;
            _smtc.IsStopEnabled = false;
            _smtc.ShuffleEnabled = false;
            _smtc.AutoRepeatMode = MediaPlaybackAutoRepeatMode.None;
            _smtc.ButtonPressed += OnButtonPressed;
            _smtc.PlaybackPositionChangeRequested += OnPositionRequested;
            _smtc.ShuffleEnabledChangeRequested += OnShuffleRequested;
            _smtc.AutoRepeatModeChangeRequested += OnRepeatRequested;
        }

        _player.MediaOpened += (_, _) => Dispatch(() =>
        {
            ApplyPendingSeek();
            PublishStatus();
            UpdateTimeline();
            StateChanged?.Invoke();
        });
        _player.MediaEnded += (_, _) => Dispatch(HandleMediaEnded);
        _player.MediaFailed += (_, args) => Dispatch(() =>
        {
            PublishStatus();
            PlaybackFailed?.Invoke(args.ErrorMessage);
        });
        _player.PlaybackSession.PlaybackStateChanged += (_, _) => Dispatch(() =>
        {
            UpdateTimeline();
            StateChanged?.Invoke();
        });
    }

    public event Action<Track>? TrackChanged;
    public event Action? StateChanged;
    public event Action? QueueChanged;
    public event Action<string>? PlaybackFailed;

    public Track? Current { get; private set; }
    public IReadOnlyList<Track> Queue => _queue;
    public bool Shuffle { get; private set; }
    public RepeatMode Repeat { get; private set; } = RepeatMode.Off;
    public bool HasTrack => Current is not null;
    public bool IsPlaying
    {
        get
        {
            if (_disposed) return false;
            try
            {
                return _player.PlaybackSession.PlaybackState == MediaPlaybackState.Playing;
            }
            catch
            {
                return false;
            }
        }
    }
    public int QueuePosition => _queue.Count == 0 ? 0 : _orderIndex + 1;
    public int QueueLength => _queue.Count;

    public TimeSpan Position
    {
        get
        {
            if (_pendingSeek is { } pending) return pending;
            try
            {
                return _player.PlaybackSession.Position;
            }
            catch
            {
                return TimeSpan.Zero;
            }
        }
    }

    public TimeSpan Duration
    {
        get
        {
            try
            {
                var natural = _player.PlaybackSession.NaturalDuration;
                if (natural > TimeSpan.Zero) return natural;
            }
            catch
            {
                // Fall back to the tag-derived duration.
            }
            return Current is null ? TimeSpan.Zero : TimeSpan.FromMilliseconds(Current.DurationMs);
        }
    }

    public double Volume
    {
        get => _player.Volume;
        set => _player.Volume = Math.Clamp(value, 0, 1);
    }

    public void PlayQueue(IReadOnlyList<Track> tracks, int startIndex, bool? shuffle = null, bool autoplay = true)
    {
        if (tracks.Count == 0) return;

        _queue.Clear();
        _queue.AddRange(tracks);
        if (shuffle.HasValue) Shuffle = shuffle.Value;
        _smtc?.SetShuffle(Shuffle);
        _smtc?.SetRepeat(Repeat);

        var start = Math.Clamp(startIndex, 0, _queue.Count - 1);
        _order.Clear();
        if (Shuffle)
        {
            var rest = Enumerable.Range(0, _queue.Count).Where(index => index != start).ToList();
            for (var i = rest.Count - 1; i > 0; i--)
            {
                var j = _random.Next(i + 1);
                (rest[i], rest[j]) = (rest[j], rest[i]);
            }
            _order.Add(start);
            _order.AddRange(rest);
            _orderIndex = 0;
        }
        else
        {
            _order.AddRange(Enumerable.Range(0, _queue.Count));
            _orderIndex = start;
        }

        QueueChanged?.Invoke();
        LoadCurrent(autoplay);
    }

    public void TogglePlayPause()
    {
        if (Current is null)
        {
            if (_queue.Count > 0) LoadCurrent(true);
            return;
        }

        if (IsPlaying)
        {
            _player.Pause();
        }
        else
        {
            _player.Play();
        }
        PublishStatus();
        StateChanged?.Invoke();
    }

    public void Play()
    {
        if (Current is null)
        {
            if (_queue.Count > 0) LoadCurrent(true);
            return;
        }
        _player.Play();
        PublishStatus();
    }

    public void Pause()
    {
        _player.Pause();
        PublishStatus();
    }

    public void Next() => Step(1, userInitiated: true);

    public void Previous()
    {
        if (Current is null) return;
        if (Position > TimeSpan.FromSeconds(3))
        {
            Seek(TimeSpan.Zero);
            return;
        }
        Step(-1, userInitiated: true);
    }

    public void Seek(TimeSpan target)
    {
        if (Current is null) return;
        var duration = Duration;
        if (target < TimeSpan.Zero) target = TimeSpan.Zero;
        if (duration > TimeSpan.Zero && target > duration) target = duration;

        try
        {
            if (_player.PlaybackSession.CanSeek)
            {
                _player.PlaybackSession.Position = target;
                _pendingSeek = null;
            }
            else
            {
                _pendingSeek = target;
            }
        }
        catch
        {
            // The source may still be opening; MediaOpened replays the seek.
            _pendingSeek = target;
        }

        UpdateTimeline();
        StateChanged?.Invoke();
    }

    private void ApplyPendingSeek()
    {
        if (_pendingSeek is not { } target) return;
        try
        {
            if (!_player.PlaybackSession.CanSeek) return;
            _player.PlaybackSession.Position = target;
            _pendingSeek = null;
        }
        catch
        {
            // Keep waiting for the next open.
        }
    }

    public void SeekToFraction(double fraction)
    {
        var duration = Duration;
        if (duration <= TimeSpan.Zero) return;
        Seek(TimeSpan.FromTicks((long)(duration.Ticks * Math.Clamp(fraction, 0, 1))));
    }

    public void SetShuffle(bool enabled)
    {
        if (Shuffle == enabled) return;
        Shuffle = enabled;
        RebuildOrderKeepingCurrent();
        _smtc?.SetShuffle(enabled);
        QueueChanged?.Invoke();
    }

    public void CycleRepeat()
    {
        Repeat = Repeat switch
        {
            RepeatMode.Off => RepeatMode.All,
            RepeatMode.All => RepeatMode.One,
            _ => RepeatMode.Off
        };
        _smtc?.SetRepeat(Repeat);
        StateChanged?.Invoke();
    }

    public void SetRepeat(RepeatMode repeat)
    {
        Repeat = repeat;
        _smtc?.SetRepeat(repeat);
        StateChanged?.Invoke();
    }

    public void JumpTo(Track track)
    {
        var position = _order.FindIndex(index => _queue[index].Id == track.Id);
        if (position < 0) return;
        _orderIndex = position;
        LoadCurrent(true);
    }

    public IReadOnlyList<Track> GetUpcoming(int count)
    {
        if (_queue.Count == 0 || count <= 0) return Array.Empty<Track>();
        var upcoming = new List<Track>(count);
        for (var offset = 1; offset <= _queue.Count && upcoming.Count < count; offset++)
        {
            var index = _orderIndex + offset;
            if (index >= _order.Count)
            {
                if (Repeat != RepeatMode.All) break;
                index %= _order.Count;
            }
            upcoming.Add(_queue[_order[index]]);
        }
        return upcoming;
    }

    /// <summary>
    /// Re-publishes the playback clock and status. Called on every UI tick so
    /// shell integrations that read SMTC (media flyout, tray mods) advance with
    /// the island instead of freezing on the position at track open.
    /// </summary>
    public void UpdateTimeline()
    {
        if (_smtc is null || _disposed || Current is null) return;
        PublishStatus();
        var duration = Duration;
        if (duration <= TimeSpan.Zero) return;
        var position = Position;
        if (position < TimeSpan.Zero) position = TimeSpan.Zero;
        if (position > duration) position = duration;

        try
        {
            _smtc.UpdateTimelineProperties(new SystemMediaTransportControlsTimelineProperties
            {
                StartTime = TimeSpan.Zero,
                MinSeekTime = TimeSpan.Zero,
                Position = position,
                MaxSeekTime = duration,
                EndTime = duration
            });
        }
        catch
        {
            // Timeline updates are best-effort while the source is changing.
        }
    }

    private void LoadCurrent(bool autoplay)
    {
        if (_queue.Count == 0 || _order.Count == 0) return;
        var track = _queue[_order[_orderIndex]];
        Current = track;
        _pendingSeek = null;

        try
        {
            var item = new MediaPlaybackItem(MediaSource.CreateFromUri(new Uri(track.Path)));
            var display = item.GetDisplayProperties();
            display.Type = MediaPlaybackType.Music;
            display.MusicProperties.Title = track.Title;
            display.MusicProperties.Artist = track.Artist;
            display.MusicProperties.AlbumTitle = track.Album;
            display.MusicProperties.AlbumArtist = track.Artist;
            _player.Source = item;

            PublishMetadata(track);
            ApplyThumbnail(track, item);
            PublishStatus();
            UpdateTimeline();

            if (autoplay) _player.Play();
            else _player.Pause();
        }
        catch (Exception exception)
        {
            PlaybackFailed?.Invoke(exception.Message);
        }

        _smtc?.SetNextPrevious(_queue.Count > 1 || Repeat != RepeatMode.Off);
        TrackChanged?.Invoke(track);
    }

    private void PublishMetadata(Track track)
    {
        if (_smtc is null) return;
        try
        {
            var updater = _smtc.DisplayUpdater;
            updater.Type = MediaPlaybackType.Music;
            updater.MusicProperties.Title = track.Title;
            updater.MusicProperties.Artist = track.Artist;
            updater.MusicProperties.AlbumTitle = track.Album;
            updater.MusicProperties.AlbumArtist = track.Artist;
            updater.Update();
        }
        catch
        {
            // Metadata is optional for playback.
        }
    }

    private async void ApplyThumbnail(Track track, MediaPlaybackItem item)
    {
        try
        {
            if (track.CoverPath is null || !File.Exists(track.CoverPath)) return;
            var file = await StorageFile.GetFileFromPathAsync(track.CoverPath);
            var reference = RandomAccessStreamReference.CreateFromFile(file);
            if (Current?.Id != track.Id) return;

            item.GetDisplayProperties().Thumbnail = reference;
            if (_smtc is not null)
            {
                _smtc.DisplayUpdater.Thumbnail = reference;
                _smtc.DisplayUpdater.Update();
            }
        }
        catch
        {
            // Artwork in the system flyout is a bonus, never a requirement.
        }
    }

    private void PublishStatus()
    {
        if (_smtc is null || _disposed) return;
        try
        {
            _smtc.PlaybackStatus = _player.PlaybackSession.PlaybackState switch
            {
                MediaPlaybackState.Playing => MediaPlaybackStatus.Playing,
                MediaPlaybackState.Paused => MediaPlaybackStatus.Paused,
                _ => MediaPlaybackStatus.Stopped
            };
        }
        catch (Exception)
        {
            // The session aborts while the player is being torn down or re-sourced.
        }
    }

    private void HandleMediaEnded()
    {
        if (Repeat == RepeatMode.One)
        {
            Seek(TimeSpan.Zero);
            _player.Play();
            return;
        }
        Step(1, userInitiated: false);
    }

    private void Step(int direction, bool userInitiated)
    {
        if (_queue.Count == 0 || _order.Count == 0) return;
        if (direction < 0 && Position > TimeSpan.FromSeconds(3))
        {
            Seek(TimeSpan.Zero);
            return;
        }

        var next = _orderIndex + direction;
        if (next >= _order.Count)
        {
            if (Repeat == RepeatMode.All || userInitiated)
            {
                next = 0;
            }
            else
            {
                _player.Pause();
                PublishStatus();
                StateChanged?.Invoke();
                return;
            }
        }
        else if (next < 0)
        {
            next = Repeat == RepeatMode.All ? _order.Count - 1 : 0;
        }

        _orderIndex = next;
        LoadCurrent(true);
    }

    private void RebuildOrderKeepingCurrent()
    {
        if (_queue.Count == 0) return;
        var currentIndex = Current is null
            ? -1
            : _queue.FindIndex(track => track.Id == Current.Id);

        var rest = Enumerable.Range(0, _queue.Count)
            .Where(index => index != currentIndex)
            .ToList();
        if (Shuffle)
        {
            for (var i = rest.Count - 1; i > 0; i--)
            {
                var j = _random.Next(i + 1);
                (rest[i], rest[j]) = (rest[j], rest[i]);
            }
        }
        else
        {
            rest.Sort();
        }

        _order.Clear();
        if (currentIndex >= 0) _order.Add(currentIndex);
        _order.AddRange(rest);
        _orderIndex = 0;
    }

    private void OnButtonPressed(SystemMediaTransportControls sender, SystemMediaTransportControlsButtonPressedEventArgs args)
    {
        Dispatch(() =>
        {
            switch (args.Button)
            {
                case SystemMediaTransportControlsButton.Play:
                    Play();
                    break;
                case SystemMediaTransportControlsButton.Pause:
                    Pause();
                    break;
                case SystemMediaTransportControlsButton.Next:
                    Next();
                    break;
                case SystemMediaTransportControlsButton.Previous:
                    Previous();
                    break;
            }
        });
    }

    private void OnPositionRequested(
        SystemMediaTransportControls sender, PlaybackPositionChangeRequestedEventArgs args)
    {
        Dispatch(() => Seek(args.RequestedPlaybackPosition));
    }

    private void OnShuffleRequested(
        SystemMediaTransportControls sender, ShuffleEnabledChangeRequestedEventArgs args)
    {
        Dispatch(() => SetShuffle(args.RequestedShuffleEnabled));
    }

    private void OnRepeatRequested(
        SystemMediaTransportControls sender, AutoRepeatModeChangeRequestedEventArgs args)
    {
        Dispatch(() =>
        {
            Repeat = args.RequestedAutoRepeatMode switch
            {
                MediaPlaybackAutoRepeatMode.Track => RepeatMode.One,
                MediaPlaybackAutoRepeatMode.List => RepeatMode.All,
                _ => RepeatMode.Off
            };
            _smtc?.SetRepeat(Repeat);
            StateChanged?.Invoke();
        });
    }

    private void Dispatch(Action action)
    {
        if (_disposed) return;
        var dispatcher = Application.Current?.Dispatcher;
        if (dispatcher is null) return;
        if (dispatcher.CheckAccess()) action();
        else dispatcher.BeginInvoke(new Action(() =>
        {
            if (!_disposed) action();
        }));
    }

    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        try
        {
            _player.Pause();
            if (_smtc is not null) _smtc.IsEnabled = false;
            _player.Dispose();
        }
        catch
        {
            // Closing during a source change can throw; ignore.
        }
        GC.SuppressFinalize(this);
    }
}

internal static class SystemMediaTransportControlsExtensions
{
    public static void SetRepeat(this SystemMediaTransportControls controls, RepeatMode repeat)
    {
        controls.AutoRepeatMode = repeat switch
        {
            RepeatMode.One => MediaPlaybackAutoRepeatMode.Track,
            RepeatMode.All => MediaPlaybackAutoRepeatMode.List,
            _ => MediaPlaybackAutoRepeatMode.None
        };
    }

    public static void SetShuffle(this SystemMediaTransportControls controls, bool enabled)
    {
        controls.ShuffleEnabled = enabled;
    }

    public static void SetNextPrevious(this SystemMediaTransportControls controls, bool enabled)
    {
        controls.IsNextEnabled = enabled;
        controls.IsPreviousEnabled = enabled;
    }
}
