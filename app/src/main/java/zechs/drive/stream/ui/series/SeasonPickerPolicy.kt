package zechs.drive.stream.ui.series

/** Presentation only: selection still belongs to SeriesDetailViewModel. */
internal object SeasonPickerPolicy {
    data class Presentation(
        val compact: Boolean,
        val selectedIndex: Int,
        val visibleIndices: List<Int>
    )

    fun present(seasonIds: List<String>, selectedId: String?): Presentation {
        if (seasonIds.isEmpty()) return Presentation(false, -1, emptyList())
        val selectedIndex = seasonIds.indexOf(selectedId).takeIf { it >= 0 } ?: 0
        val compact = seasonIds.size > 4
        return Presentation(
            compact,
            selectedIndex,
            if (compact) listOf(selectedIndex) else seasonIds.indices.toList()
        )
    }
}
