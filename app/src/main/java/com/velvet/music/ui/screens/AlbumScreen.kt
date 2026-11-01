package com.velvet.music.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velvet.music.R
import com.velvet.music.data.Album
import com.velvet.music.data.Track
import com.velvet.music.ui.PlayerState
import com.velvet.music.ui.components.Artwork
import com.velvet.music.ui.components.CircleIconButton
import com.velvet.music.ui.components.EqualizerBars
import com.velvet.music.ui.components.GradientPill
import com.velvet.music.ui.components.SectionLabel
import com.velvet.music.ui.components.formatLibraryDuration
import com.velvet.music.ui.components.formatTime
import com.velvet.music.ui.theme.Ink
import com.velvet.music.ui.theme.LocalAccent
import com.velvet.music.ui.theme.TextPrimary
import com.velvet.music.ui.theme.TextSecondary
import com.velvet.music.ui.theme.TextTertiary

@Composable
fun AlbumScreen(
    album: Album,
    player: PlayerState,
    favorites: Set<String>,
    onBack: () -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onTogglePlay: () -> Unit,
    onToggleFavorite: (Track) -> Unit
) {
    val accent = LocalAccent.current
    val artwork = album.artworkTrack

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
        ) {
            Artwork(
                uri = artwork.artUri,
                color = artwork.color,
                modifier = Modifier.fillMaxSize(),
                corner = 0.dp,
                requestSize = 640,
                overlayNote = false
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Ink.copy(alpha = 0.55f),
                                Ink.copy(alpha = 0.75f),
                                Ink
                            )
                        )
                    )
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.glow.copy(alpha = 0.14f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.1f),
                    radius = size.width * 0.8f
                ),
                radius = size.width * 0.8f,
                center = Offset(size.width * 0.8f, size.height * 0.1f)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(
                start = 22.dp,
                end = 22.dp,
                top = 10.dp,
                bottom = 36.dp
            )
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleIconButton(
                        icon = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        onClick = onBack,
                        size = 42.dp,
                        iconSize = 20.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    SectionLabel("Album")
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = album.tracks.size.toString().padStart(2, '0') + " TRACKS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }

                Spacer(Modifier.height(24.dp))

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Artwork(
                        uri = artwork.artUri,
                        color = artwork.color,
                        modifier = Modifier
                            .size(188.dp)
                            .shadow(
                                elevation = 28.dp,
                                shape = RoundedCornerShape(26.dp),
                                ambientColor = Color(artwork.color).copy(alpha = 0.4f),
                                spotColor = Color(artwork.color).copy(alpha = 0.6f)
                            ),
                        corner = 26.dp,
                        requestSize = 480
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = album.name,
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = accent.bright,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${album.tracks.size} songs  ·  ${formatLibraryDuration(album.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GradientPill(
                        text = "Play album",
                        icon = painterResource(R.drawable.ic_play),
                        onClick = { onPlayAll(album.tracks, false) }
                    )
                    Spacer(Modifier.width(10.dp))
                    CircleIconButton(
                        icon = painterResource(R.drawable.ic_shuffle),
                        contentDescription = "Shuffle album",
                        onClick = { onPlayAll(album.tracks, true) },
                        size = 46.dp,
                        iconSize = 19.dp
                    )
                }

                Spacer(Modifier.height(24.dp))
            }

            itemsIndexed(album.tracks, key = { _, track -> track.id }) { index, track ->
                val isCurrent = player.mediaId == track.id
                val isFavorite = track.id in favorites
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isCurrent) accent.primary.copy(alpha = 0.075f) else Color.Transparent)
                        .clickable {
                            if (isCurrent) onTogglePlay()
                            else onPlayTrack(track, album.tracks)
                        }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(32.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (isCurrent) {
                            EqualizerBars(
                                playing = player.isPlaying,
                                color = accent.primary,
                                barWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = (index + 1).toString().padStart(2, '0'),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isCurrent) accent.bright else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = formatTime(track.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                    Spacer(Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable { onToggleFavorite(track) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_heart),
                            contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                            tint = if (isFavorite) accent.primary else TextTertiary.copy(alpha = 0.45f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}
