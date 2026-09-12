package zechs.drive.stream.utils

import zechs.drive.stream.data.model.PlaylistItem
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import java.util.Locale

data class SeasonGroup(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val folderId: String? = null,
    val fileItems: List<FilesDataModel> = emptyList(),
    val playlistItems: List<PlaylistItem> = emptyList()
) {
    val count: Int
        get() = if (fileItems.isNotEmpty()) fileItems.size else playlistItems.size
}

object SeasonEpisodeGrouper {

    private data class CanonicalArc(
        val name: String,
        val startEp: Int,
        val endEp: Int
    )

    // One Piece Canonical Arcs (matching Netflix / Crunchyroll)
    private val ONE_PIECE_ARCS = listOf(
        CanonicalArc("Romance Dawn & East Blue", 1, 61),
        CanonicalArc("Alabasta", 62, 135),
        CanonicalArc("Skypiea", 136, 206),
        CanonicalArc("G-8 / The Naval Fortress", 196, 206),
        CanonicalArc("The Foxy Pirate Crew", 207, 228),
        CanonicalArc("Water Seven", 229, 263),
        CanonicalArc("Enies Lobby & CP9", 264, 312),
        CanonicalArc("Pós-Enies Lobby", 313, 325),
        CanonicalArc("Thriller Bark", 326, 384),
        CanonicalArc("Arquipélago de Sabaody", 385, 407),
        CanonicalArc("Amazon Lily & Impel Down", 408, 452),
        CanonicalArc("Guerra dos Maiorais / Marineford", 453, 516),
        CanonicalArc("Ilha dos Homens-Peixe", 517, 574),
        CanonicalArc("Punk Hazard", 575, 628),
        CanonicalArc("Dressrosa", 629, 746),
        CanonicalArc("Zou & Whole Cake Island", 747, 891),
        CanonicalArc("País de Wano", 892, 1085),
        CanonicalArc("Egghead Arc", 1086, 1200)
    )

    // Bleach Canonical Arcs
    private val BLEACH_ARCS = listOf(
        CanonicalArc("Shinigami Substituto", 1, 20),
        CanonicalArc("Sociedade das Almas", 21, 63),
        CanonicalArc("Bount", 64, 109),
        CanonicalArc("Arrancar: Chegada", 110, 131),
        CanonicalArc("Hueco Mundo", 132, 151),
        CanonicalArc("Arrancar: Luta Feroz", 152, 167),
        CanonicalArc("Capitão Amagai Shūsuke", 168, 189),
        CanonicalArc("Arrancar vs Shinigami", 190, 205),
        CanonicalArc("Passado", 206, 212),
        CanonicalArc("Batalha de Karakura", 213, 229),
        CanonicalArc("Zanpakutou Desconhecida", 230, 265),
        CanonicalArc("Queda dos Arrancars", 266, 316),
        CanonicalArc("Gotei 13 Invasão", 317, 342),
        CanonicalArc("Shinigami Substituto Desaparecido", 343, 366),
        CanonicalArc("Guerra Sangrenta dos Mil Anos (TYBW)", 367, 450)
    )

    // Naruto Shippuden Arcs
    private val NARUTO_SHIPPUDEN_ARCS = listOf(
        CanonicalArc("Resgate do Kazekage", 1, 32),
        CanonicalArc("Reencontro com Sasuke", 33, 53),
        CanonicalArc("Doze Guardiões Ninja", 54, 71),
        CanonicalArc("Hidan e Kakuzu", 72, 88),
        CanonicalArc("Três-Caudas", 89, 112),
        CanonicalArc("Perseguição a Itachi", 113, 143),
        CanonicalArc("Seis-Caudas", 144, 151),
        CanonicalArc("Dois Salvadores (Pain)", 152, 175),
        CanonicalArc("Passado: Lugar de Konoha", 176, 196),
        CanonicalArc("Reunião dos Cinco Kages", 197, 214),
        CanonicalArc("Quarta Guerra Mundial Ninja", 215, 375),
        CanonicalArc("Nascimento do Jinchuuriki do Dez-Caudas", 376, 479),
        CanonicalArc("Kaguya Otsutsuki", 480, 500)
    )

