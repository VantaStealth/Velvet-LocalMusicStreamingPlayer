package com.velvet.music.ui.volume

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velvet.music.R
import com.velvet.music.ui.components.GlowSlider
import com.velvet.music.ui.components.LiquidOrb
import com.velvet.music.ui.components.SectionLabel
import com.velvet.music.ui.components.VeinOverlay
import com.velvet.music.ui.components.VerticalGlowRail
import com.velvet.music.ui.theme.LocalAccent
import com.velvet.music.ui.theme.TextPrimary
import com.velvet.music.ui.theme.TextSecondary
import com.velvet.music.ui.theme.TextTertiary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlin.math.roundToInt

private data class StreamSpec(
    val stream: Int,
    val label: String,
    val detail: String,
    val key: String
)

private val streamSpecs = listOf(
    StreamSpec(AudioManager.STREAM_MUSIC, "Media", "Music and podcasts", "volume_music"),
    StreamSpec(AudioManager.STREAM_RING, "Ring", "Incoming calls", "volume_ring"),
    StreamSpec(AudioManager.STREAM_NOTIFICATION, "Alerts", "Notifications", "volume_notification"),
    StreamSpec(AudioManager.STREAM_ALARM, "Alarm", "Clock and timers", "volume_alarm"),
    StreamSpec(AudioManager.STREAM_VOICE_CALL, "Calls", "Call audio", "volume_voice")
)

