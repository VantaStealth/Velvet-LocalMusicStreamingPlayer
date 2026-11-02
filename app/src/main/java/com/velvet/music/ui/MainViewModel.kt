package com.velvet.music.ui

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.velvet.music.data.CloudApi
import com.velvet.music.data.CloudEntry
import com.velvet.music.data.LibraryRepository
import com.velvet.music.data.Prefs
import com.velvet.music.data.Track
import com.velvet.music.data.VelvetAccent
import com.velvet.music.playback.PlaybackService
import com.velvet.music.playback.SpectrumBus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlin.random.Random

data class ScanState(
    val active: Boolean = false,
    val done: Int = 0,
    val total: Int = 0,
    val currentFile: String = "",
    val error: String? = null
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

data class LibraryState(
    val loading: Boolean = true,
    val folderUri: String? = null,
    val folderName: String = "",
    val tracks: List<Track> = emptyList(),
    val scan: ScanState = ScanState()
)

data class CloudItem(
    val name: String,
    val url: String,
    val sizeBytes: Long,
    val selected: Boolean = true,
    val progress: Float = 0f,
    val status: String = "",
    val failed: Boolean = false
) {
    val sizeLabel: String
        get() = when {
            sizeBytes >= 1_073_741_824L -> String.format("%.1f GB", sizeBytes / 1_073_741_824.0)
            sizeBytes >= 1_048_576 -> String.format("%.1f MB", sizeBytes / 1_048_576.0)
            sizeBytes >= 1024 -> "${sizeBytes / 1024} KB"
            else -> "$sizeBytes B"
        }
}

data class CloudState(
    val open: Boolean = false,
    val items: List<CloudItem> = emptyList(),
    val loading: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val suggest: Int = 0
) {
    val selectedCount: Int get() = items.count { it.selected }
}

data class QueueEntry(
    val mediaId: String,
    val title: String,
    val artist: String,
    val artUri: String?,
    val color: Int
)

data class PlayerState(
    val connected: Boolean = false,
    val hasItem: Boolean = false,
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artUri: String? = null,
    val trackColor: Int = 0xFF8B5CF6.toInt(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val index: Int = 0,
    val queue: List<QueueEntry> = emptyList(),
    val error: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val prefs = Prefs(application)
    private val repository = LibraryRepository(application)

    private val _library = MutableStateFlow(LibraryState())
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _player = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _player.asStateFlow()

    private val _accent = MutableStateFlow(prefs.accent)
    val accent: StateFlow<VelvetAccent> = _accent.asStateFlow()

    private val _favorites = MutableStateFlow(prefs.favorites)
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _spectrumGain = MutableStateFlow(prefs.spectrumGain)
    val spectrumGain: StateFlow<Float> = _spectrumGain.asStateFlow()

    private val _appVolume = MutableStateFlow(prefs.appVolume)
    val appVolume: StateFlow<Float> = _appVolume.asStateFlow()

    private val _cloud = MutableStateFlow(CloudState())
    val cloud: StateFlow<CloudState> = _cloud.asStateFlow()

    val spectrum: StateFlow<FloatArray> = SpectrumBus.bands
    val spectrumLive: StateFlow<Boolean> = SpectrumBus.live

    @Volatile
    private var trackMap: Map<String, Track> = emptyMap()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    @Volatile
    private var controller: MediaController? = null
    private var scanJob: Job? = null
    private var autoSyncJob: Job? = null
    private var cloudJob: Job? = null
    private var lastCloudSuggestion = 0L
    private var pendingPlay: Pair<List<Track>, Int>? = null
    @Volatile
    private var foreground = false
    private var lastAutoCheck = 0L

    init {
        viewModelScope.launch {
            val cached = repository.loadLibrary()
            val savedFolder = prefs.folderUri
            trackMap = cached.associateBy { it.id }
            _library.value = LibraryState(
                loading = false,
                folderUri = savedFolder,
                folderName = prefs.folderName.orEmpty(),
                tracks = cached
            )
            connectController()
            if (savedFolder != null) {
                if (cached.isEmpty()) {
                    startScan(Uri.parse(savedFolder))
                } else {
                    autoSync(force = true)
                }
                refreshSuggestion(force = true)
            }
        }
    }

    private fun connectController() {
        val token = SessionToken(
            app,
            ComponentName(app, PlaybackService::class.java)
        )
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val result = runCatching { future.get() }
            result.onSuccess { mediaController ->
                controller = mediaController
                mediaController.addListener(playerListener)
                mediaController.shuffleModeEnabled = prefs.shuffle
                mediaController.repeatMode = prefs.repeatMode
                mediaController.volume = prefs.appVolume
                _player.update { it.copy(connected = true) }
                val pending = pendingPlay
                when {
                    pending != null -> {
                        pendingPlay = null
                        playTracks(pending.first, pending.second, prefs.shuffle)
                    }
                    mediaController.mediaItemCount == 0 -> restoreQueue()
                }
                syncState()
                startProgressTicker()
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun startProgressTicker() {
        viewModelScope.launch {
            var lastCheck = 0L
            while (isActive) {
                val now = System.currentTimeMillis()
                if (foreground && now - lastCheck >= 120_000L) {
                    lastCheck = now
                    autoSync()
                }
                val mediaController = controller
                if (mediaController != null && _player.value.hasItem) {
                    val duration = effectiveDuration(mediaController)
                    _player.update {
                        it.copy(
                            positionMs = mediaController.currentPosition.coerceAtLeast(0L),
                            durationMs = duration,
                            isBuffering = mediaController.playbackState == Player.STATE_BUFFERING,
                            isPlaying = mediaController.isPlaying
                        )
                    }
                }
                delay(500L)
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _player.update { it.copy(error = null) }
            syncState()
            persistQueueState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            syncState()
            if (!isPlaying) persistQueueState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncState()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            prefs.repeatMode = repeatMode
            syncState()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            prefs.shuffle = shuffleModeEnabled
            syncState()
        }

        override fun onPlayerError(error: PlaybackException) {
            _player.update {
                it.copy(error = "Playback issue: ${error.errorCodeName}")
            }
        }
    }

    private fun effectiveDuration(mediaController: MediaController): Long {
        val playerDuration = mediaController.duration
        if (playerDuration != C.TIME_UNSET && playerDuration > 0) return playerDuration
        return mediaController.currentMediaItem?.mediaId?.let { trackMap[it]?.durationMs } ?: 0L
    }

    private fun syncState() {
        val mediaController = controller ?: return
        val item = mediaController.currentMediaItem
        val metadata = item?.mediaMetadata
        val track = item?.mediaId?.let { trackMap[it] }
        val queue = (0 until mediaController.mediaItemCount).map { index ->
            val queueItem = mediaController.getMediaItemAt(index)
            val meta = queueItem.mediaMetadata
            val queueTrack = queueItem.mediaId.let { trackMap[it] }
            QueueEntry(
                mediaId = queueItem.mediaId,
                title = meta.title?.toString() ?: queueTrack?.title.orEmpty(),
                artist = meta.artist?.toString() ?: queueTrack?.artist.orEmpty(),
                artUri = meta.artworkUri?.toString() ?: queueTrack?.artUri,
                color = queueTrack?.color ?: 0xFF8B5CF6.toInt()
            )
        }
        _player.value = PlayerState(
            connected = true,
            hasItem = item != null,
            mediaId = item?.mediaId.orEmpty(),
            title = metadata?.title?.toString() ?: track?.title.orEmpty(),
            artist = metadata?.artist?.toString() ?: track?.artist.orEmpty(),
            album = metadata?.albumTitle?.toString() ?: track?.album.orEmpty(),
            artUri = metadata?.artworkUri?.toString() ?: track?.artUri,
            trackColor = track?.color ?: 0xFF8B5CF6.toInt(),
            isPlaying = mediaController.isPlaying,
            isBuffering = mediaController.playbackState == Player.STATE_BUFFERING,
            positionMs = mediaController.currentPosition.coerceAtLeast(0L),
            durationMs = effectiveDuration(mediaController),
            shuffle = mediaController.shuffleModeEnabled,
            repeatMode = mediaController.repeatMode,
            index = mediaController.currentMediaItemIndex,
            queue = queue,
            error = _player.value.error
        )
    }

    fun onFolderSelected(uri: Uri) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val folderName = repository.displayFolderName(uri)
        prefs.folderUri = uri.toString()
        prefs.folderName = folderName
        controller?.run {
            stop()
            clearMediaItems()
        }
        pendingPlay = null
        _player.update { PlayerState(connected = it.connected) }
        _library.update {
            it.copy(
                folderUri = uri.toString(),
                folderName = folderName,
                scan = ScanState(active = true, currentFile = "Looking for music…")
            )
        }
        startScan(uri)
    }

    fun rescan() {
        _library.value.folderUri?.let { lastAutoCheck = 0L; startScan(Uri.parse(it)) }
    }

    fun onAppForeground() {
        foreground = true
        autoSync()
        refreshSuggestion()
    }

    fun onAppBackground() {
        foreground = false
    }

    private fun autoSync(force: Boolean = false) {
        val state = _library.value
        val folderUri = state.folderUri ?: return
        if (state.loading || state.scan.active) return
        if (scanJob?.isActive == true || autoSyncJob?.isActive == true) return
        val now = System.currentTimeMillis()
        if (!force && now - lastAutoCheck < 45_000L) return
        lastAutoCheck = now
        autoSyncJob = viewModelScope.launch {
            val scanned = runCatching {
                repository.scan(Uri.parse(folderUri)) { _, _, _ -> }
            }.getOrNull() ?: return@launch
            ensureActive()
            val current = _library.value.tracks
            val enriched = runCatching { repository.backfillArtwork(scanned) }
                .getOrDefault(scanned)
            ensureActive()
            if (enriched == current) return@launch
            trackMap = enriched.associateBy { it.id }
            _library.update { it.copy(loading = false, tracks = enriched) }
            repository.saveLibrary(enriched)
        }
    }

    private fun startScan(uri: Uri) {
        scanJob?.cancel()
        autoSyncJob?.cancel()
        lastAutoCheck = System.currentTimeMillis()
        scanJob = viewModelScope.launch {
            _library.update {
                it.copy(scan = ScanState(active = true, currentFile = "Looking for music…"))
            }
            val result = runCatching {
                repository.scan(uri) { done, total, name ->
                    _library.update { state ->
                        state.copy(
                            scan = state.scan.copy(
                                active = true,
                                done = done,
                                total = total,
                                currentFile = name,
                                error = null
                            )
                        )
                    }
                }
            }
            result.onSuccess { tracks ->
                trackMap = tracks.associateBy { it.id }
                _library.update {
                    it.copy(
                        loading = false,
                        tracks = tracks,
                        scan = ScanState(active = false)
                    )
                }
                repository.saveLibrary(tracks)
                if (controller?.mediaItemCount == 0 && _player.value.mediaId.isEmpty()) {
                    restoreQueue()
                }
            }.onFailure { error ->
                _library.update {
                    it.copy(
                        loading = false,
                        scan = ScanState(
                            active = false,
                            error = error.message ?: "Velvet couldn't read that folder."
                        )
                    )
                }
            }
        }
    }

    fun setAccent(accent: VelvetAccent) {
        prefs.accent = accent
        _accent.value = accent
    }

    fun setSpectrumGain(value: Float) {
        val gain = value.coerceIn(0.3f, 3f)
        prefs.spectrumGain = gain
        _spectrumGain.value = gain
    }

    fun setAppVolume(value: Float) {
        val volume = value.coerceIn(0f, 1.5f)
        prefs.appVolume = volume
        _appVolume.value = volume
        controller?.volume = volume
    }

    fun openCloud() {
        _cloud.update { it.copy(open = true, message = null) }
        loadCloudList()
    }

    fun closeCloud() {
        if (_cloud.value.busy) return
        _cloud.update { it.copy(open = false) }
    }

    fun toggleCloudItem(index: Int) {
        if (_cloud.value.busy) return
        _cloud.update { state ->
            state.copy(
                items = state.items.mapIndexed { i, item ->
                    if (i == index) item.copy(selected = !item.selected) else item
                }
            )
        }
    }

    fun selectAllCloud(selected: Boolean) {
        if (_cloud.value.busy) return
        _cloud.update { state -> state.copy(items = state.items.map { it.copy(selected = selected) }) }
    }

    private fun loadCloudList() {
        val libraryUri = _library.value.folderUri ?: return
        cloudJob?.cancel()
        cloudJob = viewModelScope.launch {
            _cloud.update { it.copy(loading = true, message = null, items = emptyList()) }
            val localNames = runCatching {
                repository.listAudio(Uri.parse(libraryUri), recursive = true)
            }.getOrDefault(emptyList()).map { CloudApi.key(it.name) }.toSet()
            runCatching {
                CloudApi.manifest()
                    .filter { CloudApi.key(it.name) !in localNames }
                    .sortedBy { it.name.lowercase() }
            }.onSuccess { entries ->
                _cloud.update { state ->
                    state.copy(
                        loading = false,
                        items = entries.map { CloudItem(it.name, it.url, it.sizeBytes) }
                    )
                }
            }.onFailure { error ->
                _cloud.update {
                    it.copy(
                        loading = false,
                        message = error.message ?: "Velvet Cloud is unreachable right now."
                    )
                }
            }
        }
    }

    fun runCloud() {
        val state = _cloud.value
        if (state.busy || state.loading) return
        val libraryUri = _library.value.folderUri ?: return
        val targets = state.items.filter { it.selected }
        if (targets.isEmpty()) return
        cloudJob = viewModelScope.launch {
            _cloud.update { it.copy(busy = true, message = null) }
            var copied = 0
            for (item in targets) {
                if (!isActive) break
                updateCloudItem(item.url) {
                    it.copy(progress = 0f, status = "Downloading…", failed = false)
                }
                val result = CloudApi.download(
                    context = app,
                    entry = CloudEntry(item.name, item.url, item.sizeBytes),
                    destTreeUri = Uri.parse(libraryUri)
                ) { fraction ->
                    updateCloudItem(item.url) {
                        it.copy(progress = fraction.coerceIn(0f, 1f), status = "")
                    }
                }
                if (result.isSuccess) {
                    copied++
                    updateCloudItem(item.url) { it.copy(progress = 1f, status = "Done") }
                } else {
                    updateCloudItem(item.url) {
                        it.copy(
                            status = result.exceptionOrNull()?.message ?: "Failed",
                            failed = true
                        )
                    }
                }
            }
            _cloud.update {
                it.copy(
                    busy = false,
                    message = "Downloaded $copied of ${targets.size} track${if (targets.size == 1) "" else "s"}.",
                    suggest = 0
                )
            }
            if (copied > 0) startScan(Uri.parse(libraryUri))
        }
    }

    private fun updateCloudItem(url: String, transform: (CloudItem) -> CloudItem) {
        _cloud.update { state ->
            state.copy(items = state.items.map { if (it.url == url) transform(it) else it })
        }
    }

    fun refreshSuggestion(force: Boolean = false) {
        val libraryUri = _library.value.folderUri ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastCloudSuggestion < 60_000L) return
        lastCloudSuggestion = now
        viewModelScope.launch {
            val localNames = runCatching {
                repository.listAudio(Uri.parse(libraryUri), recursive = true)
            }.getOrDefault(emptyList()).map { CloudApi.key(it.name) }.toSet()
            val missing = runCatching { CloudApi.manifest() }
                .getOrDefault(emptyList())
                .count { CloudApi.key(it.name) !in localNames }
            _cloud.update { it.copy(suggest = missing) }
        }
    }

    fun refreshSpectrumCapture() {
        PlaybackService.refreshSpectrumCapture()
    }

    fun toggleFavorite(track: Track) {
        prefs.toggleFavorite(track.id)
        _favorites.value = prefs.favorites
    }

    fun playTrack(track: Track, queue: List<Track>) {
        if (queue.isEmpty()) return
        val index = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playTracks(queue, index)
    }

    fun playTracks(tracks: List<Track>, startIndex: Int, shuffle: Boolean? = null) {
        if (tracks.isEmpty()) return
        val mediaController = controller
        if (mediaController == null) {
            pendingPlay = tracks to startIndex
            return
        }
        val shuffled = shuffle ?: mediaController.shuffleModeEnabled
        mediaController.shuffleModeEnabled = shuffled
        mediaController.repeatMode = prefs.repeatMode
        mediaController.setMediaItems(
            tracks.map { it.toMediaItem() },
            startIndex.coerceIn(0, tracks.lastIndex),
            0L
        )
        mediaController.prepare()
        mediaController.play()
        persistQueue(tracks.map { it.id }, startIndex, 0L)
        syncState()
    }

    fun shuffleAll(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        playTracks(tracks, Random.nextInt(tracks.size), shuffle = true)
    }

    fun togglePlayPause() {
        val mediaController = controller ?: return
        if (mediaController.mediaItemCount == 0) {
            if (_library.value.tracks.isNotEmpty()) playTracks(_library.value.tracks, 0)
            return
        }
        if (mediaController.isPlaying) {
            mediaController.pause()
            persistQueueState()
        } else {
            mediaController.play()
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekToFraction(fraction: Float) {
        val mediaController = controller ?: return
        val duration = effectiveDuration(mediaController)
        if (duration <= 0) return
        val target = (duration * fraction.coerceIn(0f, 1f)).toLong()
        mediaController.seekTo(target)
        _player.update { it.copy(positionMs = target) }
        persistQueueState()
    }

    fun jumpToQueueIndex(index: Int) {
        val mediaController = controller ?: return
        if (index !in 0 until mediaController.mediaItemCount) return
        mediaController.seekTo(index, 0L)
        mediaController.play()
        syncState()
    }

    fun toggleShuffle() {
        val mediaController = controller ?: return
        mediaController.shuffleModeEnabled = !mediaController.shuffleModeEnabled
        prefs.shuffle = mediaController.shuffleModeEnabled
        syncState()
    }

    fun cycleRepeat() {
        val mediaController = controller ?: return
        val next = when (mediaController.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        mediaController.repeatMode = next
        prefs.repeatMode = next
        syncState()
    }

    fun clearError() {
        _player.update { it.copy(error = null) }
    }

    private fun Track.toMediaItem(): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setIsPlayable(true)
        artUri?.let { metadataBuilder.setArtworkUri(Uri.parse(it)) }
        if (trackNumber > 0) metadataBuilder.setTrackNumber(trackNumber)
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun persistQueueState() {
        val mediaController = controller ?: return
        if (mediaController.mediaItemCount == 0) return
        prefs.queueIndex = mediaController.currentMediaItemIndex
        prefs.queuePosition = mediaController.currentPosition.coerceAtLeast(0L)
        prefs.queueIds = JSONArray(
            (0 until mediaController.mediaItemCount).map { mediaController.getMediaItemAt(it).mediaId }
        ).toString()
        prefs.shuffle = mediaController.shuffleModeEnabled
        prefs.repeatMode = mediaController.repeatMode
    }

    private fun persistQueue(ids: List<String>, index: Int, position: Long) {
        prefs.queueIds = JSONArray(ids).toString()
        prefs.queueIndex = index
        prefs.queuePosition = position
    }

    private fun restoreQueue() {
        val mediaController = controller ?: return
        if (mediaController.mediaItemCount > 0) return
        val saved = prefs.queueIds ?: return
        val ids = runCatching {
            JSONArray(saved).let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotEmpty) }
            }
        }.getOrDefault(emptyList())
        val tracks = ids.mapNotNull { trackMap[it] }
        if (tracks.isEmpty()) return
        val index = prefs.queueIndex.coerceIn(0, tracks.lastIndex)
        mediaController.setMediaItems(
            tracks.map { it.toMediaItem() },
            index,
            prefs.queuePosition.coerceAtLeast(0L)
        )
        mediaController.prepare()
        syncState()
    }

    override fun onCleared() {
        persistQueueState()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onCleared()
    }
}
