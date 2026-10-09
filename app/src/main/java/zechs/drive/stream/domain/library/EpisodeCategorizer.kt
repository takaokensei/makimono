package zechs.drive.stream.domain.library

import java.util.Locale

/**
 * Categorias semânticas de itens para ordenação e agrupamento de biblioteca.
 */
enum class MediaItemCategory(val priority: Int) {
    STORY(1),
    SPECIAL(2),
    PROMO(3),
    MUSIC(4)
}

/**
 * Responsável por classificar e comparar arquivos e episódios por categoria de mídia.
 * Lógica pura de domínio sem dependência do Android.
 */
object EpisodeCategorizer {

    fun categorize(title: String, parsed: EpisodeParser.ParsedEpisode): MediaItemCategory {
        val badgeUpper = parsed.episodeBadge.uppercase(Locale.ROOT)
        val labelUpper = parsed.episodeLabel.uppercase(Locale.ROOT)
        val titleUpper = title.uppercase(Locale.ROOT)

        // Music: OP, ED, NCOP, NCED, OST
        if (badgeUpper in listOf("OP", "ED", "NCOP", "NCED", "OST") ||
            labelUpper.contains("ABERTURA") || labelUpper.contains("ENCERRAMENTO") ||
            titleUpper.contains("NCOP") || titleUpper.contains("NCED") ||
            titleUpper.contains("OPENING") || titleUpper.contains("ENDING") ||
            titleUpper.contains("THEME SONG")) {
            return MediaItemCategory.MUSIC
        }

        // Promos: PV, CM, Teaser, Preview
        if (badgeUpper in listOf("PV", "CM", "TEASER", "PREVIEW") ||
            labelUpper.contains("PREVIEW") || labelUpper.contains("TEASER") || labelUpper.contains("COMERCIAL") ||
            titleUpper.contains("TRAILER") || titleUpper.contains("PROMO")) {
            return MediaItemCategory.PROMO
        }

        // Specials: SP, OVA, OAD, Special
        if (parsed.isSpecial || badgeUpper in listOf("SP", "OVA", "OAD", "SPECIAL") ||
            labelUpper.contains("ESPECIAL") || labelUpper.contains("SPECIAL") || labelUpper.contains("OVA") || labelUpper.contains("OAD") ||
            titleUpper.contains("SPECIAL") || titleUpper.contains("OVA") || titleUpper.contains("OAD")) {
            return MediaItemCategory.SPECIAL
        }

        return MediaItemCategory.STORY
    }

    /**
     * Comparador de ordenação de episódios e mídias:
     * 1. Episódios canônicos / história (Temporada, Episódio 1..N)
     * 2. Especiais & OVAs (Temporada, Especial 1..N)
     * 3. Promos / Teasers / Trailers
     * 4. Músicas / Aberturas / Encerramentos
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
            MediaItemCategory.STORY -> {
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
            MediaItemCategory.SPECIAL -> {
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
            MediaItemCategory.MUSIC -> {
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
            MediaItemCategory.PROMO -> {
                val numA = parsedA.episode ?: 0.0
                val numB = parsedB.episode ?: 0.0
                if (numA != numB) numA.compareTo(numB)
                else EpisodeParser.naturalCompare(titleA, titleB)
            }
        }
    }
}
