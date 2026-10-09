package zechs.drive.stream.domain.library

import java.util.Locale

/**
 * Modelo genérico de item agrupável para algoritmos de organização de catálogo.
 * Desacoplado de adaptadores de UI e fontes de dados.
 */
data class GroupableItem(
    val id: String,
    val name: String,
    val isFolder: Boolean = false,
    val isVideo: Boolean = true
)

/**
 * Grupo de temporada, arco narrativo ou lote de episódios no domínio.
 */
data class DomainSeasonGroup(
    val id: String,
    val name: String,
    val subtitle: String? = null,
    val folderId: String? = null,
    val items: List<GroupableItem> = emptyList()
) {
    val count: Int get() = items.size
}

/**
 * Algoritmo puro de agrupamento de mídias em temporadas, arcos canônicos e categorias especiais.
 * Lógica pura de domínio sem dependências do Android.
 */
object SeasonGrouper {

    fun groupItems(showName: String, items: List<GroupableItem>): List<DomainSeasonGroup> {
        if (items.isEmpty()) return emptyList()

        // 1. Verificação de subpastas de temporada
        val folderItems = items.filter { it.isFolder }
        val videoItems = items.filter { it.isVideo }

        if (folderItems.size >= 2 && videoItems.size <= 2) {
            return folderItems.map { folder ->
                DomainSeasonGroup(
                    id = folder.id,
                    name = cleanSeasonFolderName(folder.name),
                    folderId = folder.id,
                    items = listOf(folder)
                )
            }
        }

        if (videoItems.isNotEmpty()) {
            val showLower = showName.lowercase(Locale.ROOT)

            val parsedItems = videoItems.map { it to EpisodeParser.parse(it.name) }
            val sortedItems = parsedItems.sortedWith { a, b ->
                EpisodeCategorizer.compareItems(a.first.name, a.second, b.first.name, b.second)
            }.map { it.first }

            // 2. Arcos canônicos (One Piece, Bleach, Naruto Shippuden, Hunter x Hunter)
            val canonicalArcs = when {
                showLower.contains("one piece") || videoItems.any { it.name.contains("one piece", ignoreCase = true) } -> CanonicalAnimeArcs.ONE_PIECE
                showLower.contains("bleach") || videoItems.any { it.name.contains("bleach", ignoreCase = true) } -> CanonicalAnimeArcs.BLEACH
                showLower.contains("shippuden") || videoItems.any { it.name.contains("shippuden", ignoreCase = true) } -> CanonicalAnimeArcs.NARUTO_SHIPPUDEN
                showLower.contains("hunter") || videoItems.any { it.name.contains("hunter", ignoreCase = true) } -> CanonicalAnimeArcs.HUNTER_X_HUNTER
                else -> null
            }

            if (canonicalArcs != null) {
                val arcGroups = groupItemsByCanonicalArcs(canonicalArcs, videoItems)
                if (arcGroups.size > 1) {
                    val primaryEpisodes = sortedItems.filter { item ->
                        EpisodeCategorizer.categorize(item.name, EpisodeParser.parse(item.name)) == MediaItemCategory.STORY
                    }.ifEmpty { sortedItems }
                    return addAllEpisodesOption(arcGroups, primaryEpisodes)
                }
            }

            // 3. Separação por categorias: História, Especiais, Músicas, Promos
            val storyItems = mutableListOf<Pair<GroupableItem, EpisodeParser.ParsedEpisode>>()
            val specialItems = mutableListOf<GroupableItem>()
            val musicItems = mutableListOf<GroupableItem>()
            val promoItems = mutableListOf<GroupableItem>()

            for ((item, parsed) in parsedItems) {
                when (EpisodeCategorizer.categorize(item.name, parsed)) {
                    MediaItemCategory.STORY -> storyItems.add(item to parsed)
                    MediaItemCategory.SPECIAL -> specialItems.add(item)
                    MediaItemCategory.MUSIC -> musicItems.add(item)
                    MediaItemCategory.PROMO -> promoItems.add(item)
                }
            }

            val seasonsFound = storyItems
                .mapNotNull { it.second.season }
                .distinct()
                .sorted()

            val distinctStems = extractDistinctSubStems(storyItems.map { it.first.name })

            val groups = mutableListOf<DomainSeasonGroup>()

            if (seasonsFound.size > 1) {
                for (s in seasonsFound) {
                    val seasonItems = storyItems
                        .filter { it.second.season == s }
                        .map { it.first }
                        .sortedWith { a, b ->
                            EpisodeCategorizer.compareItems(a.name, EpisodeParser.parse(a.name), b.name, EpisodeParser.parse(b.name))
                        }

                    groups.add(
                        DomainSeasonGroup(
                            id = "season_$s",
                            name = "Temporada $s",
                            subtitle = "${seasonItems.size} episódios",
                            items = seasonItems
                        )
                    )
                }

                val unassigned = storyItems.filter { it.second.season == null }.map { it.first }
                if (unassigned.isNotEmpty()) {
                    val arcName = unassigned.firstOrNull()?.name?.let { extractArcOrPrequelName(it) } ?: "Prólogo / Extras"
                    groups.add(
                        DomainSeasonGroup(
                            id = "season_prologue",
                            name = arcName,
                            subtitle = "${unassigned.size} episódios",
                            items = unassigned
                        )
                    )
                }
            } else if (distinctStems.size > 1) {
                distinctStems.forEachIndexed { idx, stem ->
                    val stemItems = storyItems
                        .filter { it.first.name.contains(stem, ignoreCase = true) }
                        .map { it.first }
                        .sortedWith { a, b ->
                            EpisodeCategorizer.compareItems(a.name, EpisodeParser.parse(a.name), b.name, EpisodeParser.parse(b.name))
                        }

                    if (stemItems.isNotEmpty()) {
                        groups.add(
                            DomainSeasonGroup(
                                id = "stem_$idx",
                                name = cleanSeasonStemName(stem, idx + 1),
                                subtitle = "${stemItems.size} episódios",
                                items = stemItems
                            )
                        )
                    }
                }
            }

            if (specialItems.isNotEmpty()) {
                val sortedSpecials = specialItems.sortedWith { a, b ->
                    EpisodeCategorizer.compareItems(a.name, EpisodeParser.parse(a.name), b.name, EpisodeParser.parse(b.name))
                }
                groups.add(
                    DomainSeasonGroup(
                        id = "specials_ovas",
                        name = "Especiais & OVAs",
                        subtitle = "${sortedSpecials.size} episódios",
                        items = sortedSpecials
                    )
                )
            }

            if (musicItems.isNotEmpty()) {
                val sortedMusic = musicItems.sortedWith { a, b ->
                    EpisodeCategorizer.compareItems(a.name, EpisodeParser.parse(a.name), b.name, EpisodeParser.parse(b.name))
                }
                groups.add(
                    DomainSeasonGroup(
                        id = "openings_endings",
                        name = "Aberturas & Encerramentos",
                        subtitle = "${sortedMusic.size} faixas",
                        items = sortedMusic
                    )
                )
            }

            if (promoItems.isNotEmpty()) {
                val sortedPromos = promoItems.sortedWith { a, b ->
                    EpisodeCategorizer.compareItems(a.name, EpisodeParser.parse(a.name), b.name, EpisodeParser.parse(b.name))
                }
                groups.add(
                    DomainSeasonGroup(
                        id = "promos_trailers",
                        name = "Trailers & Promos",
                        subtitle = "${sortedPromos.size} vídeos",
                        items = sortedPromos
                    )
                )
            }

            if (groups.isNotEmpty()) {
                val primaryEpisodes = sortedItems.filter { item ->
                    EpisodeCategorizer.categorize(item.name, EpisodeParser.parse(item.name)) == MediaItemCategory.STORY
                }.ifEmpty { sortedItems }
                return addAllEpisodesOption(groups, primaryEpisodes)
            }

            if (videoItems.size > 25) {
                val batchGroups = groupItemsByBatches(sortedItems, batchSize = 25)
                return addAllEpisodesOption(batchGroups, sortedItems)
            }

            return listOf(
                DomainSeasonGroup(
                    id = "season_all",
                    name = "Todos os Episódios",
                    subtitle = "${sortedItems.size} episódios",
                    items = sortedItems
                )
            )
        }

        return listOf(
            DomainSeasonGroup(
                id = "season_all",
                name = "Todos os Episódios",
                subtitle = "${items.size} episódios",
                items = items
            )
        )
    }

