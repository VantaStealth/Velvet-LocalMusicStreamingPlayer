package com.velvet.music.data

data class Track(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val artUri: String? = null,
    val artPath: String? = null,
    val color: Int = 0xFF8B5CF6.toInt(),
    val addedAt: Long = 0L
)

data class Album(
    val key: String,
    val name: String,
    val artist: String,
    val tracks: List<Track>
) {
    val artworkTrack: Track get() = tracks.firstOrNull { it.artUri != null } ?: tracks.first()
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

fun buildAlbums(tracks: List<Track>): List<Album> = tracks
    .groupBy { it.album.ifBlank { "Unknown album" }.lowercase() }
    .map { (_, list) ->
        val artists = list.map { it.artist }.distinct()
        val ordered = list.sortedWith(
            compareBy<Track> { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )
        Album(
            key = list.first().album.ifBlank { "Unknown album" } + "|" + ordered.first().id,
            name = list.first().album.ifBlank { "Unknown album" },
            artist = if (artists.size > 1) "Various artists" else artists.firstOrNull() ?: "Unknown artist",
            tracks = ordered
        )
    }
    .sortedBy { it.name.lowercase() }

enum class SortMode(val label: String) {
    Title("A – Z"),
    Artist("Artist"),
    Album("Album"),
    Longest("Longest")
}

enum class VelvetAccent(val label: String) {
    Amethyst("Amethyst"),
    Gold("Gold"),
    Crimson("Crimson"),
    Azure("Azure")
}
