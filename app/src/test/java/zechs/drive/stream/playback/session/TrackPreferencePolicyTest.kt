package zechs.drive.stream.playback.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import zechs.drive.stream.data.model.SubtitleItem
import zechs.drive.stream.playback.session.TrackPreferencePolicy.TrackCandidate
import zechs.drive.stream.playback.session.TrackPreferencePolicy.TrackPreference

class TrackPreferencePolicyTest {

    @Test
    fun `selectPreferredAudioTrack defaults to Japanese when no series preference exists`() {
        val candidates = listOf(
            TrackCandidate(id = "1", lang = "en", title = "English Dub"),
            TrackCandidate(id = "2", lang = "ja", title = "Japanese Original"),
            TrackCandidate(id = "3", lang = "pt", title = "Dublado PT-BR")
        )
        val selected = TrackPreferencePolicy.selectPreferredAudioTrack(candidates)
        assertEquals("2", selected?.id)
    }

    @Test
    fun `selectPreferredAudioTrack honors explicit series audio preference`() {
        val candidates = listOf(
            TrackCandidate(id = "1", lang = "ja", title = "Japanese Original"),
            TrackCandidate(id = "2", lang = "pt-BR", title = "Português")
        )
        val pref = TrackPreference(preferredAudioLang = "pt-br")
        val selected = TrackPreferencePolicy.selectPreferredAudioTrack(candidates, pref)
        assertEquals("2", selected?.id)
    }

    @Test
    fun `selectPreferredSubtitleTrack scores PT-BR full subtitle higher than forced signs`() {
        val candidates = listOf(
            TrackCandidate(id = "1", lang = "en", title = "English"),
            TrackCandidate(id = "2", lang = "pt", title = "Português Forced Signs"),
            TrackCandidate(id = "3", lang = "pt-BR", title = "Português (BR) Completo")
        )
        val selected = TrackPreferencePolicy.selectPreferredSubtitleTrack(candidates)
        assertEquals("3", selected?.id)
    }

    @Test
    fun `selectPreferredSubtitleTrack returns null when subtitlesDisabled is true`() {
        val candidates = listOf(
            TrackCandidate(id = "1", lang = "pt-BR", title = "Português (BR)")
        )
        val pref = TrackPreference(subtitlesDisabled = true)
        assertNull(TrackPreferencePolicy.selectPreferredSubtitleTrack(candidates, pref))
    }

    @Test
    fun `clampDelayMs and formatDelayLabel enforce bounds and sign`() {
        assertEquals(200L, TrackPreferencePolicy.clampDelayMs(200L))
        assertEquals(60_000L, TrackPreferencePolicy.clampDelayMs(120_000L))
        assertEquals(-60_000L, TrackPreferencePolicy.clampDelayMs(-90_000L))

        assertEquals("+250 ms", TrackPreferencePolicy.formatDelayLabel(250L))
        assertEquals("-150 ms", TrackPreferencePolicy.formatDelayLabel(-150L))
        assertEquals("0 ms", TrackPreferencePolicy.formatDelayLabel(0L))
    }

    @Test
    fun `createSavedSubtitleItem builds virtual item with saved prefix`() {
        val item = TrackPreferencePolicy.createSavedSubtitleItem("Frieren_01", "Português (BR)", "pt-BR")
        assertEquals("saved_Frieren_01", item.id)
        assertEquals("💾 [Salva] Português (BR)", item.name)
        assertEquals("pt-BR", item.languageCode)
    }
}
