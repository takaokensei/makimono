package zechs.drive.stream.domain.library

/**
 * Categorias exibidas como chips na coluna de arcos (abaixo do arco ativo).
 */
enum class SeriesMediaCategory {
    EPISODES,
    SPECIALS,
    MUSIC,
    PREVIEWS
}

data class CategoryChip(
    val category: SeriesMediaCategory,
    val count: Int,
    val isSelected: Boolean = false
)

object SeriesMediaClassifier {

    fun mapCategory(domainCat: MediaItemCategory): SeriesMediaCategory = when (domainCat) {
        MediaItemCategory.STORY -> SeriesMediaCategory.EPISODES
        MediaItemCategory.SPECIAL -> SeriesMediaCategory.SPECIALS
        MediaItemCategory.MUSIC -> SeriesMediaCategory.MUSIC
        MediaItemCategory.PROMO -> SeriesMediaCategory.PREVIEWS
    }

    fun buildChips(
        categoryCounts: Map<SeriesMediaCategory, Int>,
        selectedCategory: SeriesMediaCategory
    ): List<CategoryChip> {
        val order = listOf(
            SeriesMediaCategory.EPISODES,
            SeriesMediaCategory.SPECIALS,
            SeriesMediaCategory.MUSIC,
            SeriesMediaCategory.PREVIEWS
        )

        // Ensure selectedCategory is valid; if selectedCategory has 0 count, pick first non-empty
        val effectiveSelected = if ((categoryCounts[selectedCategory] ?: 0) > 0) {
            selectedCategory
        } else {
            order.firstOrNull { (categoryCounts[it] ?: 0) > 0 } ?: selectedCategory
        }

        return order.mapNotNull { cat ->
            val count = categoryCounts[cat] ?: 0
            if (count > 0) {
                CategoryChip(
                    category = cat,
                    count = count,
                    isSelected = (cat == effectiveSelected)
                )
            } else null
        }
    }
}
