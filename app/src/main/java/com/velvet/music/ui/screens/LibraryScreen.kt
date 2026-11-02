package com.velvet.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velvet.music.R
import com.velvet.music.data.Album
import com.velvet.music.data.SortMode
import com.velvet.music.data.Track
import com.velvet.music.data.VelvetAccent
import com.velvet.music.ui.CloudItem
import com.velvet.music.ui.CloudState
import com.velvet.music.ui.LibraryState
import com.velvet.music.ui.PlayerState
import com.velvet.music.ui.ScanState
import com.velvet.music.ui.components.AccentPicker
import com.velvet.music.ui.components.Artwork
import com.velvet.music.ui.components.AuroraBackdrop
import com.velvet.music.ui.components.BrandMark
import com.velvet.music.ui.components.CircleIconButton
import com.velvet.music.ui.components.EqualizerBars
import com.velvet.music.ui.components.GhostChip
import com.velvet.music.ui.components.GradientPill
import com.velvet.music.ui.components.HairlineProgress
import com.velvet.music.ui.components.MiniPlayer
import com.velvet.music.ui.components.SectionLabel
import com.velvet.music.ui.components.VeinOverlay
import com.velvet.music.ui.components.formatLibraryDuration
import com.velvet.music.ui.components.formatTime
import com.velvet.music.ui.components.greeting
import com.velvet.music.ui.theme.Hairline
import com.velvet.music.ui.theme.Ink
import com.velvet.music.ui.theme.LocalAccent
import com.velvet.music.ui.theme.Surface1
import com.velvet.music.ui.theme.Surface2
import com.velvet.music.ui.theme.TextPrimary
import com.velvet.music.ui.theme.TextSecondary
import com.velvet.music.ui.theme.TextTertiary