    // Hunter x Hunter Arcs
    private val HUNTER_X_HUNTER_ARCS = listOf(
        CanonicalArc("Exame Hunter", 1, 26),
        CanonicalArc("Família Zoldyck & Torre Celestial", 27, 58),
        CanonicalArc("Leilão de Yorknew (Trupe Fantasma)", 59, 75),
        CanonicalArc("Greed Island", 76, 92),
        CanonicalArc("Formigas Quimera (Chimera Ant)", 93, 136),
        CanonicalArc("Eleição do 13º Presidente", 137, 148)
    )

    /**
     * Group files in [FilesFragment] into Seasons / Arcs.
     */
    fun groupFiles(showName: String, files: List<FilesDataModel>): List<SeasonGroup> {
        val fileItems = files.filterIsInstance<FilesDataModel.File>()
        if (fileItems.isEmpty()) return emptyList()

        // 1. Check if folder contains season subfolders
        val folderItems = fileItems.filter { it.driveFile.isFolder || it.driveFile.isShortcutFolder }
        val videoItems = fileItems.filter { it.driveFile.isVideoFile || (it.driveFile.isShortcut && it.driveFile.isShortcutVideo) }

        if (folderItems.size >= 2 && videoItems.size <= 2) {
            // Folder structure: User has subfolders per season/arc!
            val groups = mutableListOf<SeasonGroup>()
            folderItems.forEach { folderItem ->
                val f = folderItem.driveFile
                val targetId = if (f.isShortcut) f.shortcutDetails.targetId ?: f.id else f.id
                groups.add(
                    SeasonGroup(
                        id = targetId,
                        name = cleanSeasonFolderName(f.name),
                        folderId = targetId,
                        fileItems = listOf(folderItem)
                    )
                )
            }
            return groups
        }

        // If we have video items, group them
        if (videoItems.isNotEmpty()) {
            val showLower = showName.lowercase(Locale.ROOT)

            // 2. Check canonical arcs for known anime
            val canonicalArcs = when {
                showLower.contains("one piece") -> ONE_PIECE_ARCS
                showLower.contains("bleach") -> BLEACH_ARCS
                showLower.contains("shippuden") -> NARUTO_SHIPPUDEN_ARCS
                showLower.contains("hunter") -> HUNTER_X_HUNTER_ARCS
                else -> null
            }

            if (canonicalArcs != null) {
                val arcGroups = groupFilesByCanonicalArcs(canonicalArcs, videoItems)
                if (arcGroups.size > 1) {
                    return addAllEpisodesOption(arcGroups, videoItems)
                }
            }

            // 3. Check parsed season numbers (S01, S02, Temporada 1, etc.)
            val parsedWithSeasons = videoItems.map { item ->
                val parsed = EpisodeParser.parse(item.driveFile.name)
                item to parsed
            }

            val seasonsFound = parsedWithSeasons
                .mapNotNull { it.second.season }
                .distinct()
                .sorted()

            if (seasonsFound.size > 1) {
                val groups = mutableListOf<SeasonGroup>()
                for (s in seasonsFound) {
                    val seasonItems = parsedWithSeasons
                        .filter { it.second.season == s }
                        .map { it.first }
                        .sortedWith { a, b -> EpisodeParser.naturalCompare(a.driveFile.name, b.driveFile.name) }

                    val seasonName = if (s == 0) "Especiais & OVAs" else "Temporada $s"
                    groups.add(
                        SeasonGroup(
                            id = "season_$s",
                            name = seasonName,
                            subtitle = "${seasonItems.size} episódios",
                            fileItems = seasonItems
                        )
                    )
                }

                // Any files without season or specials
                val unassigned = parsedWithSeasons
                    .filter { it.second.season == null }
                    .map { it.first }
                if (unassigned.isNotEmpty()) {
                    groups.add(
                        SeasonGroup(
                            id = "season_other",
                            name = "Outros Episódios",
                            subtitle = "${unassigned.size} episódios",
                            fileItems = unassigned
                        )
                    )
                }

                return addAllEpisodesOption(groups, videoItems)
            }

            // 4. Batch grouping for long-running series without season tags (> 25 episodes)
            if (videoItems.size > 25) {
                val sorted = videoItems.sortedWith { a, b ->
                    EpisodeParser.naturalCompare(a.driveFile.name, b.driveFile.name)
                }
                val batchGroups = groupFilesByBatches(sorted, batchSize = 25)
                return addAllEpisodesOption(batchGroups, sorted)
            }
        }

        // Single season or no grouping needed
        return listOf(
            SeasonGroup(
                id = "season_all",
                name = "Todos os Episódios",
                subtitle = "${videoItems.size} episódios",
                fileItems = videoItems.ifEmpty { fileItems }
            )
        )
    }

