package com.velvet.music.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.velvet.music.R
import com.velvet.music.data.LyricLine
import com.velvet.music.data.Lyrics
import com.velvet.music.ui.PlayerState
import com.velvet.music.ui.QueueEntry
import com.velvet.music.ui.components.Artwork
import com.velvet.music.ui.components.AuroraBackdrop
import com.velvet.music.ui.components.BreathingGlow
import com.velvet.music.ui.components.CircleIconButton
import com.velvet.music.ui.components.DiscoLights
import com.velvet.music.ui.components.EqualizerBars
import com.velvet.music.ui.components.FilmGrain
import com.velvet.music.ui.components.GlowSlider
import com.velvet.music.ui.components.ReactiveVeinOverlay
import com.velvet.music.ui.components.SectionLabel
import com.velvet.music.ui.components.SpectrumRing
import com.velvet.music.ui.components.SpectrumStrip
import com.velvet.music.ui.components.VelvetSeekBar
import com.velvet.music.ui.components.VinylDisc
import com.velvet.music.ui.components.formatTime
import com.velvet.music.ui.theme.Hairline
import com.velvet.music.ui.theme.Ink
import com.velvet.music.ui.theme.LocalAccent
import com.velvet.music.ui.theme.Surface1
import com.velvet.music.ui.theme.TextPrimary
import com.velvet.music.ui.theme.TextSecondary
import com.velvet.music.ui.theme.TextTertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    player: PlayerState,
    isFavorite: Boolean,
    spectrum: State<FloatArray>,
    spectrumLive: Boolean,
    spectrumGain: Float,
    onSpectrumGain: (Float) -> Unit,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onJumpToQueue: (Int) -> Unit
) {
    val accent = LocalAccent.current
    var queueVisible by remember { mutableStateOf(false) }
    var disco by remember { mutableStateOf(false) }
    var lyricsVisible by remember { mutableStateOf(false) }
    var lyricLines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var lyricMessage by remember { mutableStateOf<String?>(null) }
    var lyricAttempt by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LaunchedEffect(player.mediaId, lyricAttempt) {
        lyricLines = emptyList()
        lyricMessage = null
        if (player.mediaId.isBlank()) return@LaunchedEffect

        val local = withContext(Dispatchers.IO) {
            Lyrics.loadLocal(
                context,
                player.mediaId,
                player.title,
                player.artist,
                player.durationMs
            )
        }
        if (local.isNotEmpty()) {
            lyricLines = local
            return@LaunchedEffect
        }

        if (lyricAttempt > 0) {
            Lyrics.forget(context, player.title, player.artist, player.durationMs)
        }
        lyricMessage = "Finding synced lyrics…"
        val remote = withContext(Dispatchers.IO) {
            Lyrics.loadRemote(
                context,
                player.mediaId,
                player.title,
                player.artist,
                player.album,
                player.durationMs
            )
        }
        lyricLines = remote
        lyricMessage = if (remote.isEmpty()) {
            "No synced lyrics for this track yet. Velvet can also read a .lrc file beside the audio."
        } else {
            null
        }
    }

    val fraction = if (player.durationMs > 0L) {
        (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f)
    } else 0f

    val vinylAngle = rememberVinylAngle(player.isPlaying)
    val backdropTransition = rememberInfiniteTransition(label = "backdrop")
    val backdropScale by backdropTransition.animateFloat(
        initialValue = 1.14f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "backdropScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        if (player.hasItem) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.32f)
                    .graphicsLayer {
                        scaleX = backdropScale
                        scaleY = backdropScale
                    }
            ) {
                Artwork(
                    uri = player.artUri,
                    color = player.trackColor,
                    modifier = Modifier.fillMaxSize(),
                    corner = 0.dp,
                    requestSize = 640,
                    overlayNote = false
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Ink.copy(alpha = 0.58f),
                                Ink.copy(alpha = 0.82f),
                                Ink.copy(alpha = 0.97f),
                                Ink
                            )
                        )
                    )
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(player.trackColor).copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.28f),
                    radius = size.width * 0.85f
                ),
                radius = size.width * 0.85f,
                center = Offset(size.width * 0.5f, size.height * 0.28f)
            )
        }

        AuroraBackdrop(
            primary = Color(player.trackColor),
            secondary = accent.glow,
            modifier = Modifier.fillMaxSize(),
            intensity = 0.85f
        )
        FilmGrain(Modifier.fillMaxSize(), alpha = 0.035f)
        ReactiveVeinOverlay(
            spectrum = spectrum,
            playing = player.isPlaying,
            color = accent.primary,
            modifier = Modifier.fillMaxSize(),
            veinCount = 8,
            seed = 23
        )
        DiscoLights(
            active = disco,
            playing = player.isPlaying,
            spectrum = spectrum,
            primary = accent.primary,
            bright = accent.bright,
            deep = accent.deep,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(
                    icon = painterResource(R.drawable.ic_arrow_down),
                    contentDescription = "Close player",
                    onClick = onClose,
                    size = 42.dp,
                    iconSize = 22.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Now playing")
                        if (player.isPlaying) {
                            Spacer(Modifier.width(7.dp))
                            EqualizerBars(
                                playing = true,
                                color = accent.primary,
                                barWidth = 1.5.dp
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = player.artist.ifBlank { "From your folder" },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                CircleIconButton(
                    icon = painterResource(R.drawable.ic_heart),
                    contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                    onClick = onToggleFavorite,
                    size = 42.dp,
                    iconSize = 17.dp,
                    tint = if (isFavorite) accent.primary else TextPrimary,
                    background = if (isFavorite) accent.primary.copy(alpha = 0.14f)
                    else Color.White.copy(alpha = 0.055f),
                    border = if (isFavorite) accent.primary.copy(alpha = 0.30f)
                    else Color.White.copy(alpha = 0.08f)
                )
            }

            Spacer(Modifier.height(28.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                BreathingGlow(
                    color = Color(player.trackColor),
                    active = player.isPlaying,
                    modifier = Modifier
                        .fillMaxWidth(0.98f)
                        .widthIn(max = 430.dp)
                        .aspectRatio(1f)
                )
                SpectrumRing(
                    bands = spectrum,
                    color = accent.primary,
                    bright = accent.bright,
                    active = player.isPlaying,
                    sensitivity = spectrumGain,
                    innerFraction = 0.36f,
                    lengthFraction = 0.075f,
                    modifier = Modifier
                        .fillMaxWidth(1f)
                        .widthIn(max = 460.dp)
                        .aspectRatio(1f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .widthIn(max = 346.dp)
                        .aspectRatio(1f),
                    contentAlignment = Alignment.Center
                ) {
                    VinylDisc(
                        spin = vinylAngle,
                        color = Color(player.trackColor),
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .aspectRatio(1f)
                    )
                    Artwork(
                        uri = player.artUri,
                        color = player.trackColor,
                        modifier = Modifier
                            .fillMaxWidth(0.84f)
                            .aspectRatio(1f)
                            .shadow(
                                elevation = 34.dp,
                                shape = RoundedCornerShape(30.dp),
                                ambientColor = Color(player.trackColor).copy(alpha = 0.45f),
                                spotColor = Color(player.trackColor).copy(alpha = 0.65f)
                            )
                            .clip(RoundedCornerShape(30.dp))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.10f),
                                RoundedCornerShape(30.dp)
                            ),
                        corner = 30.dp,
                        requestSize = 640
                    )
                }
            }

            Spacer(Modifier.height(30.dp))

            AnimatedContent(
                targetState = player.mediaId,
                transitionSpec = {
                    (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 4 }) togetherWith
                        (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 4 })
                },
                label = "trackInfo"
            ) {
                Column {
                    Text(
                        text = player.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = player.artist.ifBlank { "Unknown artist" },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (player.album.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = player.album,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SpectrumStrip(
                bands = spectrum,
                color = accent.primary,
                bright = accent.bright,
                active = player.isPlaying,
                sensitivity = spectrumGain,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (spectrumLive) accent.bright else TextTertiary)
                )
                Spacer(Modifier.width(6.dp))
                SectionLabel(if (spectrumLive) "Live spectrum" else "Simulated spectrum")
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Sensitivity",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
                Spacer(Modifier.width(6.dp))
                GlowSlider(
                    value = ((spectrumGain - 0.3f) / 2.7f).coerceIn(0f, 1f),
                    onChange = { onSpectrumGain(0.3f + it * 2.7f) },
                    color = accent.primary,
                    bright = accent.bright,
                    modifier = Modifier.width(118.dp)
                )
            }
            Spacer(Modifier.height(2.dp))

            VelvetSeekBar(
                progress = fraction,
                accent = accent.primary,
                onSeek = onSeekFraction
            )
            Row(Modifier.fillMaxWidth()) {
                Text(
                    text = formatTime(player.positionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatTime(player.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToggleIcon(
                    iconRes = R.drawable.ic_shuffle,
                    selected = player.shuffle,
                    contentDescription = "Shuffle",
                    onClick = onToggleShuffle
                )
                BigControl(
                    iconRes = R.drawable.ic_skip_previous,
                    contentDescription = "Previous",
                    onClick = onPrevious
                )
                PlayButton(
                    isPlaying = player.isPlaying,
                    isBuffering = player.isBuffering,
                    onClick = onTogglePlay
                )
                BigControl(
                    iconRes = R.drawable.ic_skip_next,
                    contentDescription = "Next",
                    onClick = onNext
                )
                ToggleIcon(
                    iconRes = R.drawable.ic_repeat,
                    selected = player.repeatMode != Player.REPEAT_MODE_OFF,
                    contentDescription = "Repeat",
                    onClick = onCycleRepeat,
                    badge = if (player.repeatMode == Player.REPEAT_MODE_ONE) "1" else null
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (disco) accent.primary.copy(alpha = 0.20f)
                            else Color.White.copy(alpha = 0.05f)
                        )
                        .border(
                            1.dp,
                            if (disco) accent.primary.copy(alpha = 0.55f) else Hairline,
                            RoundedCornerShape(50)
                        )
                        .clickable { disco = !disco }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (disco) "DISCO ON" else "DISCO",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                        color = if (disco) accent.bright else TextSecondary
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (lyricsVisible) accent.primary.copy(alpha = 0.20f)
                            else Color.White.copy(alpha = 0.05f)
                        )
                        .border(
                            1.dp,
                            if (lyricsVisible) accent.primary.copy(alpha = 0.55f) else Hairline,
                            RoundedCornerShape(50)
                        )
                        .clickable { lyricsVisible = !lyricsVisible }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "LYRICS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                        color = if (lyricsVisible) accent.bright else TextSecondary
                    )
                }
            }

            if (lyricsVisible) {
                Spacer(Modifier.height(12.dp))
                LyricsPane(
                    lines = lyricLines,
                    positionMs = player.positionMs,
                    playing = player.isPlaying,
                    message = lyricMessage,
                    bright = accent.bright,
                    onRetry = { lyricAttempt++ },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (player.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = player.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF8A9A),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(26.dp))

            val nextEntry = player.queue.getOrNull(player.index + 1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.045f))
                    .border(1.dp, Hairline, RoundedCornerShape(20.dp))
                    .clickable { queueVisible = true }
                    .padding(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (nextEntry != null) {
                    Artwork(
                        uri = nextEntry.artUri,
                        color = nextEntry.color,
                        modifier = Modifier.size(40.dp),
                        corner = 12.dp,
                        requestSize = 128
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_queue),
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SectionLabel("Up next")
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = nextEntry?.title ?: "Queue is empty",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_queue),
                    contentDescription = "Open queue",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (queueVisible) {
        ModalBottomSheet(
            onDismissRequest = { queueVisible = false },
            sheetState = sheetState,
            containerColor = Surface1,
            scrimColor = Color.Black.copy(alpha = 0.65f),
            dragHandle = {
                BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.22f))
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .navigationBarsPadding()
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        SectionLabel("Playing from your folder")
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "Up next",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "${player.queue.size} tracks",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(player.queue, key = { _, entry -> entry.mediaId }) { index, entry ->
                        QueueRow(
                            entry = entry,
                            isCurrent = index == player.index,
                            isPlaying = player.isPlaying,
                            onClick = {
                                onJumpToQueue(index)
                                scope.launch {
                                    runCatching { sheetState.hide() }
                                    queueVisible = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberVinylAngle(playing: Boolean): Float {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val seconds = ((now - last).coerceAtMost(80_000_000L)) / 1_000_000_000f
                angle = (angle + seconds * 15f) % 360f
                last = now
            }
        }
    }
    return angle
}

@Composable
private fun ToggleIcon(
    iconRes: Int,
    selected: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    badge: String? = null
) {
    val accent = LocalAccent.current
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = if (selected) accent.primary else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-5).dp)
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(accent.primary)
            )
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(accent.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badge,
                    color = accent.onAccent,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BigControl(iconRes: Int, contentDescription: String, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.size(26.dp)
        )
    }
}

@Composable
private fun PlayButton(isPlaying: Boolean, isBuffering: Boolean, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "playScale"
    )
    Box(
        modifier = Modifier
            .size(78.dp)
            .scale(scale)
            .shadow(
                elevation = 26.dp,
                shape = CircleShape,
                ambientColor = accent.glow.copy(alpha = 0.55f),
                spotColor = accent.glow.copy(alpha = 0.75f)
            )
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(accent.gradientStart, accent.primary, accent.gradientEnd)
                )
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(26.dp),
                color = accent.onAccent,
                strokeWidth = 2.5.dp
            )
        } else {
            Icon(
                painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = accent.onAccent,
                modifier = Modifier.size(31.dp)
            )
        }
    }
}

