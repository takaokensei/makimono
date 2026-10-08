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
}