    /**
     * Group a playlist of [PlaylistItem] for in-player Seasons & Arcs drawer.
     */
    fun groupPlaylist(showName: String, playlist: List<PlaylistItem>): List<SeasonGroup> {
        if (playlist.isEmpty()) return emptyList()

        val showLower = showName.lowercase(Locale.ROOT)

        // 1. Canonical Arcs
        val canonicalArcs = when {
            showLower.contains("one piece") || playlist.any { it.title.contains("one piece", ignoreCase = true) } -> ONE_PIECE_ARCS
            showLower.contains("bleach") || playlist.any { it.title.contains("bleach", ignoreCase = true) } -> BLEACH_ARCS
            showLower.contains("shippuden") || playlist.any { it.title.contains("shippuden", ignoreCase = true) } -> NARUTO_SHIPPUDEN_ARCS
            showLower.contains("hunter") || playlist.any { it.title.contains("hunter", ignoreCase = true) } -> HUNTER_X_HUNTER_ARCS
            else -> null
        }

        if (canonicalArcs != null) {
            val arcGroups = groupPlaylistByCanonicalArcs(canonicalArcs, playlist)
            if (arcGroups.size > 1) {
                return addAllPlaylistOption(arcGroups, playlist)
            }
        }

        // 2. Parsed season numbers
        val parsedList = playlist.map { item ->
            val parsed = EpisodeParser.parse(item.title)
            item to parsed
        }

        val seasonsFound = parsedList
            .mapNotNull { it.second.season }
            .distinct()
            .sorted()

        if (seasonsFound.size > 1) {
            val groups = mutableListOf<SeasonGroup>()
            for (s in seasonsFound) {
                val seasonItems = parsedList
                    .filter { it.second.season == s }
                    .map { it.first }
                    .sortedWith { a, b -> EpisodeParser.naturalCompare(a.title, b.title) }

                val seasonName = if (s == 0) "Especiais & OVAs" else "Temporada $s"
                groups.add(
                    SeasonGroup(
                        id = "season_$s",
                        name = seasonName,
                        subtitle = "${seasonItems.size} episódios",
                        playlistItems = seasonItems
                    )
                )
            }
            return addAllPlaylistOption(groups, playlist)
        }

        // 3. Batch grouping (> 25 episodes)
        if (playlist.size > 25) {
            val sorted = playlist.sortedWith { a, b -> EpisodeParser.naturalCompare(a.title, b.title) }
            val batchGroups = groupPlaylistByBatches(sorted, batchSize = 25)
            return addAllPlaylistOption(batchGroups, sorted)
        }

        // Single season
        return listOf(
            SeasonGroup(
                id = "season_all",
                name = "Todos os Episódios",
                subtitle = "${playlist.size} episódios",
                playlistItems = playlist
            )
        )
    }

    private fun groupFilesByCanonicalArcs(
        arcs: List<CanonicalArc>,
        files: List<FilesDataModel.File>
    ): List<SeasonGroup> {
        val groups = mutableListOf<SeasonGroup>()
        val matchedFiles = mutableSetOf<FilesDataModel.File>()

        for (arc in arcs) {
            val arcItems = files.filter { item ->
                val ep = EpisodeParser.parse(item.driveFile.name).episode?.toInt()
                ep != null && ep in arc.startEp..arc.endEp
            }.sortedWith { a, b -> EpisodeParser.naturalCompare(a.driveFile.name, b.driveFile.name) }

            if (arcItems.isNotEmpty()) {
                matchedFiles.addAll(arcItems)
                val epRange = "Ep. ${arc.startEp} - ${arc.endEp}"
                groups.add(
                    SeasonGroup(
                        id = "arc_${arc.startEp}_${arc.endEp}",
                        name = arc.name,
                        subtitle = "$epRange • ${arcItems.size} eps",
                        fileItems = arcItems
                    )
                )
            }
        }

        val unmatched = files.filterNot { matchedFiles.contains(it) }
        if (unmatched.isNotEmpty()) {
            groups.add(
                SeasonGroup(
                    id = "arc_others",
                    name = "Outros Episódios",
                    subtitle = "${unmatched.size} episódios",
                    fileItems = unmatched.sortedWith { a, b -> EpisodeParser.naturalCompare(a.driveFile.name, b.driveFile.name) }
                )
            )
        }

        return groups
    }

