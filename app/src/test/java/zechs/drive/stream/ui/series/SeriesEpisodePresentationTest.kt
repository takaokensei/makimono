package zechs.drive.stream.ui.series

import org.junit.Assert.assertEquals
import org.junit.Test

class SeriesEpisodePresentationTest {

    @Test
    fun openingVariantsUseHumanTitlesAndDoNotLookLikeRegularEpisodes() {
        val openingA = presentSeriesEpisode("Bakemonogatari_OP4a_.mkv", canonicalTitle = "Hitagi Crab")
        val openingB = presentSeriesEpisode("Bakemonogatari_OP4b_.mkv", canonicalTitle = "Hitagi Crab")

        assertEquals("Abertura 04A", openingA.displayTitle)
        assertEquals("Abertura 04B", openingB.displayTitle)
        assertEquals(null, openingA.episodeNumber)
        assertEquals(null, openingB.episodeNumber)
    }

    @Test
    fun regularEpisodeKeepsCanonicalTitleAndNumber() {
        val presentation = presentSeriesEpisode(
            "Bakemonogatari - 01 (1080p).mkv",
            canonicalTitle = "Hitagi Crab, Part One"
        )

        assertEquals("Episódio 01 - Hitagi Crab, Part One", presentation.displayTitle)
        assertEquals(1, presentation.episodeNumber)
    }

    @Test
    fun regularEpisodeWithoutCanonicalTitleUsesCleanEpisodeNumber() {
        val presentation = presentSeriesEpisode("Bakemonogatari - 01 (1080p).mkv")
        assertEquals("Episódio 01", presentation.displayTitle)
        assertEquals(1, presentation.episodeNumber)
    }

    @Test
    fun regularEpisodeWithParsedSubtitleUsesEpisodeNumberAndSubtitle() {
        val presentation = presentSeriesEpisode("Bakemonogatari - 02 - Mayoi Snail.mkv")
        assertEquals("Episódio 02 - Mayoi Snail", presentation.displayTitle)
        assertEquals(2, presentation.episodeNumber)
    }

    @Test
    fun formatCountLabelHandlesSingularAndPluralCorrectly() {
        assertEquals("1 Episódio", formatCountLabel(1, isMovie = false))
        assertEquals("12 Episódios", formatCountLabel(12, isMovie = false))
        assertEquals("Filme", formatCountLabel(1, isMovie = true))
        assertEquals("3 Filmes", formatCountLabel(3, isMovie = true))
    }

    @Test
    fun formatRatingHandlesDiverseClassifications() {
        assertEquals("18+", formatRating("18+"))
        assertEquals("18+", formatRating("TV-MA"))
        assertEquals("18+", formatRating("R+ - Mild Nudity"))
        assertEquals("16+", formatRating("R - 17+ (violence & profanity)"))
        assertEquals("16+", formatRating("16"))
        assertEquals("14+", formatRating("PG-13 - Teens 13 or older"))
        assertEquals("14+", formatRating("TV-14"))
        assertEquals("14+", formatRating("A14"))
        assertEquals("10+", formatRating("PG"))
        assertEquals("10+", formatRating("TV-PG"))
        assertEquals("L", formatRating("G - All Ages"))
        assertEquals("L", formatRating("Livre"))
        assertEquals(null, formatRating(null))
        assertEquals(null, formatRating(""))
    }
}
