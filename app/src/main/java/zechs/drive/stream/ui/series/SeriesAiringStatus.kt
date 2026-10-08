package zechs.drive.stream.ui.series

import java.util.Locale

/** Known publication states returned by MAL/AniList. Unknown API values stay undisplayed. */
internal enum class SeriesAiringStatus {
    COMPLETED,
    AIRING,
    UPCOMING,
    HIATUS,
    CANCELLED
}

internal object SeriesAiringStatusPolicy {
    fun classify(rawStatus: String?): SeriesAiringStatus? {
        val normalized = rawStatus
            ?.trim()
            ?.uppercase(Locale.ROOT)
            ?.replace(Regex("[\\s-]+"), "_")
            ?.takeIf(String::isNotEmpty)
            ?: return null

        return when (normalized) {
            "FINISHED", "FINISHED_AIRING" -> SeriesAiringStatus.COMPLETED
            "RELEASING", "CURRENTLY_AIRING" -> SeriesAiringStatus.AIRING
            "NOT_YET_RELEASED", "NOT_YET_AIRED" -> SeriesAiringStatus.UPCOMING
            "HIATUS" -> SeriesAiringStatus.HIATUS
            "CANCELLED", "CANCELED" -> SeriesAiringStatus.CANCELLED
            else -> null
        }
    }
}
