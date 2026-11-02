package com.velvet.music

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velvet.music.data.buildAlbums
import com.velvet.music.ui.MainViewModel
import com.velvet.music.ui.components.BrandMark
import com.velvet.music.ui.screens.AlbumScreen
import com.velvet.music.ui.screens.LibraryScreen
import com.velvet.music.ui.screens.OnboardingScreen
import com.velvet.music.ui.screens.PlayerScreen
import com.velvet.music.ui.theme.Ink
import com.velvet.music.ui.theme.LocalAccent
import com.velvet.music.ui.theme.TextPrimary
import com.velvet.music.ui.theme.TextTertiary
import com.velvet.music.ui.theme.VelvetTheme
import com.velvet.music.ui.volume.VolumeHudHost
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val volumeKeys = MutableSharedFlow<Int>(extraBufferCapacity = 8)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            VelvetApp(viewModel, volumeKeys)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN -> volumeKeys.tryEmit(event.keyCode)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForeground()
    }

    override fun onStop() {
        viewModel.onAppBackground()
        super.onStop()
    }

    fun openVolumeSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }
}

@Composable
private fun VelvetApp(viewModel: MainViewModel, volumeKeys: MutableSharedFlow<Int>) {
    val accent by viewModel.accent.collectAsStateWithLifecycle()

    VelvetTheme(accent = accent) {
        val library by viewModel.library.collectAsStateWithLifecycle()
        val player by viewModel.playerState.collectAsStateWithLifecycle()
        val favorites by viewModel.favorites.collectAsStateWithLifecycle()
        val bands by viewModel.spectrum.collectAsStateWithLifecycle()
        val spectrumLive by viewModel.spectrumLive.collectAsStateWithLifecycle()
        val spectrumGain by viewModel.spectrumGain.collectAsStateWithLifecycle()
        val appVolume by viewModel.appVolume.collectAsStateWithLifecycle()
        val cloud by viewModel.cloud.collectAsStateWithLifecycle()
        val albums = remember(library.tracks) { buildAlbums(library.tracks) }

        val context = LocalContext.current
        val notificationLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }
        val audioLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) viewModel.refreshSpectrumCapture()
        }

        val folderLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                viewModel.onFolderSelected(uri)
                requestNotificationPermission(context, notificationLauncher)
            }
        }

        LaunchedEffect(library.folderUri) {
            if (library.folderUri != null) {
                requestNotificationPermission(context, notificationLauncher)
            }
        }

        var showPlayer by rememberSaveable { mutableStateOf(false) }
        var selectedAlbumKey by rememberSaveable { mutableStateOf<String?>(null) }
        val selectedAlbum = albums.firstOrNull { it.key == selectedAlbumKey }

        LaunchedEffect(showPlayer) {
            if (showPlayer) requestAudioPermission(context, audioLauncher)
        }

        BackHandler(enabled = cloud.open || showPlayer || selectedAlbum != null) {
            when {
                cloud.open -> viewModel.closeCloud()
                showPlayer -> showPlayer = false
                else -> selectedAlbumKey = null
            }
        }

        Box(Modifier.fillMaxSize().background(Ink)) {
            when {
                library.loading -> SplashScreen()

                library.folderUri == null -> OnboardingScreen(
                    accent = accent,
                    onAccent = viewModel::setAccent,
                    onChooseFolder = { folderLauncher.launch(null) }
                )

                else -> LibraryScreen(
                    library = library,
                    player = player,
                    favorites = favorites,
                    albums = albums,
                    accent = accent,
                    onAccent = viewModel::setAccent,
                    onChooseFolder = { folderLauncher.launch(null) },
                    onRescan = viewModel::rescan,
                    onTogglePlay = viewModel::togglePlayPause,
                    onNext = viewModel::next,
                    onPlayTrack = viewModel::playTrack,
                    onPlayAll = { tracks, shuffle ->
                        if (shuffle) viewModel.shuffleAll(tracks) else viewModel.playTracks(tracks, 0)
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onOpenPlayer = { showPlayer = true },
                    onOpenAlbum = { album -> selectedAlbumKey = album.key },
                    onOpenVolumeSettings = { (context as? MainActivity)?.openVolumeSettings() },
                    cloud = cloud,
                    onCloudDownload = viewModel::openCloud,
                    onCloudClose = viewModel::closeCloud,
                    onCloudToggle = viewModel::toggleCloudItem,
                    onCloudSelectAll = viewModel::selectAllCloud,
                    onCloudRun = viewModel::runCloud
                )
            }

            AnimatedVisibility(
                visible = selectedAlbum != null,
                enter = fadeIn(tween(220)) + slideInHorizontally(tween(320)) { it / 4 },
                exit = fadeOut(tween(160)) + slideOutHorizontally(tween(240)) { it / 4 }
            ) {
                selectedAlbum?.let { album ->
                    AlbumScreen(
                        album = album,
                        player = player,
                        favorites = favorites,
                        onBack = { selectedAlbumKey = null },
                        onPlayAll = { tracks, shuffle ->
                            if (shuffle) viewModel.shuffleAll(tracks) else viewModel.playTracks(tracks, 0)
                        },
                        onPlayTrack = viewModel::playTrack,
                        onTogglePlay = viewModel::togglePlayPause,
                        onToggleFavorite = viewModel::toggleFavorite
                    )
                }
            }

            AnimatedVisibility(
                visible = showPlayer && player.hasItem,
                enter = slideInVertically(tween(360)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(260)) { it } + fadeOut(tween(180))
            ) {
                PlayerScreen(
                    player = player,
                    isFavorite = player.mediaId in favorites,
                    bands = bands,
                    spectrumLive = spectrumLive,
                    spectrumGain = spectrumGain,
                    onSpectrumGain = viewModel::setSpectrumGain,
                    onClose = { showPlayer = false },
                    onTogglePlay = viewModel::togglePlayPause,
                    onNext = viewModel::next,
                    onPrevious = viewModel::previous,
                    onSeekFraction = viewModel::seekToFraction,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeat,
                    onToggleFavorite = {
                        library.tracks.firstOrNull { it.id == player.mediaId }?.let(viewModel::toggleFavorite)
                    },
                    onJumpToQueue = viewModel::jumpToQueueIndex
                )
            }

            VolumeHudHost(
                volumeKeyEvents = volumeKeys,
                appVolume = appVolume,
                onAppVolume = viewModel::setAppVolume,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

private fun requestAudioPermission(
    context: Context,
    launcher: ActivityResultLauncher<String>
) {
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    if (!granted) launcher.launch(Manifest.permission.RECORD_AUDIO)
}

private fun requestNotificationPermission(
    context: Context,
    launcher: ActivityResultLauncher<String>
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun SplashScreen() {
    val accent = LocalAccent.current
    val transition = rememberInfiniteTransition(label = "splash")
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Ink, Color(0xFF0F0917), Ink)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.glow.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.42f),
                    radius = size.width * 0.8f
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.5f, size.height * 0.42f)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.scale(pulse)) {
                BrandMark(64.dp)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "VELVET",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 9.sp),
                color = TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "YOUR PRIVATE LISTENING ROOM",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = TextTertiary
            )
        }
    }
}
