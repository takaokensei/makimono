package zechs.drive.stream.playback.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineSelectorTest {

    private val defaultDevice = DeviceCaps(
        isTv = true,
        isLowRamDevice = false,
        supportedVideoCodecs = setOf("h264", "hevc", "av1", "vp9"),
        supportedAudioCodecs = setOf("aac", "mp3", "ac3", "eac3", "opus"),
        maxSupportedAudioChannels = 6,
        supportsHardwareAss = false
    )

    private val standardMedia = MediaInfo(
        name = "Episode 01.mkv",
        extension = "mkv",
        videoCodec = "hevc",
        audioCodec = "aac",
        audioChannels = 2,
        hasAssSubtitles = false,
        hasSrtSubtitles = true
    )

    @Test
    fun `default standard media routes to Media3`() {
        val choice = EngineSelector.selectEngine(standardMedia, defaultDevice)
        assertTrue(choice is EngineChoice.Media3)
        assertEquals("Default primary hardware engine (Media3)", choice.reason)
    }

    @Test
    fun `media with ASS subtitles routes to MPV when hardware ASS not supported`() {
        val animeMedia = standardMedia.copy(hasAssSubtitles = true)
        val choice = EngineSelector.selectEngine(animeMedia, defaultDevice)
        assertTrue(choice is EngineChoice.Mpv)
        assertTrue(choice.reason.contains("ASS/SSA"))
    }

    @Test
    fun `media with ASS subtitles routes to Media3 if device supports hardware ASS`() {
        val deviceWithAss = defaultDevice.copy(supportsHardwareAss = true)
        val animeMedia = standardMedia.copy(hasAssSubtitles = true)
        val choice = EngineSelector.selectEngine(animeMedia, deviceWithAss)
        assertTrue(choice is EngineChoice.Media3)
    }

    @Test
    fun `user preference FORCE_MEDIA3 overrides ASS subtitle check`() {
        val animeMedia = standardMedia.copy(hasAssSubtitles = true)
        val choice = EngineSelector.selectEngine(animeMedia, defaultDevice, UserEnginePreference.FORCE_MEDIA3)
        assertTrue(choice is EngineChoice.Media3)
        assertEquals("Forced by user preference", choice.reason)
    }

    @Test
    fun `user preference FORCE_MPV overrides standard media`() {
        val choice = EngineSelector.selectEngine(standardMedia, defaultDevice, UserEnginePreference.FORCE_MPV)
        assertTrue(choice is EngineChoice.Mpv)
        assertEquals("Forced by user preference", choice.reason)
    }

    @Test
    fun `unsupported audio codec routes to MPV`() {
        val exoticAudio = standardMedia.copy(audioCodec = "truehd")
        val choice = EngineSelector.selectEngine(exoticAudio, defaultDevice)
        assertTrue(choice is EngineChoice.Mpv)
        assertTrue(choice.reason.contains("truehd"))
    }

    @Test
    fun `unsupported video codec routes to MPV`() {
        val exoticVideo = standardMedia.copy(videoCodec = "vc1")
        val choice = EngineSelector.selectEngine(exoticVideo, defaultDevice)
        assertTrue(choice is EngineChoice.Mpv)
        assertTrue(choice.reason.contains("vc1"))
    }

    @Test
    fun `audio channels exceeding device limit routes to MPV`() {
        val multichannel = standardMedia.copy(audioChannels = 8)
        val tvStereoOnly = defaultDevice.copy(maxSupportedAudioChannels = 2)
        val choice = EngineSelector.selectEngine(multichannel, tvStereoOnly)
        assertTrue(choice is EngineChoice.Mpv)
        assertTrue(choice.reason.contains("Audio channels (8) exceed device maximum (2)"))
    }

    @Test
    fun `low RAM device favors Media3 when media is compatible`() {
        val lowRamDevice = defaultDevice.copy(isLowRamDevice = true)
        val choice = EngineSelector.selectEngine(standardMedia, lowRamDevice)
        assertTrue(choice is EngineChoice.Media3)
        assertTrue(choice.reason.contains("Low-RAM device"))
    }
}