@Composable
fun LibraryScreen(
    library: LibraryState,
    player: PlayerState,
    favorites: Set<String>,
    albums: List<Album>,
    accent: VelvetAccent,
    onAccent: (VelvetAccent) -> Unit,
    onChooseFolder: () -> Unit,
    onRescan: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: (List<Track>, Boolean) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onOpenPlayer: () -> Unit,
    onOpenAlbum: (Album) -> Unit,
    onOpenVolumeSettings: () -> Unit,
    cloud: CloudState,
    onCloudDownload: () -> Unit,
    onCloudClose: () -> Unit,
    onCloudToggle: (Int) -> Unit,
    onCloudSelectAll: (Boolean) -> Unit,
    onCloudRun: () -> Unit
) {
    val palette = LocalAccent.current
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var sortName by rememberSaveable { mutableStateOf(SortMode.Title.name) }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }

    val sortMode = runCatching { SortMode.valueOf(sortName) }.getOrDefault(SortMode.Title)

    val displayed = remember(library.tracks, query, sortMode, favoritesOnly, favorites) {
        val filtered = library.tracks.filter { track ->
            val matchesQuery = query.isBlank() ||
                track.title.contains(query, ignoreCase = true) ||
                track.artist.contains(query, ignoreCase = true) ||
                track.album.contains(query, ignoreCase = true)
            val matchesFavorite = !favoritesOnly || track.id in favorites
            matchesQuery && matchesFavorite
        }
        when (sortMode) {
            SortMode.Title -> filtered.sortedBy { it.title.lowercase() }
            SortMode.Artist -> filtered.sortedWith(
                compareBy<Track> { it.artist.lowercase() }
                    .thenBy { it.album.lowercase() }
                    .thenBy { it.title.lowercase() }
            )
            SortMode.Album -> filtered.sortedWith(
                compareBy<Track> { it.album.lowercase() }
                    .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
            )
            SortMode.Longest -> filtered.sortedByDescending { it.durationMs }
        }
    }

    val totalDuration = remember(library.tracks) { library.tracks.sumOf { it.durationMs } }
    val scan = library.scan

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Ink, Color(0xFF0A0810), Ink)))
    ) {
        AuroraBackdrop(
            primary = palette.glow,
            secondary = Color(0xFFD4AF37),
            modifier = Modifier.fillMaxSize(),
            intensity = 0.52f
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 150.dp
            )
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(36.dp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "VELVET",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.5.sp),
                            color = TextPrimary
                        )
                        Text(
                            text = library.folderName.ifBlank { "Your music" },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    AccentPicker(selected = accent, onSelect = onAccent)
                    Box {
                        CircleIconButton(
                            icon = painterResource(R.drawable.ic_more),
                            contentDescription = "More",
                            onClick = { moreMenu = true },
                            size = 36.dp,
                            iconSize = 16.dp
                        )
                        DropdownMenu(
                            expanded = moreMenu,
                            onDismissRequest = { moreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rescan folder", color = TextPrimary) },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, null, tint = TextSecondary)
                                },
                                onClick = {
                                    moreMenu = false
                                    onRescan()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Change folder", color = TextPrimary) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_folder),
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                },
                                onClick = {
                                    moreMenu = false
                                    onChooseFolder()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Volume keys takeover", color = TextPrimary) },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_volume),
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                },
                                onClick = {
                                    moreMenu = false
                                    onOpenVolumeSettings()
                                }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(26.dp))
                SectionLabel(greeting())
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "Your library",
                    style = MaterialTheme.typography.displayMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "${library.tracks.size} songs  ·  ${formatLibraryDuration(totalDuration)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GradientPill(
                        text = "Shuffle all",
                        icon = painterResource(R.drawable.ic_shuffle),
                        modifier = Modifier.weight(1f),
                        onClick = { if (displayed.isNotEmpty()) onPlayAll(displayed, true) }
                    )
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(
                        icon = painterResource(R.drawable.ic_cloud_download),
                        contentDescription = "Velvet Cloud",
                        onClick = onCloudDownload,
                        size = 42.dp,
                        iconSize = 18.dp,
                        tint = if (cloud.suggest > 0) palette.primary else TextPrimary,
                        background = if (cloud.suggest > 0) palette.primary.copy(alpha = 0.12f)
                        else Color.White.copy(alpha = 0.055f),
                        border = if (cloud.suggest > 0) palette.primary.copy(alpha = 0.30f)
                        else Color.White.copy(alpha = 0.08f)
                    )
                    Spacer(Modifier.width(8.dp))
                    CircleIconButton(
                        icon = painterResource(R.drawable.ic_search),
                        contentDescription = "Search",
                        onClick = {
                            searchOpen = !searchOpen
                            if (!searchOpen) query = ""
                        },
                        size = 46.dp,
                        iconSize = 20.dp,
                        tint = if (searchOpen) palette.primary else TextPrimary,
                        background = if (searchOpen) palette.primary.copy(alpha = 0.12f)
                        else Color.White.copy(alpha = 0.055f),
                        border = if (searchOpen) palette.primary.copy(alpha = 0.30f)
                        else Color.White.copy(alpha = 0.08f)
                    )
                }
                AnimatedVisibility(
                    visible = searchOpen,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        SearchField(
                            query = query,
                            onQueryChange = { query = it },
                            onClear = { query = "" }
                        )
                    }
                }
            }

            if (cloud.suggest > 0) {
                item {
                    Spacer(Modifier.height(16.dp))
                    CloudSuggestionCard(
                        count = cloud.suggest,
                        onDownload = onCloudDownload
                    )
                }
            }

            if (scan.active && library.tracks.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(14.dp))
                    ScanBanner(scan = scan)
                }
            }

            if (scan.error != null) {
                item {
                    Spacer(Modifier.height(16.dp))
                    ErrorCard(
                        message = scan.error,
                        onRescan = onRescan,
                        onChooseFolder = onChooseFolder
                    )
                }
            }

            if (query.isBlank() && !favoritesOnly && library.tracks.size >= 4) {
                item {
                    Spacer(Modifier.height(24.dp))
                    VelvetMixCard(
                        albums = albums,
                        trackCount = library.tracks.size,
                        onPlay = { onPlayAll(library.tracks, true) }
                    )
                }
            }

            if (query.isBlank() && albums.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(26.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Albums")
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${albums.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                    Spacer(Modifier.height(11.dp))
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(end = 4.dp)
                    ) {
                        items(albums, key = { it.key }) { album ->
                            AlbumCard(album = album, onClick = { onOpenAlbum(album) })
                        }
                    }
                }
            }

            if (library.tracks.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(26.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            SectionLabel("Collection")
                            Text(
                                text = if (query.isNotBlank()) "Search results" else "All songs",
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary
                            )
                        }
                        Box {
                            GhostChip(
                                text = sortMode.label,
                                onClick = { sortMenu = true }
                            )
                            DropdownMenu(
                                expanded = sortMenu,
                                onDismissRequest = { sortMenu = false }
                            ) {
                                SortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = mode.label,
                                                color = if (mode == sortMode) palette.primary else TextPrimary
                                            )
                                        },
                                        trailingIcon = {
                                            if (mode == sortMode) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = palette.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        },
                                        onClick = {
                                            sortName = mode.name
                                            sortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GhostChip(
                            text = "Favorites",
                            selected = favoritesOnly,
                            icon = painterResource(R.drawable.ic_heart),
                            onClick = { favoritesOnly = !favoritesOnly }
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${displayed.size} tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                items(displayed, key = { it.id }) { track ->
                    SongRow(
                        track = track,
                        isCurrent = player.mediaId == track.id,
                        isPlaying = player.isPlaying,
                        isFavorite = track.id in favorites,
                        onClick = {
                            if (player.mediaId == track.id) onTogglePlay()
                            else onPlayTrack(track, displayed)
                        },
                        onToggleFavorite = { onToggleFavorite(track) }
                    )
                }
            }

            if (library.tracks.isEmpty() && !scan.active) {
                item {
                    Spacer(Modifier.height(28.dp))
                    EmptyLibraryCard(onChooseFolder = onChooseFolder)
                }
            }

            if (query.isNotBlank() && displayed.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_note),
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Nothing matches \"$query\"",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = player.hasItem,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            enter = fadeIn(tween(260)) + androidx.compose.animation.slideInVertically(
                initialOffsetY = { it / 2 },
                animationSpec = tween(360)
            ),
            exit = fadeOut(tween(180)) + androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(240)
            )
        ) {
            MiniPlayer(
                player = player,
                onOpen = onOpenPlayer,
                onTogglePlay = onTogglePlay,
                onNext = onNext
            )
        }

        AnimatedVisibility(visible = scan.active && library.tracks.isEmpty()) {
            ScannerOverlay(scan = scan, onChooseFolder = onChooseFolder)
        }

        if (cloud.open) {
            CloudSheet(
                cloud = cloud,
                onClose = onCloudClose,
                onToggle = onCloudToggle,
                onSelectAll = onCloudSelectAll,
                onRun = onCloudRun
            )
        }
    }
}

