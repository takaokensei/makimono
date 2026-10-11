package zechs.drive.stream.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.playback.engine.DeviceCaps
import zechs.drive.stream.utils.VideoPlayer

class PlayerLauncherTest {

    private val defaultDevice = DeviceCaps(
        isTv = true,
        isLowRamDevice = false,
        supportedVideoCodecs = setOf("h264", "hevc"),
        supportedAudioCodecs = setOf("aac", "mp3"),
        maxSupportedAudioChannels = 6,
        supportsHardwareAss = false
    )

    @Test
    fun `default exo player with no subtitles resolves to EXO_PLAYER`() {
        val player = PlayerLauncher.resolvePlayerType(
            playerType = VideoPlayer.EXO_PLAYER,
            title = "Movie.mkv",
            subtitles = null,
            deviceCaps = defaultDevice
        )
        assertEquals(VideoPlayer.EXO_PLAYER, player)
    }

    @Test
    fun `default exo player with ASS subtitles routes to MPV`() {
        val subs = listOf(
            SubtitleItem(id = "1", name = "Anime_S01E01.ass")
        )
        val player = PlayerLauncher.resolvePlayerType(
            playerType = VideoPlayer.EXO_PLAYER,
            title = "Anime_S01E01.mkv",
            subtitles = subs,
            deviceCaps = defaultDevice
        )
        assertEquals(VideoPlayer.MPV, player)
    }

    @Test
    fun `default exo player with SSA subtitles routes to MPV`() {
        val subs = listOf(
            SubtitleItem(id = "2", name = "Anime_S01E01.ssa")
        )
        val player = PlayerLauncher.resolvePlayerType(
            playerType = VideoPlayer.EXO_PLAYER,
            title = "Anime_S01E01.mkv",
            subtitles = subs,
            deviceCaps = defaultDevice
        )
        assertEquals(VideoPlayer.MPV, player)
    }

    @Test
    fun `default exo player with SRT subtitles stays on EXO_PLAYER`() {
        val subs = listOf(
            SubtitleItem(id = "3", name = "Movie.por.srt")
        )
        val player = PlayerLauncher.resolvePlayerType(
            playerType = VideoPlayer.EXO_PLAYER,
            title = "Movie.mkv",
            subtitles = subs,
            deviceCaps = defaultDevice
        )
        assertEquals(VideoPlayer.EXO_PLAYER, player)
    }

    @Test
    fun `explicit MPV choice always resolves to MPV`() {
        val player = PlayerLauncher.resolvePlayerType(
            playerType = VideoPlayer.MPV,
            title = "Movie.mkv",
            subtitles = null,
            deviceCaps = defaultDevice
        )
        assertEquals(VideoPlayer.MPV, player)
    }

    @Test
    fun `disabled auto routing keeps EXO_PLAYER even with ASS subtitles`() {
        val subs = listOf(
            SubtitleItem(id = "1", name = "Anime_S01E01.ass")
        )
        val player = PlayerLauncher.resolvePlayerType(
            playerType = VideoPlayer.EXO_PLAYER,
            title = "Anime_S01E01.mkv",
            subtitles = subs,
            autoRouting = false,
            deviceCaps = defaultDevice
        )
        assertEquals(VideoPlayer.EXO_PLAYER, player)
    }

    @Test
    fun `hardware ASS capable device stays on EXO_PLAYER with ASS subtitles`() {
        val hwAssDevice = defaultDevice.copy(supportsHardwareAss = true)
        val subs = listOf(
            SubtitleItem(id = "1", name = "Anime_S01E01.ass")
        )
        val player = PlayerLauncher.resolvePlayerType(
            playerType = VideoPlayer.EXO_PLAYER,
            title = "Anime_S01E01.mkv",
            subtitles = subs,
            autoRouting = true,
            deviceCaps = hwAssDevice
        )
        assertEquals(VideoPlayer.EXO_PLAYER, player)
    }
}
