package zechs.drive.stream.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeMetadataResolverTest {

    private val resolver = EpisodeMetadataResolver()

    @Test
    fun `buildSearchQueries expands anime queries with canonical English names`() {
        val bleachQueries = resolver.buildSearchQueries("Bleach: Sennen Kessen-hen - Soukoku-tan")
        assertTrue(bleachQueries.contains("Bleach: Thousand-Year Blood War"))
        assertTrue(bleachQueries.contains("Bleach"))

        val monogatariQueries = resolver.buildSearchQueries("Bakemonogatari")
        assertTrue(monogatariQueries.contains("Monogatari Series"))
        assertTrue(monogatariQueries.contains("Bakemonogatari"))

        val slimeQueries = resolver.buildSearchQueries("Tensei Shitara Slime Datta Ken")
        assertTrue(slimeQueries.contains("That Time I Got Reincarnated as a Slime"))

        val mentalistQueries = resolver.buildSearchQueries("O Mentalista")
        assertTrue(mentalistQueries.contains("The Mentalist"))
    }

    @Test
    fun `findEpisode matches by season and episode number`() {
        val episodes = listOf(
            EpisodeDetails(season = 1, episodeNumber = 1, name = "The Blood Warfare", synopsis = "Peace broken", previewImageUrl = "https://img/s1e1.jpg"),
            EpisodeDetails(season = 3, episodeNumber = 1, name = "A", synopsis = "Squad Zero battle", previewImageUrl = "https://img/s3e1.jpg"),
            EpisodeDetails(season = 3, episodeNumber = 2, name = "Kill the King", synopsis = "Ichigo goes to palace", previewImageUrl = "https://img/s3e2.jpg")
        )

        val s3e2 = resolver.findEpisode(episodes, season = 3, episodeNumber = 2)
        assertNotNull(s3e2)
        assertEquals("Kill the King", s3e2?.name)
        assertEquals("https://img/s3e2.jpg", s3e2?.previewImageUrl)
        assertEquals("Ichigo goes to palace", s3e2?.synopsis)
    }

    @Test
    fun `findEpisode matches by canonical title if season is null`() {
        val episodes = listOf(
            EpisodeDetails(season = 1, episodeNumber = 1, name = "Hitagi Crab, Part 1", synopsis = "Crab oddity", previewImageUrl = "https://img/crab.jpg"),
            EpisodeDetails(season = 1, episodeNumber = 2, name = "Mayoi Snail, Part 1", synopsis = "Lost cow", previewImageUrl = "https://img/snail.jpg")
        )

        val matched = resolver.findEpisode(episodes, season = null, episodeNumber = null, canonicalName = "Hitagi Crab, Part 1")
        assertNotNull(matched)
        assertEquals("Hitagi Crab, Part 1", matched?.name)
        assertEquals("https://img/crab.jpg", matched?.previewImageUrl)
    }

    @Test
    fun `findEpisode returns null when no matching episode exists`() {
        val episodes = listOf(
            EpisodeDetails(season = 1, episodeNumber = 1, name = "Pilot", synopsis = "Jane", previewImageUrl = null)
        )
        assertNull(resolver.findEpisode(episodes, season = 1, episodeNumber = 99))
    }
}
