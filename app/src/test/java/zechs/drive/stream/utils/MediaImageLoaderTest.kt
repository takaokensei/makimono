package zechs.drive.stream.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaImageLoaderTest {

    @Test
    fun `selectNeighborPreloadUrls returns up to radius neighbors excluding focused item`() {
        val urls = listOf(
            "https://img/0.jpg",
            "https://img/1.jpg",
            "https://img/2.jpg",
            "https://img/3.jpg",
            "https://img/4.jpg"
        )

        val selected = MediaImageLoader.selectNeighborPreloadUrls(
            urls = urls,
            focusedIndex = 2,
            radius = 2
        )

        assertEquals(
            listOf(
                "https://img/0.jpg",
                "https://img/1.jpg",
                "https://img/3.jpg",
                "https://img/4.jpg"
            ),
            selected
        )
    }

    @Test
    fun `selectNeighborPreloadUrls clamps bounds and filters blank or duplicate urls`() {
        val urls = listOf(
            "https://img/0.jpg",
            "  ",
            "https://img/2.jpg",
            "https://img/2.jpg"
        )

        val selected = MediaImageLoader.selectNeighborPreloadUrls(
            urls = urls,
            focusedIndex = 0,
            radius = 3
        )

        assertEquals(listOf("https://img/2.jpg"), selected)
        assertEquals(640, MediaImageLoader.CARD_WIDTH)
        assertEquals(400, MediaImageLoader.CARD_HEIGHT)
    }

    @Test
    fun `baseline-prof txt is versioned and covers startup Home Detail and Player hot paths`() {
        val candidates = listOf(
            File("src/main/baseline-prof.txt"),
            File("app/src/main/baseline-prof.txt")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: throw AssertionError("baseline-prof.txt not found")
        val content = file.readText()
        assertTrue(content.contains("Lzechs/drive/stream/ThisApp;"))
        assertTrue(content.contains("Lzechs/drive/stream/ui/main/MainActivity;"))
        assertTrue(content.contains("Lzechs/drive/stream/ui/home/HomeFragment;"))
        assertTrue(content.contains("Lzechs/drive/stream/ui/series/SeriesDetailFragment;"))
        assertTrue(content.contains("Lzechs/drive/stream/ui/player/BasePlayerActivity;"))
    }
}
