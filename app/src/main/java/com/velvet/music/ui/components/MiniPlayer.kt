package com.velvet.music.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.velvet.music.R
import com.velvet.music.ui.MainViewModel
import com.velvet.music.ui.PlayerState
import com.velvet.music.ui.theme.Hairline
import com.velvet.music.ui.theme.LocalAccent
import com.velvet.music.ui.theme.Surface1
import com.velvet.music.ui.theme.Surface2
import com.velvet.music.ui.theme.TextPrimary
import com.velvet.music.ui.theme.TextSecondary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    player: PlayerState,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(24.dp)
    val fraction = if (player.durationMs > 0) {
        (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f)
    } else 0f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 22.dp,
                shape = shape,
                ambientColor = Color(player.trackColor).copy(alpha = 0.35f),
                spotColor = Color(player.trackColor).copy(alpha = 0.45f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1E1828), Surface1)
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .clickable { onOpen() }
    ) {
        Column {
            HairlineProgress(fraction = fraction, accent = accent.primary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(
                    uri = player.artUri,
                    color = player.trackColor,
                    modifier = Modifier.size(50.dp),
                    corner = 15.dp,
                    requestSize = 160
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = player.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                    )
                    Spacer(Modifier.size(1.dp))
                    Text(
                        text = player.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.primary.copy(alpha = 0.14f))
                        .clickable { onTogglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        ),
                        contentDescription = if (player.isPlaying) "Pause" else "Play",
                        tint = accent.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(2.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onNext() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_skip_next),
                        contentDescription = "Next",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
