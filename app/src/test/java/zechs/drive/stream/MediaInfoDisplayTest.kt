package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.ui.player.MediaInfoDisplay

class MediaInfoDisplayTest {
    @Test
    fun absentVideoMetadataNeverInventsResolutionOrFrameRate() {
        assertEquals("Resolução desconhecida", MediaInfoDisplay.videoDimensions(null, null))
        assertEquals("Resolução desconhecida", MediaInfoDisplay.videoDimensions(1920, 0))
        assertEquals("FPS desconhecido", MediaInfoDisplay.frameRate(null))
        assertEquals("FPS desconhecido", MediaInfoDisplay.frameRate(Float.NaN))
    }

    @Test
    fun reportedVideoMetadataIsFormattedFromTheInputFormat() {
        assertEquals("AVC (H.264)", MediaInfoDisplay.videoCodec("video/avc"))
        assertEquals("1920x1080", MediaInfoDisplay.videoDimensions(1920, 1080))
        assertEquals("23.976 fps", MediaInfoDisplay.frameRate(23.976f))
    }

    @Test
    fun absentAudioMetadataDoesNotDefaultToStereoOr48Khz() {
        assertEquals(
            "Codec desconhecido • Canais desconhecidos • Frequência desconhecida",
            MediaInfoDisplay.audioSummary(null, null, null)
        )
    }

    @Test
    fun reportedAudioMetadataIsFormattedWithExplicitChannelAndRateValues() {
        assertEquals(
            "Dolby Digital Plus (E-AC-3) • Surround (5.1) • 48.0 kHz",
            MediaInfoDisplay.audioSummary("audio/eac3", 6, 48_000)
        )
    }
}
