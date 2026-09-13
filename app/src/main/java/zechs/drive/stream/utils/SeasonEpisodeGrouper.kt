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

    enum class ItemCategory(val priority: Int) {
        STORY(1),
        SPECIAL(2),
        PROMO(3),
        MUSIC(4)
    }

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

    fun categorize(title: String, parsed: EpisodeParser.ParsedEpisode): ItemCategory {
        val badgeUpper = parsed.episodeBadge.uppercase(Locale.ROOT)
        val labelUpper = parsed.episodeLabel.uppercase(Locale.ROOT)
        val titleUpper = title.uppercase(Locale.ROOT)

        // Music: OP, ED, NCOP, NCED, OST
        if (badgeUpper in listOf("OP", "ED", "NCOP", "NCED", "OST") ||
            labelUpper.contains("ABERTURA") || labelUpper.contains("ENCERRAMENTO") ||
            titleUpper.contains("NCOP") || titleUpper.contains("NCED") ||
            titleUpper.contains("OPENING") || titleUpper.contains("ENDING") ||
            titleUpper.contains("THEME SONG")) {
            return ItemCategory.MUSIC
        }

        // Promos: PV, CM, Teaser, Preview
        if (badgeUpper in listOf("PV", "CM", "TEASER", "PREVIEW") ||
            labelUpper.contains("PREVIEW") || labelUpper.contains("TEASER") || labelUpper.contains("COMERCIAL") ||
            titleUpper.contains("TRAILER") || titleUpper.contains("PROMO")) {
            return ItemCategory.PROMO
        }

        // Specials: SP, OVA, OAD, Special
        if (parsed.isSpecial || badgeUpper in listOf("SP", "OVA", "OAD", "SPECIAL") ||
            labelUpper.contains("ESPECIAL") || labelUpper.contains("SPECIAL") || labelUpper.contains("OVA") || labelUpper.contains("OAD") ||
            titleUpper.contains("SPECIAL") || titleUpper.contains("OVA") || titleUpper.contains("OAD")) {
            return ItemCategory.SPECIAL
        }

        return ItemCategory.STORY
    }

    /**
     * Smart comparator for video files/episodes:
     * 1. Story episodes first (ordered by Season, then Episode 1..N)
     * 2. Specials & OVAs second (ordered by Season, then Special 1..N)
     * 3. Promos / Previews third (PV 1..N)
     * 4. Music / Openings & Endings last (OP 1..N, ED 1..N)
     */
    fun compareItems(
        titleA: String, parsedA: EpisodeParser.ParsedEpisode,
        titleB: String, parsedB: EpisodeParser.ParsedEpisode
    ): Int {
        val catA = categorize(titleA, parsedA)
        val catB = categorize(titleB, parsedB)

        if (catA != catB) {
            return catA.priority.compareTo(catB.priority)
        }

        return when (catA) {
            ItemCategory.STORY -> {
                val seasonA = parsedA.season ?: 1
                val seasonB = parsedB.season ?: 1
                if (seasonA != seasonB) {
                    seasonA.compareTo(seasonB)
                } else {
                    val epA = parsedA.episode ?: 0.0
                    val epB = parsedB.episode ?: 0.0
                    if (epA != epB) epA.compareTo(epB)
                    else EpisodeParser.naturalCompare(titleA, titleB)
                }
            }
            ItemCategory.SPECIAL -> {
                val seasonA = parsedA.season ?: 0
                val seasonB = parsedB.season ?: 0
                if (seasonA != seasonB) {
                    seasonA.compareTo(seasonB)
                } else {
                    val spA = parsedA.episode ?: 0.0
                    val spB = parsedB.episode ?: 0.0
                    if (spA != spB) spA.compareTo(spB)
                    else EpisodeParser.naturalCompare(titleA, titleB)
                }
            }
            ItemCategory.MUSIC -> {
                val isOpA = parsedA.episodeBadge.equals("OP", ignoreCase = true) || titleA.contains("OP", ignoreCase = true)
                val isOpB = parsedB.episodeBadge.equals("OP", ignoreCase = true) || titleB.contains("OP", ignoreCase = true)
                if (isOpA && !isOpB) -1
                else if (!isOpA && isOpB) 1
                else {
                    val numA = parsedA.episode ?: 0.0
                    val numB = parsedB.episode ?: 0.0
                    if (numA != numB) numA.compareTo(numB)
                    else EpisodeParser.naturalCompare(titleA, titleB)
                }
            }
            ItemCategory.PROMO -> {
                val numA = parsedA.episode ?: 0.0
                val numB = parsedB.episode ?: 0.0
                if (numA != numB) numA.compareTo(numB)
                else EpisodeParser.naturalCompare(titleA, titleB)
            }
        }
    }

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

        if (videoItems.isNotEmpty()) {
            val showLower = showName.lowercase(Locale.ROOT)

            // Categorize and sort all items intelligently
            val parsedFiles = videoItems.map { it to EpisodeParser.parse(it.driveFile.name) }
            val sortedFiles = parsedFiles.sortedWith { a, b ->
                compareItems(a.first.driveFile.name, a.second, b.first.driveFile.name, b.second)
            }.map { it.first }

            // 2. Canonical Arcs (One Piece, Bleach, Naruto, Hunter x Hunter)
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
                    return addAllEpisodesOption(arcGroups, sortedFiles)
                }
            }

            // 3. Separate categories: Story, Specials, Music, Promos
            val storyFiles = mutableListOf<Pair<FilesDataModel.File, EpisodeParser.ParsedEpisode>>()
            val specialFiles = mutableListOf<FilesDataModel.File>()
            val musicFiles = mutableListOf<FilesDataModel.File>()
            val promoFiles = mutableListOf<FilesDataModel.File>()

            for ((item, parsed) in parsedFiles) {
                when (categorize(item.driveFile.name, parsed)) {
                    ItemCategory.STORY -> storyFiles.add(item to parsed)
                    ItemCategory.SPECIAL -> specialFiles.add(item)
                    ItemCategory.MUSIC -> musicFiles.add(item)
                    ItemCategory.PROMO -> promoFiles.add(item)
                }
            }

            // Check seasons among STORY files only (ignore specials/music for season counting)
            val seasonsFound = storyFiles
                .mapNotNull { it.second.season }
                .distinct()
                .sorted()

            // Check distinct sub-franchise titles in story files (e.g. Kajitsu, Meikyuu, Rakuen)
            val distinctStems = extractDistinctSubStems(storyFiles.map { it.first.driveFile.name })

            val groups = mutableListOf<SeasonGroup>()

            if (seasonsFound.size > 1) {
                for (s in seasonsFound) {
                    val seasonItems = storyFiles
                        .filter { it.second.season == s }
                        .map { it.first }
                        .sortedWith { a, b ->
                            compareItems(a.driveFile.name, EpisodeParser.parse(a.driveFile.name), b.driveFile.name, EpisodeParser.parse(b.driveFile.name))
                        }

                    groups.add(
                        SeasonGroup(
                            id = "season_$s",
                            name = "Temporada $s",
                            subtitle = "${seasonItems.size} episódios",
                            fileItems = seasonItems
                        )
                    )
                }

                // Any story files without an explicit season (e.g. Prologue, Movie)
                val unassigned = storyFiles.filter { it.second.season == null }.map { it.first }
                if (unassigned.isNotEmpty()) {
                    val arcName = unassigned.firstOrNull()?.driveFile?.name?.let { extractArcOrPrequelName(it) } ?: "Prólogo / Extras"
                    groups.add(
                        SeasonGroup(
                            id = "season_prologue",
                            name = arcName,
                            subtitle = "${unassigned.size} episódios",
                            fileItems = unassigned
                        )
                    )
                }
            } else if (distinctStems.size > 1) {
                // Franchise clustering based on distinct titles in files (e.g. Grisaia no Kajitsu vs Rakuen)
                distinctStems.forEachIndexed { idx, stem ->
                    val stemItems = storyFiles
                        .filter { it.first.driveFile.name.contains(stem, ignoreCase = true) }
                        .map { it.first }
                        .sortedWith { a, b ->
                            compareItems(a.driveFile.name, EpisodeParser.parse(a.driveFile.name), b.driveFile.name, EpisodeParser.parse(b.driveFile.name))
                        }

                    if (stemItems.isNotEmpty()) {
                        groups.add(
                            SeasonGroup(
                                id = "stem_$idx",
                                name = cleanSeasonStemName(stem, idx + 1),
                                subtitle = "${stemItems.size} episódios",
                                fileItems = stemItems
                            )
                        )
                    }
                }
            }

            // Dedicated Group: Especiais & OVAs (if any specials exist)
            if (specialFiles.isNotEmpty()) {
                val sortedSpecials = specialFiles.sortedWith { a, b ->
                    compareItems(a.driveFile.name, EpisodeParser.parse(a.driveFile.name), b.driveFile.name, EpisodeParser.parse(b.driveFile.name))
                }
                groups.add(
                    SeasonGroup(
                        id = "specials_ovas",
                        name = "Especiais & OVAs",
                        subtitle = "${sortedSpecials.size} episódios",
                        fileItems = sortedSpecials
                    )
                )
            }

            // Dedicated Group: Aberturas & Encerramentos (if any OPs/EDs exist)
            if (musicFiles.isNotEmpty()) {
                val sortedMusic = musicFiles.sortedWith { a, b ->
                    compareItems(a.driveFile.name, EpisodeParser.parse(a.driveFile.name), b.driveFile.name, EpisodeParser.parse(b.driveFile.name))
                }
                groups.add(
                    SeasonGroup(
                        id = "openings_endings",
                        name = "Aberturas & Encerramentos",
                        subtitle = "${sortedMusic.size} faixas",
                        fileItems = sortedMusic
                    )
                )
            }

            // Dedicated Group: Trailers & Promos (if any PVs exist)
            if (promoFiles.isNotEmpty()) {
                val sortedPromos = promoFiles.sortedWith { a, b ->
                    compareItems(a.driveFile.name, EpisodeParser.parse(a.driveFile.name), b.driveFile.name, EpisodeParser.parse(b.driveFile.name))
                }
                groups.add(
                    SeasonGroup(
                        id = "promos_trailers",
                        name = "Trailers & Promos",
                        subtitle = "${sortedPromos.size} vídeos",
                        fileItems = sortedPromos
                    )
                )
            }

            if (groups.isNotEmpty()) {
                return addAllEpisodesOption(groups, sortedFiles)
            }

            // Batch grouping (> 25 episodes)
            if (videoItems.size > 25) {
                val batchGroups = groupFilesByBatches(sortedFiles, batchSize = 25)
                return addAllEpisodesOption(batchGroups, sortedFiles)
            }

            return listOf(
                SeasonGroup(
                    id = "season_all",
                    name = "Todos os Episódios",
                    subtitle = "${sortedFiles.size} episódios",
                    fileItems = sortedFiles
                )
            )
        }

        return listOf(
            SeasonGroup(
                id = "season_all",
                name = "Todos os Episódios",
                subtitle = "${fileItems.size} episódios",
                fileItems = fileItems
            )
        )
    }

    /**
     * Group a playlist of [PlaylistItem] for in-player Seasons & Arcs drawer.
     */
    fun groupPlaylist(showName: String, playlist: List<PlaylistItem>): List<SeasonGroup> {
        if (playlist.isEmpty()) return emptyList()

        val showLower = showName.lowercase(Locale.ROOT)

        // Categorize and sort all items intelligently
        val parsedPlaylist = playlist.map { it to EpisodeParser.parse(it.title) }
        val sortedPlaylist = parsedPlaylist.sortedWith { a, b ->
            compareItems(a.first.title, a.second, b.first.title, b.second)
        }.map { it.first }

        // 1. Canonical Arcs (One Piece, Bleach, Naruto, Hunter x Hunter)
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
                return addAllPlaylistOption(arcGroups, sortedPlaylist)
            }
        }

        // 2. Separate categories: Story, Specials, Music, Promos
        val storyItems = mutableListOf<Pair<PlaylistItem, EpisodeParser.ParsedEpisode>>()
        val specialItems = mutableListOf<PlaylistItem>()
        val musicItems = mutableListOf<PlaylistItem>()
        val promoItems = mutableListOf<PlaylistItem>()

        for ((item, parsed) in parsedPlaylist) {
            when (categorize(item.title, parsed)) {
                ItemCategory.STORY -> storyItems.add(item to parsed)
                ItemCategory.SPECIAL -> specialItems.add(item)
                ItemCategory.MUSIC -> musicItems.add(item)
                ItemCategory.PROMO -> promoItems.add(item)
            }
        }

        // Check seasons among STORY items only
        val seasonsFound = storyItems
            .mapNotNull { it.second.season }
            .distinct()
            .sorted()

        // Check distinct sub-franchise titles in story files
        val distinctStems = extractDistinctSubStems(storyItems.map { it.first.title })

        val groups = mutableListOf<SeasonGroup>()

        if (seasonsFound.size > 1) {
            for (s in seasonsFound) {
                val seasonItems = storyItems
                    .filter { it.second.season == s }
                    .map { it.first }
                    .sortedWith { a, b ->
                        compareItems(a.title, EpisodeParser.parse(a.title), b.title, EpisodeParser.parse(b.title))
                    }

                groups.add(
                    SeasonGroup(
                        id = "season_$s",
                        name = "Temporada $s",
                        subtitle = "${seasonItems.size} episódios",
                        playlistItems = seasonItems
                    )
                )
            }

            // Any story files without an explicit season
            val unassigned = storyItems.filter { it.second.season == null }.map { it.first }
            if (unassigned.isNotEmpty()) {
                val arcName = unassigned.firstOrNull()?.title?.let { extractArcOrPrequelName(it) } ?: "Prólogo / Extras"
                groups.add(
                    SeasonGroup(
                        id = "season_prologue",
                        name = arcName,
                        subtitle = "${unassigned.size} episódios",
                        playlistItems = unassigned
                    )
                )
            }
        } else if (distinctStems.size > 1) {
            distinctStems.forEachIndexed { idx, stem ->
                val stemItems = storyItems
                    .filter { it.first.title.contains(stem, ignoreCase = true) }
                    .map { it.first }
                    .sortedWith { a, b ->
                        compareItems(a.title, EpisodeParser.parse(a.title), b.title, EpisodeParser.parse(b.title))
                    }

                if (stemItems.isNotEmpty()) {
                    groups.add(
                        SeasonGroup(
                            id = "stem_$idx",
                            name = cleanSeasonStemName(stem, idx + 1),
                            subtitle = "${stemItems.size} episódios",
                            playlistItems = stemItems
                        )
                    )
                }
            }
        }

        // Dedicated Group: Especiais & OVAs
        if (specialItems.isNotEmpty()) {
            val sortedSpecials = specialItems.sortedWith { a, b ->
                compareItems(a.title, EpisodeParser.parse(a.title), b.title, EpisodeParser.parse(b.title))
            }
            groups.add(
                SeasonGroup(
                    id = "specials_ovas",
                    name = "Especiais & OVAs",
                    subtitle = "${sortedSpecials.size} episódios",
                    playlistItems = sortedSpecials
                )
            )
        }

        // Dedicated Group: Aberturas & Encerramentos
        if (musicItems.isNotEmpty()) {
            val sortedMusic = musicItems.sortedWith { a, b ->
                compareItems(a.title, EpisodeParser.parse(a.title), b.title, EpisodeParser.parse(b.title))
            }
            groups.add(
                SeasonGroup(
                    id = "openings_endings",
                    name = "Aberturas & Encerramentos",
                    subtitle = "${sortedMusic.size} faixas",
                    playlistItems = sortedMusic
                )
            )
        }

        // Dedicated Group: Trailers & Promos
        if (promoItems.isNotEmpty()) {
            val sortedPromos = promoItems.sortedWith { a, b ->
                compareItems(a.title, EpisodeParser.parse(a.title), b.title, EpisodeParser.parse(b.title))
            }
            groups.add(
                SeasonGroup(
                    id = "promos_trailers",
                    name = "Trailers & Promos",
                    subtitle = "${sortedPromos.size} vídeos",
                    playlistItems = sortedPromos
                )
            )
        }

        if (groups.isNotEmpty()) {
            return addAllPlaylistOption(groups, sortedPlaylist)
        }

        if (playlist.size > 25) {
            val batchGroups = groupPlaylistByBatches(sortedPlaylist, batchSize = 25)
            return addAllPlaylistOption(batchGroups, sortedPlaylist)
        }

        return listOf(
            SeasonGroup(
                id = "season_all",
                name = "Todos os Episódios",
                subtitle = "${sortedPlaylist.size} episódios",
                playlistItems = sortedPlaylist
            )
        )
    }

    private fun extractDistinctSubStems(titles: List<String>): List<String> {
        val candidates = mutableMapOf<String, Int>()
        for (raw in titles) {
            val clean = EpisodeParser.cleanShowTitle(raw)
            if (clean.length > 3) {
                candidates[clean] = (candidates[clean] ?: 0) + 1
            }
        }
        return candidates.filter { it.value >= 1 }.keys.toList().sorted()
    }

    private fun cleanSeasonStemName(stem: String, fallbackNum: Int): String {
        val clean = stem.replace(Regex("(?i)^(\\[.*?\\]|\\(.*?\\))"), "").trim()
        return if (clean.isNotBlank()) clean else "Temporada $fallbackNum"
    }

    private fun extractArcOrPrequelName(rawTitle: String): String {
        val parsed = EpisodeParser.parse(rawTitle)
        if (parsed.showTitle.isNotBlank()) {
            return parsed.showTitle
        }
        return "Prólogo / Meikyuu"
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
            }.sortedWith { a, b ->
                compareItems(a.driveFile.name, EpisodeParser.parse(a.driveFile.name), b.driveFile.name, EpisodeParser.parse(b.driveFile.name))
            }

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
                    fileItems = unmatched.sortedWith { a, b ->
                        compareItems(a.driveFile.name, EpisodeParser.parse(a.driveFile.name), b.driveFile.name, EpisodeParser.parse(b.driveFile.name))
                    }
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
            }.sortedWith { a, b ->
                compareItems(a.title, EpisodeParser.parse(a.title), b.title, EpisodeParser.parse(b.title))
            }

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
                    playlistItems = unmatched.sortedWith { a, b ->
                        compareItems(a.title, EpisodeParser.parse(a.title), b.title, EpisodeParser.parse(b.title))
                    }
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
