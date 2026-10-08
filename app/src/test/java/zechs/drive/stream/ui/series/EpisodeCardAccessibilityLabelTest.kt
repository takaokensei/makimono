package zechs.drive.stream.ui.series

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeCardAccessibilityLabelTest {

    @Test
    fun includesKnownActionAndPlaybackMetadataInOrder() {
        val label = EpisodeCardAccessibilityLabel.build(
            actionAndTitle = "Reproduzir Episódio 02 - Uma nova jornada",
            duration = "24 min",
            audio = "Áudio duplo",
            progress = "Em andamento, 42% assistido"
        )

        assertEquals(
            "Reproduzir Episódio 02 - Uma nova jornada, 24 min, Áudio duplo, Em andamento, 42% assistido",
            label
        )
    }

    @Test
    fun omitsUnknownMetadataWithoutEmptySeparators() {
        val label = EpisodeCardAccessibilityLabel.build(
            actionAndTitle = "Reproduzir Episódio 01",
            duration = " ",
            audio = null,
            progress = null
        )

        assertEquals("Reproduzir Episódio 01", label)
    }

    @Test
    fun trimsMetadataBeforeJoining() {
        val label = EpisodeCardAccessibilityLabel.build(
            actionAndTitle = " Reproduzir Episódio 03 ",
            duration = " 24 min ",
            audio = "",
            progress = " Assistido "
        )

        assertEquals("Reproduzir Episódio 03, 24 min, Assistido", label)
    }
}
