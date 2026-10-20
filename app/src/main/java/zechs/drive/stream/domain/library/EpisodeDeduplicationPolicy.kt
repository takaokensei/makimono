package zechs.drive.stream.domain.library

import java.text.Normalizer
import java.util.Locale

/** Conservative duplicate detection: only collapse files when Drive identity or media metadata agrees. */
object EpisodeDeduplicationPolicy {
    data class Candidate(
        val id: String,
        val title: String,
        val sizeBytes: Long?,
        val durationMillis: Long?,
        val checksum: String?
    )

    fun removeDuplicates(items: List<Candidate>): List<Candidate> {
        val retained = mutableListOf<Candidate>()
        val checksums = mutableSetOf<String>()
        for (candidate in items) {
            val checksum = candidate.checksum?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotEmpty)
            if (checksum != null) {
                if (!checksums.add(checksum)) continue
            } else if (retained.any { existing -> isStrongMetadataMatch(existing, candidate) }) {
                continue
            }
            retained += candidate
        }
        return retained
    }

    private fun isStrongMetadataMatch(left: Candidate, right: Candidate): Boolean {
        if (left.id == right.id) return true
        val leftTitle = normalizeTitle(left.title)
        val rightTitle = normalizeTitle(right.title)
        if (leftTitle.isBlank() || leftTitle != rightTitle) return false
        val leftSize = left.sizeBytes ?: return false
        val rightSize = right.sizeBytes ?: return false
        val leftDuration = left.durationMillis ?: return false
        val rightDuration = right.durationMillis ?: return false
        val maxSize = maxOf(leftSize, rightSize).coerceAtLeast(1L)
        val sizeDelta = kotlin.math.abs(leftSize - rightSize).toDouble() / maxSize
        return sizeDelta <= 0.01 && kotlin.math.abs(leftDuration - rightDuration) <= 1_000L
    }

    private fun normalizeTitle(raw: String): String {
        val withoutExtension = raw.replace(Regex("(?i)\\.(mkv|mp4|m4v|webm|avi|mov|mpg|mpeg|ts|m2ts)$"), "")
        val withoutReleaseTags = withoutExtension
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("(?i)\\b(2160p|1080p|720p|480p|4k|uhd|hevc|avc|x264|x265|h264|h265|bluray|web[- .]?dl|webrip|dual audio|multi audio)\\b"), " ")
        val normalized = Normalizer.normalize(withoutReleaseTags, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
        return normalized
    }
}