@Composable
private fun CloudSuggestionCard(count: Int, onDownload: () -> Unit) {
    val palette = LocalAccent.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.primary.copy(alpha = 0.10f))
            .border(1.dp, palette.primary.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .clickable { onDownload() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(palette.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_cloud_download),
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "$count track${if (count == 1) "" else "s"} waiting in Velvet Cloud",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = "Tap to add them to this library",
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_arrow_down),
            contentDescription = null,
            tint = palette.primary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun CloudSheet(
    cloud: CloudState,
    onClose: () -> Unit,
    onToggle: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onRun: () -> Unit
) {
    val palette = LocalAccent.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.66f))
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                .background(Surface1)
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "VELVET CLOUD",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 2.6.sp),
                        color = palette.primary
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Your shared library, hosted online",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                CircleIconButton(
                    icon = painterResource(R.drawable.ic_close),
                    contentDescription = "Close",
                    onClick = onClose,
                    size = 32.dp,
                    iconSize = 15.dp,
                    background = Color.White.copy(alpha = 0.05f)
                )
            }
            Spacer(Modifier.height(14.dp))

            when {
                cloud.loading -> {
                    Text(
                        text = "Checking Velvet Cloud…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                    Spacer(Modifier.height(24.dp))
                }

                cloud.items.isEmpty() -> {
                    Text(
                        text = cloud.message
                            ?: "Everything in Velvet Cloud is already in your library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                }

                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GhostChip(text = "Select all", onClick = { onSelectAll(true) })
                        Spacer(Modifier.width(8.dp))
                        GhostChip(text = "None", onClick = { onSelectAll(false) })
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "${cloud.selectedCount} of ${cloud.items.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 360.dp)
                    ) {
                        items(cloud.items, key = { it.url }) { item ->
                            CloudRow(
                                item = item,
                                accent = palette.primary,
                                onAccent = palette.onAccent,
                                onToggle = { onToggle(cloud.items.indexOf(item)) }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (cloud.message != null) {
                        Text(
                            text = cloud.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (cloud.items.any { it.failed }) palette.primary else TextTertiary
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    GradientPill(
                        text = when {
                            cloud.busy -> "Downloading…"
                            cloud.selectedCount == 0 -> "Select tracks to download"
                            else -> "Download ${cloud.selectedCount} track${if (cloud.selectedCount == 1) "" else "s"}"
                        },
                        onClick = { if (!cloud.busy && cloud.selectedCount > 0) onRun() }
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}

@Composable
private fun CloudRow(
    item: CloudItem,
    accent: Color,
    onAccent: Color,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(21.dp)
                    .clip(CircleShape)
                    .background(if (item.selected) accent else Color.White.copy(alpha = 0.05f))
                    .border(
                        1.dp,
                        if (item.selected) accent else Color.White.copy(alpha = 0.12f),
                        CircleShape
                    )
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (item.selected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = onAccent,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.failed) TextTertiary else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.status.ifBlank { item.sizeLabel },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.failed) accent else TextTertiary
                )
            }
            if (item.progress > 0f) {
                Text(
                    text = "${(item.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = accent
                )
            }
        }
        if (item.progress > 0f && item.progress < 1f) {
            Spacer(Modifier.height(5.dp))
            HairlineProgress(
                fraction = item.progress,
                accent = accent,
                modifier = Modifier.padding(start = 32.dp)
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    val palette = LocalAccent.current
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
        cursorBrush = SolidColor(palette.primary),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.045f))
                    .border(1.dp, Hairline, shape)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Songs, artists, albums",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextTertiary
                        )
                    }
                    inner()
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .clickable { onClear() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun SongRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val palette = LocalAccent.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isCurrent) palette.primary.copy(alpha = 0.075f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Artwork(
                uri = track.artUri,
                color = track.color,
                modifier = Modifier.size(52.dp),
                corner = 14.dp,
                requestSize = 160
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.42f)),
                    contentAlignment = Alignment.Center
                ) {
                    EqualizerBars(
                        playing = isPlaying,
                        color = Color.White,
                        barWidth = 2.dp
                    )
                }
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) palette.bright else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = "${track.artist}  ·  ${track.album}",
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
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable { onToggleFavorite() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_heart),
                contentDescription = if (isFavorite) "Remove favorite" else "Add favorite",
                tint = if (isFavorite) palette.primary else TextTertiary.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun VelvetMixCard(albums: List<Album>, trackCount: Int, onPlay: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(28.dp)
    val mixNumber = remember {
        val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
        (day * 13 % 87) + 12
    }
    val picks = remember(albums) { albums.take(3).map { it.artworkTrack } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF23182F), Color(0xFF14101B), Color(0xFF0A0810))
                )
            )
            .border(1.dp, accent.primary.copy(alpha = 0.18f), shape)
            .clickable { onPlay() }
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.primary.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(size.width * 0.85f, size.height * 0.05f),
                    radius = size.width * 0.62f
                ),
                radius = size.width * 0.62f,
                center = Offset(size.width * 0.85f, size.height * 0.05f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.05f),
                radius = size.height * 0.62f,
                center = Offset(size.width * 0.94f, size.height * 0.70f),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        VeinOverlay(
            color = accent.primary,
            modifier = Modifier.matchParentSize(),
            alpha = 0.13f,
            veinCount = 6,
            seed = 71
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 166.dp)
                .padding(start = 20.dp, top = 22.dp, bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            ) {
                SectionLabel("Velvet session", color = accent.primary.copy(alpha = 0.85f))
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Mix No. $mixNumber",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TextPrimary
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "$trackCount tracks, reshuffled daily.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(accent.gradientStart, accent.primary, accent.gradientEnd)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play),
                            contentDescription = null,
                            tint = accent.onAccent,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Play the room",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(166.dp)
            ) {
                picks.forEachIndexed { index, track ->
                    val rotation = when (index) {
                        0 -> -12f
                        1 -> -2f
                        else -> 8f
                    }
                    val x = when (index) {
                        0 -> 0.dp
                        1 -> 26.dp
                        else -> 52.dp
                    }
                    val y = when (index) {
                        0 -> 30.dp
                        1 -> 12.dp
                        else -> 24.dp
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = x, y = y)
                            .size(102.dp)
                            .graphicsLayer { rotationZ = rotation }
                            .shadow(
                                elevation = 14.dp,
                                shape = RoundedCornerShape(18.dp),
                                ambientColor = Color.Black.copy(alpha = 0.5f),
                                spotColor = Color.Black.copy(alpha = 0.6f)
                            )
                            .clip(RoundedCornerShape(18.dp))
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(18.dp)
                            )
                    ) {
                        Artwork(
                            uri = track.artUri,
                            color = track.color,
                            modifier = Modifier.fillMaxSize(),
                            corner = 18.dp,
                            requestSize = 220
                        )
                    }
                }
                if (picks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(accent.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_note),
                            contentDescription = null,
                            tint = accent.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable { onClick() }
    ) {
        Box {
            Artwork(
                uri = album.artworkTrack.artUri,
                color = album.artworkTrack.color,
                modifier = Modifier.size(150.dp),
                corner = 22.dp,
                requestSize = 320
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f)
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.32f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ScanBanner(scan: ScanState) {
    val palette = LocalAccent.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.primary.copy(alpha = 0.07f))
            .border(1.dp, palette.primary.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LinearProgressIndicator(
            progress = { scan.fraction },
            modifier = Modifier
                .weight(1f)
                .height(3.dp)
                .clip(RoundedCornerShape(50)),
            color = palette.primary,
            trackColor = Color.White.copy(alpha = 0.08f),
            strokeCap = StrokeCap.Round
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Scanning ${scan.done}/${scan.total}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun ErrorCard(message: String, onRescan: () -> Unit, onChooseFolder: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF220F16))
            .border(1.dp, Color(0xFF5B1B2A), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Library out of tune",
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFFF9AAA)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostChip(text = "Rescan", onClick = onRescan)
            GhostChip(text = "Choose folder", onClick = onChooseFolder)
        }
    }
}