    private fun cleanSeasonFolderName(raw: String): String {
        return raw.replace(Regex("(?i)^(\\s*temporada\\s*|\\s*season\\s*)"), "Temporada ").trim()
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

    private fun groupItemsByCanonicalArcs(
        arcs: List<CanonicalArc>,
        items: List<GroupableItem>
    ): List<DomainSeasonGroup> {
        val groups = mutableListOf<DomainSeasonGroup>()
        val storyItems = items.filter { item ->
            EpisodeCategorizer.categorize(item.name, EpisodeParser.parse(item.name)) == MediaItemCategory.STORY
        }
        val matchedItems = mutableSetOf<GroupableItem>()

        for (arc in arcs) {
            val arcItems = storyItems.filter { item ->
                val ep = EpisodeParser.parse(item.name).episode?.toInt()
                ep != null && ep in arc.startEp..arc.endEp
            }.sortedWith { a, b ->
                EpisodeCategorizer.compareItems(a.name, EpisodeParser.parse(a.name), b.name, EpisodeParser.parse(b.name))
            }

            if (arcItems.isNotEmpty()) {
                matchedItems.addAll(arcItems)
                val epRange = "Ep. ${arc.startEp} - ${arc.endEp}"
                groups.add(
                    DomainSeasonGroup(
                        id = "arc_${arc.startEp}_${arc.endEp}",
                        name = arc.name,
                        subtitle = "$epRange • ${arcItems.size} eps",
                        items = arcItems
                    )
                )
            }
        }

        val unmatched = storyItems.filterNot { matchedItems.contains(it) }
        if (unmatched.isNotEmpty()) {
            groups.add(
                DomainSeasonGroup(
                    id = "arc_others",
                    name = "Outros Episódios",
                    subtitle = "${unmatched.size} episódios",
                    items = unmatched.sortedWith { a, b ->
                        EpisodeCategorizer.compareItems(a.name, EpisodeParser.parse(a.name), b.name, EpisodeParser.parse(b.name))
                    }
                )
            )
        }

        val specials = items.filter { item ->
            EpisodeCategorizer.categorize(item.name, EpisodeParser.parse(item.name)) == MediaItemCategory.SPECIAL
        }.sortedWith { a, b ->
            EpisodeCategorizer.compareItems(a.name, EpisodeParser.parse(a.name), b.name, EpisodeParser.parse(b.name))
        }
        if (specials.isNotEmpty()) {
            groups += DomainSeasonGroup("specials_ovas", "Especiais & OVAs", "${specials.size} episódios", items = specials)
        }

        val music = items.filter { item ->
            EpisodeCategorizer.categorize(item.name, EpisodeParser.parse(item.name)) == MediaItemCategory.MUSIC
        }.sortedWith { a, b ->
            EpisodeCategorizer.compareItems(a.name, EpisodeParser.parse(a.name), b.name, EpisodeParser.parse(b.name))
        }
        if (music.isNotEmpty()) {
            groups += DomainSeasonGroup("openings_endings", "Aberturas & Encerramentos", "${music.size} faixas", items = music)
        }

        val promos = items.filter { item ->
            EpisodeCategorizer.categorize(item.name, EpisodeParser.parse(item.name)) == MediaItemCategory.PROMO
        }.sortedWith { a, b ->
            EpisodeCategorizer.compareItems(a.name, EpisodeParser.parse(a.name), b.name, EpisodeParser.parse(b.name))
        }
        if (promos.isNotEmpty()) {
            groups += DomainSeasonGroup("promos_trailers", "Trailers & Promos", "${promos.size} vídeos", items = promos)
        }

        return groups
    }

    private fun groupItemsByBatches(
        items: List<GroupableItem>,
        batchSize: Int
    ): List<DomainSeasonGroup> {
        val groups = mutableListOf<DomainSeasonGroup>()
        val chunks = items.chunked(batchSize)
        chunks.forEachIndexed { index, chunk ->
            val start = index * batchSize + 1
            val end = start + chunk.size - 1
            groups.add(
                DomainSeasonGroup(
                    id = "batch_${start}_$end",
                    name = "Episódios $start - $end",
                    subtitle = "${chunk.size} episódios",
                    items = chunk
                )
            )
        }
        return groups
    }

    private fun addAllEpisodesOption(
        groups: List<DomainSeasonGroup>,
        allEpisodes: List<GroupableItem>
    ): List<DomainSeasonGroup> {
        val allOption = DomainSeasonGroup(
            id = "season_all",
            name = "Todos os Episódios",
            subtitle = "${allEpisodes.size} episódios",
            items = allEpisodes
        )
        return listOf(allOption) + groups
    }
}
