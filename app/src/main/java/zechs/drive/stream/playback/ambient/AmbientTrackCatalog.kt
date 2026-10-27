package zechs.drive.stream.playback.ambient

import java.io.File

data class AmbientTrack(
    val id: String,
    val title: String,
    val artist: String = "Ryuichi Sakamoto",
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val sourceUri: String? = null
) {
    val displayLabel: String
        get() = if (artist.isNotBlank()) "$title — $artist" else title
}

object AmbientTrackCatalog {

    /**
     * Canonical 15-track chapter index for Ryuichi Sakamoto's Opus concert.
     */
    val OPUS_CANONICAL_CHAPTERS = listOf(
        AmbientTrack("opus_01", "Intro", "Ryuichi Sakamoto", 0L, 6_000L),
        AmbientTrack("opus_02", "Lack of Love", "Ryuichi Sakamoto", 6_000L, 289_000L),
        AmbientTrack("opus_03", "Solitude", "Ryuichi Sakamoto", 289_000L, 582_000L),
        AmbientTrack("opus_04", "Aubade 2020", "Ryuichi Sakamoto", 582_000L, 799_000L),
        AmbientTrack("opus_05", "Mizu no Naka no Bagatelle", "Ryuichi Sakamoto", 799_000L, 1054_000L),
        AmbientTrack("opus_06", "Bibo no Aozora", "Ryuichi Sakamoto", 1054_000L, 1362_000L),
        AmbientTrack("opus_07", "Aqua", "Ryuichi Sakamoto", 1362_000L, 1630_000L),
        AmbientTrack("opus_08", "Tong Poo", "Ryuichi Sakamoto", 1630_000L, 1932_000L),
        AmbientTrack("opus_09", "The Wuthering Heights", "Ryuichi Sakamoto", 1932_000L, 2260_000L),
        AmbientTrack("opus_10", "20220302 - sarabande", "Ryuichi Sakamoto", 2260_000L, 2448_000L),
        AmbientTrack("opus_11", "The Sheltering Sky", "Ryuichi Sakamoto", 2448_000L, 2823_000L),
        AmbientTrack("opus_12", "The Last Emperor", "Ryuichi Sakamoto", 2823_000L, 3215_000L),
        AmbientTrack("opus_13", "Happy End", "Ryuichi Sakamoto", 3215_000L, 3518_000L),
        AmbientTrack("opus_14", "Merry Christmas Mr. Lawrence", "Ryuichi Sakamoto", 3518_000L, 3853_000L),
        AmbientTrack("opus_15", "opus", "Ryuichi Sakamoto", 3853_000L, 4153_000L)
    )

    /**
     * Fallback public domain / CC0 minimalist solo piano pieces for zero-config startup.
     */
    val PUBLIC_DOMAIN_PIANO_TRACKS = listOf(
        AmbientTrack(
            id = "satie_gymnopedie_1",
            title = "Gymnopédie No. 1",
            artist = "Erik Satie",
            sourceUri = "https://ia800501.us.archive.org/1/items/ErikSatieGymnopedieNo1/GymnopedieNo1.mp3"
        ),
        AmbientTrack(
            id = "debussy_clair_de_lune",
            title = "Clair de Lune",
            artist = "Claude Debussy",
            sourceUri = "https://ia800301.us.archive.org/15/items/ClairDeLune_83/ClairDeLune.mp3"
        ),
        AmbientTrack(
            id = "satie_gnossienne_1",
            title = "Gnossienne No. 1",
            artist = "Erik Satie",
            sourceUri = "https://ia800501.us.archive.org/1/items/ErikSatieGymnopedieNo1/GnossienneNo1.mp3"
        )
    )

    /**
     * Parses custom chapters from a CUE sheet or a text file formatted with lines like:
     * `04:49 Solitude` or `01:04:13 opus`
     */
    fun parseTimestampChapters(content: String, artist: String = "Ryuichi Sakamoto"): List<AmbientTrack> {
        val tracks = mutableListOf<AmbientTrack>()
        val regex = Regex("""(?:(\d{1,2}):)?(\d{2}):(\d{2})\s+[-–]?\s*(.+)""")
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }

        var idx = 1
        for (line in lines) {
            val match = regex.find(line)
            if (match != null) {
                val hours = match.groupValues[1].toLongOrNull() ?: 0L
                val minutes = match.groupValues[2].toLongOrNull() ?: 0L
                val seconds = match.groupValues[3].toLongOrNull() ?: 0L
                val title = match.groupValues[4].trim()

                val startMs = (hours * 3600L + minutes * 60L + seconds) * 1000L
                tracks.add(
                    AmbientTrack(
                        id = "track_${idx++}",
                        title = title,
                        artist = artist,
                        startMs = startMs
                    )
                )
            }
        }

        // Fill endMs based on the next track's startMs
        return tracks.mapIndexed { index, track ->
            val nextStart = tracks.getOrNull(index + 1)?.startMs ?: 0L
            track.copy(endMs = nextStart)
        }
    }
}
