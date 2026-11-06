package zechs.drive.stream.ui.player

import android.content.Context
import androidx.annotation.StringRes
import zechs.drive.stream.R

object PlaybackLabel {
    @StringRes
    fun subtitleRes(isMovie: Boolean, episode: Int?): Int {
        return if (isMovie || episode == null || episode <= 0) {
            R.string.playback_movie_full
        } else {
            R.string.playback_episode_number
        }
    }

    fun renderSubtitle(context: Context, isMovie: Boolean, episode: Int?): String {
        return if (isMovie || episode == null || episode <= 0) {
            context.getString(R.string.playback_movie_full)
        } else {
            context.getString(R.string.playback_episode_number, episode)
        }
    }

    fun format(isMovie: Boolean, episode: Int?): String {
        return if (isMovie || episode == null || episode <= 0) {
            "Filme Completo"
        } else {
            "Episódio %02d".format(episode)
        }
    }

    /**
     * Sanitizes raw episode titles from torrent group tags, resolutions, extensions,
     * and duplicate/mojibake 'Episódio XX' occurrences.
     * Returns Pair(cleanDistinctTitle, hasDistinctTitle).
     */
    fun sanitizeEpisodeTitle(rawTitle: String, episodeNumber: Int, isMovie: Boolean): Pair<String, Boolean> {
        if (isMovie || rawTitle.isBlank()) return Pair("", false)
        val epPrefix2Digit = if (episodeNumber > 0) "Episódio %02d".format(episodeNumber) else ""
        val epPrefix1Digit = if (episodeNumber > 0) "Episódio $episodeNumber" else ""
        val clean = rawTitle
            .replace("Epis?dio", "Episódio")
            .replace("epis?dio", "episódio")
            .replace("Episodio", "Episódio")
            .replace("episodio", "episódio")
            .replace("\uFFFD", "ó")
            .replace("?", "ó")
            .replace(Regex("\\[.*?\\]"), "")
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("(?i)\\.(ts|mkv|mp4|avi)$"), "")
            .replace(Regex("(?i)\\b(1080p|720p|4k|2160p|hevc|x264|x265|aac|ac3|eac3|mpeg2)\\b"), "")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', '•', '_')

        val isRedundant = clean.isBlank() ||
            clean.equals(epPrefix2Digit, ignoreCase = true) ||
            clean.equals(epPrefix1Digit, ignoreCase = true) ||
            clean.matches(Regex("(?i)^(epis[oó]dio|ep|episode)\\s*0*\\d+$")) ||
            clean.matches(Regex("^\\d+$"))

        if (isRedundant) {
            return Pair("", false)
        }

        val trimmed = clean
            .replace(Regex("(?i)^(epis[oó]dio|ep|episode)\\s*0*\\d+\\s*[-•:]*\\s*"), "")
            .replace(Regex("(?i)[-•:]*\\s*(epis[oó]dio|ep|episode)?\\s*0*$episodeNumber\\s*$"), "")
            .replace(Regex("(?i)[-•:]*\\s*(epis[oó]dio|ep|episode)?\\s*0*\\d+\\s*$"), "")
            .trim(' ', '-', '•', '_')

        if (trimmed.isBlank() || trimmed.equals(epPrefix2Digit, ignoreCase = true) || trimmed.equals(epPrefix1Digit, ignoreCase = true)) {
            return Pair("", false)
        }
        return Pair(trimmed, true)
    }
}