    private fun groupPlaylistByCanonicalArcs(
        arcs: List<CanonicalArc>,
        playlist: List<PlaylistItem>
    ): List<SeasonGroup> {
        val groups = mutableListOf<SeasonGroup>()
        val matched = mutableSetOf<PlaylistItem>()

        for (arc in arcs) {
            val arcItems = playlist.filter { item ->
                val ep = EpisodeParser.parse(item.title).episode?.toInt()
                ep != null && ep in arc.startEp..arc.endEp
            }.sortedWith { a, b -> EpisodeParser.naturalCompare(a.title, b.title) }

            if (arcItems.isNotEmpty()) {
                matched.addAll(arcItems)
                val epRange = "Ep. ${arc.startEp} - ${arc.endEp}"
                groups.add(
                    SeasonGroup(
                        id = "arc_${arc.startEp}_${arc.endEp}",
                        name = arc.name,
                        subtitle = "$epRange • ${arcItems.size} eps",
                        playlistItems = arcItems
                    )
                )
            }
        }

        val unmatched = playlist.filterNot { matched.contains(it) }
        if (unmatched.isNotEmpty()) {
            groups.add(
                SeasonGroup(
                    id = "arc_others",
                    name = "Outros Episódios",
                    subtitle = "${unmatched.size} episódios",
                    playlistItems = unmatched.sortedWith { a, b -> EpisodeParser.naturalCompare(a.title, b.title) }
                )
            )
        }

        return groups
    }

    private fun groupFilesByBatches(
        sortedFiles: List<FilesDataModel.File>,
        batchSize: Int
    ): List<SeasonGroup> {
        val groups = mutableListOf<SeasonGroup>()
        val chunks = sortedFiles.chunked(batchSize)
        chunks.forEachIndexed { idx, chunk ->
            val start = idx * batchSize + 1
            val end = start + chunk.size - 1
            groups.add(
                SeasonGroup(
                    id = "batch_$idx",
                    name = "Episódios $start - $end",
                    subtitle = "${chunk.size} episódios",
                    fileItems = chunk
                )
            )
        }
        return groups
    }

    private fun groupPlaylistByBatches(
        sortedPlaylist: List<PlaylistItem>,
        batchSize: Int
    ): List<SeasonGroup> {
        val groups = mutableListOf<SeasonGroup>()
        val chunks = sortedPlaylist.chunked(batchSize)
        chunks.forEachIndexed { idx, chunk ->
            val start = idx * batchSize + 1
            val end = start + chunk.size - 1
            groups.add(
                SeasonGroup(
                    id = "batch_$idx",
                    name = "Episódios $start - $end",
                    subtitle = "${chunk.size} episódios",
                    playlistItems = chunk
                )
            )
        }
        return groups
    }

    private fun addAllEpisodesOption(
        groups: List<SeasonGroup>,
        allFiles: List<FilesDataModel.File>
    ): List<SeasonGroup> {
        val allOption = SeasonGroup(
            id = "season_all",
            name = "Todos os Episódios",
            subtitle = "${allFiles.size} episódios",
            fileItems = allFiles
        )
        return listOf(allOption) + groups
    }

    private fun addAllPlaylistOption(
        groups: List<SeasonGroup>,
        allPlaylist: List<PlaylistItem>
    ): List<SeasonGroup> {
        val allOption = SeasonGroup(
            id = "season_all",
            name = "Todos os Episódios",
            subtitle = "${allPlaylist.size} episódios",
            playlistItems = allPlaylist
        )
        return listOf(allOption) + groups
    }

    private fun cleanSeasonFolderName(raw: String): String {
        return raw.replace(Regex("(?i)^(?:season|temporada|arco|arc)\\s*"), "")
            .trim()
            .ifBlank { raw }
            .let { cleaned ->
                if (cleaned.toIntOrNull() != null) "Temporada $cleaned" else raw
            }
    }
}