@Composable
fun VolumeHudHost(
    volumeKeyEvents: SharedFlow<Int>,
    appVolume: Float,
    onAppVolume: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accent = LocalAccent.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val volumes = remember {
        mutableStateMapOf<Int, Int>().apply {
            streamSpecs.forEach { spec ->
                put(spec.stream, runCatching { audioManager.getStreamVolume(spec.stream) }.getOrDefault(0))
            }
        }
    }
    var activeStream by remember { mutableStateOf(AudioManager.STREAM_MUSIC) }
    var visible by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    val maxVolume = remember(activeStream) {
        runCatching { audioManager.getStreamMaxVolume(activeStream) }.getOrDefault(15).coerceAtLeast(1)
    }
    val volumeFraction = (volumes[activeStream] ?: 0).toFloat() / maxVolume

    fun applyVolume(stream: Int, fraction: Float) {
        val max = runCatching { audioManager.getStreamMaxVolume(stream) }.getOrDefault(15)
            .coerceAtLeast(1)
        val value = (fraction.coerceIn(0f, 1f) * max).roundToInt()
        runCatching { audioManager.setStreamVolume(stream, value, 0) }
        volumes[stream] = value
    }

    DisposableEffect(audioManager, context) {
        val observers = streamSpecs.map { spec ->
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    val current = runCatching { audioManager.getStreamVolume(spec.stream) }
                        .getOrDefault(volumes[spec.stream] ?: 0)
                    if (current != volumes[spec.stream]) {
                        volumes[spec.stream] = current
                        activeStream = spec.stream
                        visible = true
                        tick++
                    }
                }
            }
            runCatching {
                context.contentResolver.registerContentObserver(
                    Settings.System.getUriFor(spec.key),
                    false,
                    observer
                )
            }
            observer
        }
        onDispose {
            observers.forEach { observer ->
                runCatching { context.contentResolver.unregisterContentObserver(observer) }
            }
        }
    }

    LaunchedEffect(volumeKeyEvents) {
        volumeKeyEvents.collect {
            activeStream = AudioManager.STREAM_MUSIC
            visible = true
            tick++
            delay(70)
            val current = runCatching { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }
                .getOrDefault(volumes[AudioManager.STREAM_MUSIC] ?: 0)
            volumes[AudioManager.STREAM_MUSIC] = current
        }
    }

    LaunchedEffect(visible, expanded, tick) {
        if (!visible) return@LaunchedEffect
        delay(if (expanded) 7000L else 2700L)
        expanded = false
        visible = false
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(if (expanded) 296.dp else 88.dp)
                .padding(vertical = 24.dp, horizontal = 8.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            val shape = RoundedCornerShape(if (expanded) 30.dp else 40.dp)
            Box(
                modifier = Modifier
                    .shadow(
                        elevation = 30.dp,
                        shape = shape,
                        ambientColor = accent.primary.copy(alpha = 0.30f),
                        spotColor = accent.primary.copy(alpha = 0.45f)
                    )
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xF0161120), Color(0xF20A0810))
                        )
                    )
                    .border(1.dp, accent.primary.copy(alpha = 0.26f), shape)
            ) {
                VeinOverlay(
                    color = accent.primary,
                    modifier = Modifier.matchParentSize(),
                    alpha = 0.10f,
                    veinCount = 5,
                    seed = 41
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    accent.bright.copy(alpha = 0.85f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                AnimatedContent(
                    targetState = expanded,
                    transitionSpec = {
                        (fadeIn() + scaleIn(initialScale = 0.94f)) togetherWith
                            (fadeOut() + scaleOut(targetScale = 0.94f))
                    },
                    label = "volumeHud"
                ) { isExpanded ->
                    if (isExpanded) {
                        ExpandedMixer(
                            volumes = volumes,
                            activeStream = activeStream,
                            audioManager = audioManager,
                            appVolume = appVolume,
                            onAppVolume = onAppVolume,
                            onStreamVolume = { stream, fraction ->
                                activeStream = stream
                                applyVolume(stream, fraction)
                                tick++
                            },
                            onCollapse = {
                                expanded = false
                                visible = false
                            }
                        )
                    } else {
                        CollapsedVolume(
                            fraction = volumeFraction,
                            percent = (volumeFraction * 100).roundToInt(),
                            label = streamSpecs.firstOrNull { it.stream == activeStream }?.label
                                ?: "Volume",
                            onVolume = {
                                applyVolume(activeStream, it)
                                tick++
                            },
                            onExpand = {
                                expanded = true
                                tick++
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapsedVolume(
    fraction: Float,
    percent: Int,
    label: String,
    onVolume: (Float) -> Unit,
    onExpand: () -> Unit
) {
    val accent = LocalAccent.current
    Column(
        modifier = Modifier.padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LiquidOrb(
            fraction = fraction,
            color = accent.primary,
            bright = accent.bright,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$percent",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
            color = accent.bright.copy(alpha = 0.85f)
        )
        Spacer(Modifier.height(12.dp))
        VerticalGlowRail(
            value = fraction,
            onChange = onVolume,
            color = accent.primary,
            bright = accent.bright,
            modifier = Modifier
                .width(26.dp)
                .height(132.dp)
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(accent.primary.copy(alpha = 0.16f))
                .border(1.dp, accent.primary.copy(alpha = 0.28f), CircleShape)
                .clickable { onExpand() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "MIX",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
                color = accent.bright
            )
        }
    }
}

@Composable
private fun ExpandedMixer(
    volumes: Map<Int, Int>,
    activeStream: Int,
    audioManager: AudioManager,
    appVolume: Float,
    onAppVolume: (Float) -> Unit,
    onStreamVolume: (Int, Float) -> Unit,
    onCollapse: () -> Unit
) {
    val accent = LocalAccent.current
    val activeSpec = streamSpecs.firstOrNull { it.stream == activeStream } ?: streamSpecs.first()
    val activeMax = runCatching { audioManager.getStreamMaxVolume(activeStream) }.getOrDefault(15)
        .coerceAtLeast(1)
    val activeFraction = (volumes[activeStream] ?: 0).toFloat() / activeMax
    val orbPulse by animateFloatAsState(
        targetValue = if (activeFraction > 0.02f) 1f else 0.86f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "orbPulse"
    )

    Column(
        modifier = Modifier
            .heightIn(max = 560.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Volume", color = accent.bright.copy(alpha = 0.9f))
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable { onCollapse() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_down),
                    contentDescription = "Close volume mixer",
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            LiquidOrb(
                fraction = activeFraction,
                color = accent.primary,
                bright = accent.bright,
                modifier = Modifier.size((52 * orbPulse).dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = activeSpec.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
                Text(
                    text = activeSpec.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
            Text(
                text = "${(activeFraction * 100).roundToInt()}",
                style = MaterialTheme.typography.headlineSmall,
                color = accent.bright
            )
        }

        Spacer(Modifier.height(14.dp))

        streamSpecs.forEach { spec ->
            val max = runCatching { audioManager.getStreamMaxVolume(spec.stream) }
                .getOrDefault(15).coerceAtLeast(1)
            val fraction = (volumes[spec.stream] ?: 0).toFloat() / max
            StreamRow(
                label = spec.label,
                detail = spec.detail,
                fraction = fraction,
                selected = spec.stream == activeStream,
                color = accent.primary,
                bright = accent.bright,
                onChange = { onStreamVolume(spec.stream, it) }
            )
        }

        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.07f))
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent.primary)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Velvet channel",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${(appVolume * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = accent.bright
            )
        }
        Text(
            text = "Trims or boosts only Velvet, independent of the media stream.",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )
        Spacer(Modifier.height(4.dp))
        GlowSlider(
            value = appVolume,
            onChange = onAppVolume,
            color = accent.primary,
            bright = accent.bright,
            max = 1.5f
        )

        Spacer(Modifier.height(6.dp))
        Text(
            text = "Slide past 100% to push Velvet above a game or video. Android shares one " +
                "media stream across apps, so lowering the other app alone needs root.",
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun StreamRow(
    label: String,
    detail: String,
    fraction: Float,
    selected: Boolean,
    color: Color,
    bright: Color,
    onChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) bright else TextPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(fraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = if (selected) bright else TextSecondary
            )
        }
        GlowSlider(
            value = fraction,
            onChange = onChange,
            color = if (selected) color else color.copy(alpha = 0.6f),
            bright = bright
        )
    }
}
