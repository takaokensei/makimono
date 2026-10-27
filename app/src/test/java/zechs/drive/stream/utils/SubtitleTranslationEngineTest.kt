package zechs.drive.stream.utils

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import zechs.drive.stream.data.translation.MultiProviderSubtitleTranslator
import java.io.File

class SubtitleTranslationEngineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private val translator = mockk<MultiProviderSubtitleTranslator>(relaxed = true)
    private lateinit var engine: SubtitleTranslationEngine

    @Before
    fun setup() {
        every { context.cacheDir } returns tempFolder.root
        engine = SubtitleTranslationEngine(context, translator)
    }

    @Test
    fun testTranslateFileProgressiveCreatesInitialChunkAndCompletes() = runBlocking {
        val srtFile = tempFolder.newFile("test_episode.srt")
        srtFile.writeText(
            """
                1
                00:00:05,000 --> 00:00:08,000
                Good evening.
                
                2
                00:00:10,000 --> 00:00:13,000
                The moon is clear tonight.
            """.trimIndent()
        )

        coEvery { translator.translateBatch(any(), any(), any()) } returns Result.success(
            mapOf(
                1 to "Boa noite.",
                2 to "A lua está clara esta noite."
            )
        )

        val result = engine.translateFileProgressive(
            sourceFile = srtFile,
            cacheKey = "test_ep_01",
            currentPositionMs = 5000L
        )

        assertTrue(result.isSuccess)
        val outFile = result.getOrNull()
        assertNotNull(outFile)
        assertTrue(outFile!!.exists())
        val content = outFile.readText()
        assertTrue(content.contains("Boa noite."))
    }
}
