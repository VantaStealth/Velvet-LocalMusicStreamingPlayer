package com.velvet.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velvet.music.R
import com.velvet.music.data.VelvetAccent
import com.velvet.music.ui.theme.Hairline
import com.velvet.music.ui.theme.LocalAccent
import com.velvet.music.ui.theme.TextPrimary
import com.velvet.music.ui.theme.TextSecondary
import com.velvet.music.ui.theme.paletteFor
import java.util.Locale

@Composable
fun BrandMark(size: Dp = 34.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFF2B1C3A), Color(0xFF140E1D), Color(0xFF08060C))
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        Color(0x99F3DCA0),
                        Color(0x1FF3DCA0),
                        Color(0x44A87A25)
                    )
                ),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_brand_v),
            contentDescription = null,
            modifier = Modifier.size(size * 0.58f)
        )
    }
}

@Composable
fun AccentPicker(
    selected: VelvetAccent,
    onSelect: (VelvetAccent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VelvetAccent.entries.forEach { accent ->
            val palette = paletteFor(accent)
            val isSelected = accent == selected
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable { onSelect(accent) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(1.dp, palette.primary.copy(alpha = 0.55f), CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 13.dp else 10.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(palette.gradientStart, palette.gradientEnd)
                            )
                        )
                )
            }
        }
    }
}

@Composable
fun CircleIconButton(
    icon: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    iconSize: Dp = 19.dp,
    tint: Color = TextPrimary,
    background: Color = Color.White.copy(alpha = 0.055f),
    border: Color = Color.White.copy(alpha = 0.08f)
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, border, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun GradientPill(
    text: String,
    icon: Painter? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = LocalAccent.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(accent.gradientStart, accent.primary, accent.gradientEnd)
                )
            )
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = accent.onAccent,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = accent.onAccent,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
fun GhostChip(
    text: String,
    selected: Boolean = false,
    icon: Painter? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccent.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) accent.primary.copy(alpha = 0.14f)
                else Color.White.copy(alpha = 0.04f)
            )
            .border(
                1.dp,
                if (selected) accent.primary.copy(alpha = 0.35f) else Hairline,
                RoundedCornerShape(50)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = if (selected) accent.primary else TextSecondary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(
            text = text,
            color = if (selected) accent.primary else TextSecondary,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = TextSecondary) {
    Text(
        text = text.uppercase(Locale.getDefault()),
        modifier = modifier,
        color = color,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.2.sp)
    )
}

@Composable
fun EqualizerBars(
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    barWidth: Dp = 2.dp
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    val first by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(380, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val second by transition.animateFloat(
        initialValue = 0.65f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val third by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(460, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )
    val fourth by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(340, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar4"
    )
    val heights = if (playing) {
        listOf(first, second, third, fourth)
    } else {
        listOf(0.32f, 0.55f, 0.4f, 0.62f)
    }
    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun VelvetSeekBar(
    progress: Float,
    accent: Color,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(progress) }

    val shown = if (dragging) dragFraction else progress.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    dragFraction = (down.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    down.consume()
                    var active = true
                    while (active) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || !change.pressed) {
                            active = false
                        } else {
                            dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            change.consume()
                        }
                    }
                    dragging = false
                    onSeek(dragFraction)
                }
            }
    ) {
        val centerY = size.height / 2f
        val trackHeight = 4.dp.toPx()
        val radius = CornerRadius(trackHeight / 2f)
        drawRoundRect(
            color = Color.White.copy(alpha = 0.10f),
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = radius
        )
        val progressWidth = size.width * shown
        if (progressWidth > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.85f), accent)
                ),
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(progressWidth, trackHeight),
                cornerRadius = radius
            )
            drawCircle(
                color = accent.copy(alpha = 0.20f),
                radius = 13.dp.toPx(),
                center = Offset(progressWidth, centerY)
            )
            drawCircle(
                color = Color.White,
                radius = 5.5.dp.toPx(),
                center = Offset(progressWidth, centerY)
            )
            drawCircle(
                color = accent,
                radius = 2.4.dp.toPx(),
                center = Offset(progressWidth, centerY)
            )
        }
    }
}

@Composable
fun HairlineProgress(
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        drawRect(Color.White.copy(alpha = 0.06f))
        val width = size.width * fraction.coerceIn(0f, 1f)
        if (width > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.6f))),
                size = Size(width, size.height)
            )
        }
    }
}

fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = totalSeconds / 60
    return if (minutes >= 60) {
        String.format(Locale.US, "%d:%02d:%02d", minutes / 60, minutes % 60, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

fun formatLibraryDuration(ms: Long): String {
    val totalMinutes = ms / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours hr $minutes min"
        hours > 0 -> "$hours hr"
        else -> "$minutes min"
    }
}

fun greeting(): String {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late night"
    }
}
