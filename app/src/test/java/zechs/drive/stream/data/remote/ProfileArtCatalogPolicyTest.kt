package zechs.drive.stream.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileArtCatalogPolicyTest {

    @Test
    fun `isValidArtUrl rejects invalid URLs and placeholders`() {
        assertFalse(ProfileArtCatalogPolicy.isValidArtUrl(null))
        assertFalse(ProfileArtCatalogPolicy.isValidArtUrl(""))
        assertFalse(ProfileArtCatalogPolicy.isValidArtUrl("   "))
        assertFalse(ProfileArtCatalogPolicy.isValidArtUrl("ftp://example.com/img.png"))
        // AniList default NO IMAGE
        assertFalse(ProfileArtCatalogPolicy.isValidArtUrl(
            "https://s4.anilist.co/file/anilistcdn/character/large/default.jpg"
        ))
        assertFalse(ProfileArtCatalogPolicy.isValidArtUrl(
            "https://s4.anilist.co/file/anilistcdn/character/large/default.png"
        ))
        // MAL question mark
        assertFalse(ProfileArtCatalogPolicy.isValidArtUrl(
            "https://cdn.myanimelist.net/images/questionmark_50.gif"
        ))
        // Generic placeholders
        assertFalse(ProfileArtCatalogPolicy.isValidArtUrl(
            "https://cdn.example.com/images/no_image.png"
        ))
        assertFalse(ProfileArtCatalogPolicy.isValidArtUrl(
            "https://cdn.example.com/placeholder.jpg"
        ))
        // Broken TVmaze IDs
        assertFalse(ProfileArtCatalogPolicy.isValidArtUrl(
            "https://static.tvmaze.com/uploads/images/medium_portrait/1/3990.jpg"
        ))
        assertFalse(ProfileArtCatalogPolicy.isValidArtUrl(
            "https://static.tvmaze.com/uploads/images/medium_portrait/1/3991.jpg"
        ))

        // Valid URLs
        assertTrue(ProfileArtCatalogPolicy.isValidArtUrl(
            "https://s4.anilist.co/file/anilistcdn/character/large/b22037-sY7GWSKYr2Nl.jpg"
        ))
        assertTrue(ProfileArtCatalogPolicy.isValidArtUrl(
            "https://static.tvmaze.com/uploads/images/medium_portrait/175/439819.jpg"
        ))
    }

    @Test
    fun `resolveFranchise correctly identifies Monogatari Series sub-types`() {
        val bake = ProfileArtCatalogPolicy.resolveFranchise("Bakemonogatari")
        assertEquals("monogatari_series", bake.franchiseKey)
        assertEquals("Monogatari Series", bake.canonicalTitle)
        assertEquals("Bakemonogatari", bake.subType)

        val kizu = ProfileArtCatalogPolicy.resolveFranchise("Kizumonogatari I: Tekketsu-hen")
        assertEquals("monogatari_series", kizu.franchiseKey)
        assertEquals("Monogatari Series", kizu.canonicalTitle)
        assertEquals("Kizumonogatari", kizu.subType)

        val secondSeason = ProfileArtCatalogPolicy.resolveFranchise("Monogatari Series: Second Season")
        assertEquals("monogatari_series", secondSeason.franchiseKey)
        assertEquals("Monogatari Series", secondSeason.canonicalTitle)
    }

    @Test
    fun `resolveFranchise unifies Mushoku Tensei seasons`() {
        val s1 = ProfileArtCatalogPolicy.resolveFranchise("Mushoku Tensei: Isekai Ittara Honki Dasu")
        val s2 = ProfileArtCatalogPolicy.resolveFranchise("Mushoku Tensei Season 2")

        assertEquals("mushoku_tensei", s1.franchiseKey)
        assertEquals("Mushoku Tensei", s1.canonicalTitle)
        assertEquals("mushoku_tensei", s2.franchiseKey)
        assertEquals("Mushoku Tensei", s2.canonicalTitle)
    }

    @Test
    fun `filterAndDeduplicateArts removes duplicates and invalid items`() {
        val arts = listOf(
            ProfileArt("Luffy", "https://cdn.example/luffy.png", false),
            ProfileArt("Luffy Duplicate", "https://cdn.example/luffy.png", false),
            ProfileArt("No Image", "https://s4.anilist.co/file/anilistcdn/character/large/default.jpg", false),
            ProfileArt("Zoro", "https://cdn.example/zoro.png", false)
        )
        val filtered = ProfileArtCatalogPolicy.filterAndDeduplicateArts(arts)
        assertEquals(2, filtered.size)
        assertEquals("https://cdn.example/luffy.png", filtered[0].url)
        assertEquals("https://cdn.example/zoro.png", filtered[1].url)
    }

    @Test
    fun `organizeSections unifies Mushoku Tensei and eliminates duplicate rows`() {
        val raw = listOf(
            Pair("Mushoku Tensei: Isekai Ittara Honki Dasu", listOf(
                ProfileArt("Rudeus", "https://cdn.example/rudeus.png", false)
            )),
            Pair("Mushoku Tensei Season 2", listOf(
                ProfileArt("Rudeus", "https://cdn.example/rudeus.png", false),
                ProfileArt("Sylphiette", "https://cdn.example/sylph.png", false)
            ))
        )
        val organized = ProfileArtCatalogPolicy.organizeSections(raw)
        assertEquals(1, organized.size)
        assertEquals("Mushoku Tensei", organized[0].first)
        assertEquals(2, organized[0].second.size) // Rudeus and Sylphiette
    }

    @Test
    fun `organizeSections groups Monogatari into distinct sub-types`() {
        val raw = listOf(
            Pair("Bakemonogatari", listOf(
                ProfileArt("Hitagi", "https://cdn.example/hitagi.png", false)
            )),
            Pair("Monogatari Series: Second Season", listOf(
                ProfileArt("Hitagi", "https://cdn.example/hitagi.png", false)
            )),
            Pair("Kizumonogatari", listOf(
                ProfileArt("Kiss-Shot", "https://cdn.example/kiss_shot.png", false)
            )),
            Pair("Kizumonogatari I: Tekketsu-hen", listOf(
                ProfileArt("Kiss-Shot", "https://cdn.example/kiss_shot.png", false)
            ))
        )
        val organized = ProfileArtCatalogPolicy.organizeSections(raw)
        // Should produce exactly 2 sections: Bakemonogatari and Kizumonogatari (no redundant duplicate rows)
        assertEquals(2, organized.size)
        val titles = organized.map { it.first }
        assertTrue(titles.contains("Monogatari Series · Bakemonogatari"))
        assertTrue(titles.contains("Monogatari Series · Kizumonogatari"))
    }

    @Test
    fun `organizeSections sorts top relevant series first followed by alphabetical`() {
        val raw = listOf(
            Pair("Zoro The Movie", listOf(ProfileArt("Zoro", "https://cdn.example/z.png", false))),
            Pair("Bleach", listOf(ProfileArt("Ichigo", "https://cdn.example/i.png", false))),
            Pair("Attack on Titan", listOf(ProfileArt("Eren", "https://cdn.example/e.png", false))),
            Pair("Mushoku Tensei", listOf(ProfileArt("Rudeus", "https://cdn.example/r.png", false)))
        )
        // Relevant series: Mushoku Tensei, then Bleach
        val organized = ProfileArtCatalogPolicy.organizeSections(
            rawSections = raw,
            relevantTitles = listOf("Mushoku Tensei", "Bleach"),
            maxRelevant = 2
        )
        assertEquals(4, organized.size)
        // Top 2 must be relevant in priority order
        assertEquals("Mushoku Tensei", organized[0].first)
        assertEquals("Bleach", organized[1].first)
        // Remaining must be alphabetical
        assertEquals("Attack on Titan", organized[2].first)
        assertEquals("Zoro The Movie", organized[3].first)
    }
}
