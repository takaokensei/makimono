package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import zechs.drive.stream.ui.player.GlassMenuFocusPolicy
import zechs.drive.stream.ui.player.GlassMenuItem

class PlayerGlassMenuFocusPolicyTest {
    @Test
    fun readOnlyTechnicalRowsDoNotBecomeInitialDpadTargets() {
        val items = listOf(
            GlassMenuItem("video", "Vídeo", "h264", isInteractive = false),
            GlassMenuItem("audio", "Áudio", "AAC", isInteractive = false)
        )

        assertNull(GlassMenuFocusPolicy.initialInteractiveIndex(items))
    }

    @Test
    fun initialFocusPrefersTheSelectedInteractiveAction() {
        val items = listOf(
            GlassMenuItem("info", "Informação", isInteractive = false),
            GlassMenuItem("normal", "Velocidade"),
            GlassMenuItem("selected", "1.0x", isSelected = true)
        )

        assertEquals(2, GlassMenuFocusPolicy.initialInteractiveIndex(items))
    }

    @Test
    fun initialFocusFallsBackToFirstActionWhenThereIsNoSelection() {
        val items = listOf(
            GlassMenuItem("info", "Informação", isInteractive = false),
            GlassMenuItem("first", "Velocidade"),
            GlassMenuItem("next", "Proporção")
        )

        assertEquals(1, GlassMenuFocusPolicy.initialInteractiveIndex(items))
    }
}
