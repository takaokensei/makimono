package zechs.drive.stream.ui.player2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvTechnicalInfoDisplayTest {
    @Test
    fun missingPropertiesStayUnavailableInsteadOfShowingAssumedHardwareDetails() {
        val rows = MpvTechnicalInfoDisplay.rows(
            videoCodec = null,
            width = null,
            height = 0,
            containerFps = Double.NaN,
            activeDecoder = null,
            audioCodec = null,
            channels = null,
            sampleRateHz = null,
            subtitleTrack = null,
            cacheDurationSeconds = null
        ).associateBy { it.id }

        assertEquals("Indisponível", rows.getValue("video").value)
        assertEquals("Indisponível", rows.getValue("resolution").value)
        assertEquals("Indisponível", rows.getValue("fps").value)
        assertEquals("Indisponível", rows.getValue("decoder").value)
        assertEquals("Indisponível", rows.getValue("subtitle").value)
        assertEquals("Indisponível", rows.getValue("buffer").value)
        assertTrue(rows.getValue("audio").value.contains("Indisponível"))
    }

    @Test
    fun reportedPropertiesAreFormattedWithDecoderAndCacheSemantics() {
        val rows = MpvTechnicalInfoDisplay.rows(
            videoCodec = "hevc",
            width = 1920,
            height = 1080,
            containerFps = 23.976,
            activeDecoder = "mediacodec",
            audioCodec = "eac3",
            channels = "5.1",
            sampleRateHz = 48_000,
            subtitleTrack = "Português (BR)",
            cacheDurationSeconds = 4.25
        ).associateBy { it.id }

        assertEquals("1920×1080", rows.getValue("resolution").value)
        assertEquals("23.976 fps", rows.getValue("fps").value)
        assertEquals("mediacodec", rows.getValue("decoder").value)
        assertEquals("Português (BR)", rows.getValue("subtitle").value)
        assertEquals("4.3 s (estimativa)", rows.getValue("buffer").value)
        assertTrue(rows.getValue("audio").value.contains("48.0 kHz"))
    }

    @Test
    fun softwareDecoderIsIdentifiedTruthfully() {
        val row = MpvTechnicalInfoDisplay.rows(
            videoCodec = "h264",
            width = 1280,
            height = 720,
            containerFps = 24.0,
            activeDecoder = "no",
            audioCodec = "aac",
            channels = "stereo",
            sampleRateHz = 44_100,
            subtitleTrack = "Desativadas",
            cacheDurationSeconds = -1.0
        ).first { it.id == "decoder" }

        assertEquals("Software", row.value)
    }

    @Test
    fun subtitleStatusSeparatesDisabledAutomaticSelectedAndUnreportedStates() {
        assertEquals("Desativadas", MpvTechnicalInfoDisplay.subtitleSelection("no", null))
        assertEquals("Automática", MpvTechnicalInfoDisplay.subtitleSelection("auto", null))
        assertEquals(
            "Português (BR)",
            MpvTechnicalInfoDisplay.subtitleSelection("3", " Português (BR) ")
        )
        assertEquals("Faixa 3", MpvTechnicalInfoDisplay.subtitleSelection("3", null))
        assertEquals("Indisponível", MpvTechnicalInfoDisplay.subtitleSelection(null, null))
        assertEquals("Indisponível", MpvTechnicalInfoDisplay.subtitleSelection("invalid", null))
    }
}