@Composable
private fun QueueRow(
    entry: QueueEntry,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val accent = LocalAccent.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isCurrent) accent.primary.copy(alpha = 0.08f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(
            uri = entry.artUri,
            color = entry.color,
            modifier = Modifier.size(42.dp),
            corner = 12.dp,
            requestSize = 128
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isCurrent) accent.bright else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.artist,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrent) {
            EqualizerBars(
                playing = isPlaying,
                color = accent.primary,
                barWidth = 2.dp
            )
        }
    }
}

@Composable
private fun LyricsPane(
    lines: List<LyricLine>,
    positionMs: Long,
    playing: Boolean,
    message: String?,
    bright: Color,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeIndex = remember { mutableIntStateOf(-1) }
    val reported = rememberUpdatedState(positionMs)

    // Run one smooth clock and only re-anchor when the player actually jumps
    // (seek, skip or track change). Restarting on every 500 ms progress report
    // made the highlight stutter backwards.
    LaunchedEffect(lines, playing) {
        if (lines.isEmpty()) {
            activeIndex.intValue = -1
            return@LaunchedEffect
        }
        var clock = reported.value
        fun refresh() {
            activeIndex.intValue = lines.indexOfLast { it.timeMs <= clock }
                .coerceAtLeast(0)
        }
        refresh()
        if (!playing) return@LaunchedEffect

        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) clock += (now - last) / 1_000_000L
                last = now
            }
            val current = reported.value
            if (abs(clock - current) > 1_500L) clock = current
            refresh()
        }
    }

    val active = activeIndex.intValue
    val start = if (lines.size <= 5) 0 else (active - 2).coerceIn(0, lines.size - 5)

    // Drift is read only inside the draw lambda, so the pane redraws without
    // recomposing while the dust moves.
    val dust = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    dust.floatValue = (dust.floatValue + (now - last) / 9_000_000_000f) % 1f
                }
                last = now
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Hairline, RoundedCornerShape(20.dp))
    ) {
        Canvas(Modifier.matchParentSize()) {
            val drift = dust.floatValue
            val step = 1f / 26f
            for (index in 0 until 26) {
                val progress = (drift + index * step) % 1f
                val x = size.width * ((index * 0.37f) % 1f) +
                    sin(progress * 2f * PI.toFloat() + index) * 14.dp.toPx()
                val y = size.height * (1f - progress)
                val radius = (0.7f + (index % 3) * 0.55f).dp.toPx()
                drawCircle(
                    color = bright.copy(alpha = 0.06f + (index % 4) * 0.035f),
                    radius = radius,
                    center = Offset(x, y)
                )
            }
        }

        if (lines.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = message ?: "Lyrics load when this track starts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    textAlign = TextAlign.Center
                )
                if (message != null && message != "Finding synced lyrics…") {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "SEARCH AGAIN",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                        color = bright,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onRetry)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        } else {
            AnimatedContent(
                targetState = start,
                transitionSpec = {
                    (slideInVertically { it / 3 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 3 } + fadeOut())
                },
                label = "lyricWindow"
            ) { first ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    lines.drop(first).take(5).forEachIndexed { offset, line ->
                        val current = first + offset == active
                        val emphasis by animateFloatAsState(
                            targetValue = if (current) 1f else 0f,
                            animationSpec = spring(
                                dampingRatio = 0.72f,
                                stiffness = 420f
                            ),
                            label = "lyricEmphasis"
                        )
                        Text(
                            text = line.text,
                            style = if (current) {
                                MaterialTheme.typography.titleMedium
                            } else {
                                MaterialTheme.typography.bodySmall
                            },
                            color = if (current) bright else TextTertiary,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .graphicsLayer {
                                    alpha = 0.52f + emphasis * 0.48f
                                    val scale = 0.97f + emphasis * 0.05f
                                    scaleX = scale
                                    scaleY = scale
                                }
                        )
                    }
                }
            }
        }
    }
}