@Composable
private fun EmptyLibraryCard(onChooseFolder: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Surface1)
            .border(1.dp, Hairline, RoundedCornerShape(26.dp))
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandMark(54.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No music here yet",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Velvet searched this folder and found nothing it can play. Try another folder filled with MP3, FLAC, WAV or M4A files.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        GradientPill(
            text = "Choose another folder",
            icon = painterResource(R.drawable.ic_folder),
            onClick = onChooseFolder
        )
    }
}

@Composable
private fun ScannerOverlay(scan: ScanState, onChooseFolder: () -> Unit) {
    val palette = LocalAccent.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.97f))
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SpinningRecord()
            Spacer(Modifier.height(30.dp))
            SectionLabel("One moment")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tuning your library",
                style = MaterialTheme.typography.headlineLarge,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = scan.currentFile.ifBlank { "Reading your files…" },
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(22.dp))
            LinearProgressIndicator(
                progress = { scan.fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50)),
                color = palette.primary,
                trackColor = Color.White.copy(alpha = 0.08f),
                strokeCap = StrokeCap.Round
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (scan.total > 0) "${scan.done} of ${scan.total} tracks" else "Looking for audio files",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(30.dp))
            GhostChip(text = "Change folder", onClick = onChooseFolder)
        }
    }
}

@Composable
private fun SpinningRecord() {
    val palette = LocalAccent.current
    val transition = rememberInfiniteTransition(label = "scanner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "spin"
    )
    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0xFF0D0A12))
            for (ring in 1..6) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.05f),
                    radius = size.minDimension * (0.18f + ring * 0.05f),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(palette.gradientStart, palette.gradientEnd)
                ),
                radius = size.minDimension * 0.16f
            )
            drawCircle(Color(0xFF07060A), radius = size.minDimension * 0.028f)
            rotate(rotation) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(Color.Transparent, palette.bright.copy(alpha = 0.75f), Color.Transparent)
                    ),
                    startAngle = 0f,
                    sweepAngle = 80f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.06f, size.height * 0.06f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.88f, size.height * 0.88f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_note),
            contentDescription = null,
            tint = palette.onAccent,
            modifier = Modifier.size(16.dp)
        )
    }
}
