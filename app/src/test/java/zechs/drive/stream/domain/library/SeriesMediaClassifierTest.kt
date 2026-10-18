package zechs.drive.stream.domain.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesMediaClassifierTest {

    @Test
    fun mapCategory_mapsAllDomainCategoriesCorrectly() {
        assertEquals(SeriesMediaCategory.EPISODES, SeriesMediaClassifier.mapCategory(MediaItemCategory.STORY))
        assertEquals(SeriesMediaCategory.SPECIALS, SeriesMediaClassifier.mapCategory(MediaItemCategory.SPECIAL))
        assertEquals(SeriesMediaCategory.MUSIC, SeriesMediaClassifier.mapCategory(MediaItemCategory.MUSIC))
        assertEquals(SeriesMediaCategory.PREVIEWS, SeriesMediaClassifier.mapCategory(MediaItemCategory.PROMO))
    }

    @Test
    fun buildChips_excludesZeroCountCategories() {
        val counts = mapOf(
            SeriesMediaCategory.EPISODES to 15,
            SeriesMediaCategory.SPECIALS to 0,
            SeriesMediaCategory.MUSIC to 3,
            SeriesMediaCategory.PREVIEWS to 0
        )

        val chips = SeriesMediaClassifier.buildChips(counts, SeriesMediaCategory.EPISODES)

        assertEquals(2, chips.size)
        assertEquals(SeriesMediaCategory.EPISODES, chips[0].category)
        assertEquals(15, chips[0].count)
        assertTrue(chips[0].isSelected)

        assertEquals(SeriesMediaCategory.MUSIC, chips[1].category)
        assertEquals(3, chips[1].count)
        assertFalse(chips[1].isSelected)
    }

    @Test
    fun buildChips_fallsBackWhenSelectedCategoryHasZeroCount() {
        val counts = mapOf(
            SeriesMediaCategory.EPISODES to 0,
            SeriesMediaCategory.SPECIALS to 2,
            SeriesMediaCategory.MUSIC to 1
        )

        val chips = SeriesMediaClassifier.buildChips(counts, SeriesMediaCategory.EPISODES)

        assertEquals(2, chips.size)
        assertEquals(SeriesMediaCategory.SPECIALS, chips[0].category)
        assertTrue("When EPISODES has 0 count, first available category should be selected", chips[0].isSelected)
    }

    @Test
    fun categorizeFilename_classifiesRealAnimeFilenamesProperly() {
        val regularEp = "[SubDesu] Bakemonogatari - 01 (BD 1080p).mkv"
        val specialEp = "Bakemonogatari - SP01.mkv"
        val musicEp = "[Coalgirls] Bakemonogatari NCOP1.mkv"
        val previewEp = "Bakemonogatari - PV.mkv"

        val parsedRegular = EpisodeParser.parse(regularEp)
        val catRegular = SeriesMediaClassifier.mapCategory(EpisodeCategorizer.categorize(regularEp, parsedRegular))
        assertEquals(SeriesMediaCategory.EPISODES, catRegular)

        val parsedSpecial = EpisodeParser.parse(specialEp)
        val catSpecial = SeriesMediaClassifier.mapCategory(EpisodeCategorizer.categorize(specialEp, parsedSpecial))
        assertEquals(SeriesMediaCategory.SPECIALS, catSpecial)

        val parsedMusic = EpisodeParser.parse(musicEp)
        val catMusic = SeriesMediaClassifier.mapCategory(EpisodeCategorizer.categorize(musicEp, parsedMusic))
        assertEquals(SeriesMediaCategory.MUSIC, catMusic)

        val parsedPreview = EpisodeParser.parse(previewEp)
        val catPreview = SeriesMediaClassifier.mapCategory(EpisodeCategorizer.categorize(previewEp, parsedPreview))
        assertEquals(SeriesMediaCategory.PREVIEWS, catPreview)
    }
}
