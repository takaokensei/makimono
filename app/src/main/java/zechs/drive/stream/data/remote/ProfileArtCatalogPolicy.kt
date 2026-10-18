package zechs.drive.stream.data.remote

import java.util.Locale

/**
 * Pure, testable policy for filtering, franchise unification, and relevance ordering
 * of profile avatars and wallpapers.
 */
object ProfileArtCatalogPolicy {

    /**
     * Determines whether an avatar/wallpaper URL is valid, publicly accessible,
     * and not a placeholder (e.g. AniList "default.jpg" NO IMAGE or MAL question mark).
     */
    fun isValidArtUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            return false
        }
        val lower = trimmed.lowercase(Locale.ROOT)

        // Filter AniList / MAL / CDN placeholder images
        if (lower.contains("default.jpg") || lower.contains("default.png")) return false
        if (lower.contains("questionmark") || lower.contains("no_image") || lower.contains("noimage")) return false
        if (lower.contains("placeholder") || lower.contains("assets/no-image")) return false

        // Filter known broken/defunct TVmaze 404 image IDs
        if (lower.contains("static.tvmaze.com/uploads/images/medium_portrait/1/3990.jpg") ||
            lower.contains("static.tvmaze.com/uploads/images/medium_portrait/1/3991.jpg") ||
            lower.contains("static.tvmaze.com/uploads/images/medium_portrait/1/3992.jpg") ||
            lower.contains("static.tvmaze.com/uploads/images/medium_portrait/1/3993.jpg")
        ) {
            return false
        }

        return true
    }

    data class FranchiseDescriptor(
        val franchiseKey: String,
        val canonicalTitle: String,
        val subType: String? = null
    )

    /**
     * Identifies franchise and optional sub-type for a given title or folder name.
     */
    fun resolveFranchise(rawTitle: String): FranchiseDescriptor {
        val clean = AnimePosterResolver.cleanAnimeTitle(rawTitle).trim()
        val norm = clean.lowercase(Locale.ROOT)

        return when {
            // Monogatari Series: unifies all seasons, movies and arcs into one franchise
            norm.contains("monogatari") || norm.contains("kizumonogatari") || norm.contains("bakemonogatari") ||
                norm.contains("nisemonogatari") || norm.contains("nekomonogatari") ||
                norm.contains("koyomimonogatari") || norm.contains("owarimonogatari") ||
                norm.contains("tsukimonogatari") || norm.contains("zoku") -> {
                FranchiseDescriptor("monogatari_series", "Monogatari Series", null)
            }

            // Owari no Seraph
            norm.contains("owari no seraph") || norm.contains("seraph of the end") -> {
                FranchiseDescriptor("owari_no_seraph", "Owari no Seraph", null)
            }

            // Mushoku Tensei: unifies all seasons and cours
            norm.contains("mushoku") || norm.contains("jobless reincarnation") -> {
                FranchiseDescriptor("mushoku_tensei", "Mushoku Tensei", null)
            }

            // Bleach
            norm.contains("bleach") -> {
                FranchiseDescriptor("bleach", "BLEACH", null)
            }

            // The Mentalist
            norm.contains("mentalist") || norm.contains("mentalista") -> {
                FranchiseDescriptor("the_mentalist", "The Mentalist", null)
            }

            // Tsuihou Sareta Tensei Juukishi
            norm.contains("tsuihou sareta") || norm.contains("tensei juukishi") -> {
                FranchiseDescriptor("tsuihou_sareta", "Tsuihou Sareta Tensei Juukishi", null)
            }

            // Spider-Man
            norm.contains("spider-man") || norm.contains("homem-aranha") || norm.contains("homem aranha") -> {
                FranchiseDescriptor("spider_man", "Spider-Man", null)
            }

            // Slime
            norm.contains("tensei shitara") || norm.contains("slime datta ken") -> {
                FranchiseDescriptor("tensei_shitara_slime", "Tensei Shitara Slime Datta Ken", null)
            }

            // Jigokuraku
            norm.contains("jigokuraku") || norm.contains("hell's paradise") -> {
                FranchiseDescriptor("jigokuraku", "Jigokuraku", null)
            }

            // Grisaia
            norm.contains("grisaia") -> {
                FranchiseDescriptor("grisaia", "Grisaia Series", null)
            }

            // Durarara
            norm.contains("durarara") -> {
                FranchiseDescriptor("durarara", "Durarara!!", null)
            }

            // Shangri-La Frontier
            norm.contains("shangri-la frontier") || norm.contains("shangri la frontier") -> {
                FranchiseDescriptor("shangri_la_frontier", "Shangri-La Frontier", null)
            }

            // Default: standalone work
            else -> {
                val key = norm.replace(Regex("[^a-z0-9]"), "_")
                FranchiseDescriptor(key, clean, null)
            }
        }
    }

    /**
     * Builds standard section title according to franchise and sub-type.
     * E.g. "Monogatari Series · Bakemonogatari", "Mushoku Tensei", "The Mentalist"
     */
    fun formatSectionTitle(descriptor: FranchiseDescriptor): String {
        return if (!descriptor.subType.isNullOrBlank()) {
            "${descriptor.canonicalTitle} · ${descriptor.subType}"
        } else {
            descriptor.canonicalTitle
        }
    }

    /**
     * Filters invalid art items and deduplicates them by URL.
     */
    fun filterAndDeduplicateArts(arts: List<ProfileArt>): List<ProfileArt> {
        val seen = mutableSetOf<String>()
        return arts.filter { art ->
            isValidArtUrl(art.url) && seen.add(art.url.trim().lowercase(Locale.ROOT))
        }
    }

    /**
     * Groups raw sections into unified franchises and orders them:
     * 1. Top: Up to [maxRelevant] relevant series (starred or recently accessed), in order of relevance.
     * 2. Remainder: Alphabetical order (A -> Z).
     */
    fun organizeSections(
        rawSections: List<Pair<String, List<ProfileArt>>>,
        relevantTitles: List<String> = emptyList(),
        maxRelevant: Int = 3
    ): List<Pair<String, List<ProfileArt>>> {
        if (rawSections.isEmpty()) return emptyList()

        // 1. Group arts by formatted section title (merges duplicate folders of the same franchise/sub-type)
        val groupedSections = linkedMapOf<String, MutableList<ProfileArt>>()

        for ((title, arts) in rawSections) {
            val validArts = filterAndDeduplicateArts(arts)
            if (validArts.isEmpty()) continue

            val descriptor = resolveFranchise(title)
            val sectionKey = formatSectionTitle(descriptor)

            val existing = groupedSections.getOrPut(sectionKey) { mutableListOf() }
            val existingUrls = existing.map { it.url.trim().lowercase(Locale.ROOT) }.toMutableSet()

            for (art in validArts) {
                if (existingUrls.add(art.url.trim().lowercase(Locale.ROOT))) {
                    existing.add(art)
                }
            }
        }

        val allUnified = groupedSections.map { (title, arts) -> Pair(title, arts.toList()) }
        if (allUnified.isEmpty()) return emptyList()

        // 2. Identify top relevant sections (matched against relevantTitles normalized)
        val normalizedRelevant = relevantTitles.map { resolveFranchise(it).canonicalTitle.lowercase(Locale.ROOT) }

        val relevantPicks = mutableListOf<Pair<String, List<ProfileArt>>>()
        val remainingPicks = mutableListOf<Pair<String, List<ProfileArt>>>()

        for (section in allUnified) {
            val descriptor = resolveFranchise(section.first)
            val canon = descriptor.canonicalTitle.lowercase(Locale.ROOT)
            val relIndex = normalizedRelevant.indexOf(canon)
            if (relIndex != -1) {
                relevantPicks.add(section)
            } else {
                remainingPicks.add(section)
            }
        }

        // Sort relevant picks according to their order in relevantTitles
        val sortedRelevant = relevantPicks.sortedBy { section ->
            val canon = resolveFranchise(section.first).canonicalTitle.lowercase(Locale.ROOT)
            val idx = normalizedRelevant.indexOf(canon)
            if (idx == -1) Int.MAX_VALUE else idx
        }.take(maxRelevant)

        // Sections that were relevant beyond maxRelevant go back to remaining
        val overflowRelevant = relevantPicks.filterNot { sortedRelevant.contains(it) }
        val allRemaining = (remainingPicks + overflowRelevant).sortedBy { it.first.lowercase(Locale.ROOT) }

        return sortedRelevant + allRemaining
    }
}
