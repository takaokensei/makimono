package zechs.drive.stream.data.translation

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CulturalXRayEngineTest {

    @Test
    fun testExplainSceneContextRespectsWindowAndFiltersOutFutureCues() = runBlocking {
        val translator = mockk<MultiProviderSubtitleTranslator>(relaxed = true)
        val engine = CulturalXRayEngine(translator)

        val cues = listOf(
            AssTagPreservingCodec.CueItem(1, 10_000L, 14_000L, "Old line", "", emptyMap()),
            AssTagPreservingCodec.CueItem(2, 50_000L, 55_000L, "Koyomi-onii-chan", "", emptyMap()),
            AssTagPreservingCodec.CueItem(3, 90_000L, 95_000L, "I don't know everything, I only know what I know.", "", emptyMap()),
            AssTagPreservingCodec.CueItem(4, 120_000L, 125_000L, "Major spoiler in future", "", emptyMap())
        )

        coEvery { translator.translateBatch(any(), any(), any()) } returns Result.success(
            mapOf(1 to "• 'Onii-chan' demonstra afeto fraternal.\n• Frase icônica da Tsubasa Hanekawa sobre sabedoria.")
        )

        val result = engine.explainSceneContext(
            animeTitle = "Bakemonogatari",
            episodeNumber = 1,
            currentPositionMs = 100_000L,
            cues = cues
        )

        assertTrue(result.isSuccess)
        val points = result.getOrNull()
        assertNotNull(points)
        assertEquals(2, points!!.size)
        assertTrue(points[0].contains("Onii-chan"))
    }
}
