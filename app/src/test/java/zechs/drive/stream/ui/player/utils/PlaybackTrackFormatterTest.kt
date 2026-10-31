package zechs.drive.stream.ui.player.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTrackFormatterTest {

    @Test
    fun formatsBrazilianSubtitleCorrectly() {
        val result = PlaybackTrackFormatter.formatSubtitle(
            rawLabel = "Brazilian_CR",
            rawLanguage = "pt",
            mimeType = "application/x-subrip",
            isEmbedded = true
        )
        assertEquals("Português (Brasil)", result.primaryTitle)
        assertTrue(result.secondaryDetails.contains("Embutida"))
        assertTrue(result.secondaryDetails.contains("SRT"))
    }

    @Test
    fun formatsJapaneseAudioCorrectly() {
        val result = PlaybackTrackFormatter.formatAudio(
            rawLabel = "[Erai-raws]_AAC_CR",
            rawLanguage = "ja",
            channelCount = 2,
            mimeType = "audio/mp4a-latm"
        )
        assertEquals("Japonês", result.primaryTitle)
        assertTrue(result.secondaryDetails.contains("Áudio Original"))
        assertTrue(result.secondaryDetails.contains("Estéreo 2.0"))
        assertTrue(result.secondaryDetails.contains("AAC"))
    }

    @Test
    fun formatsDubbedAudioCorrectly() {
        val result = PlaybackTrackFormatter.formatAudio(
            rawLabel = "Portuguese (Brazil)",
            rawLanguage = "pt-BR",
            channelCount = 6,
            mimeType = "audio/eac3"
        )
        assertEquals("Português (Brasil)", result.primaryTitle)
        assertTrue(result.secondaryDetails.contains("Dublado"))
        assertTrue(result.secondaryDetails.contains("Surround 5.1"))
        assertTrue(result.secondaryDetails.contains("Dolby Digital Plus (E-AC3)"))
    }
}
