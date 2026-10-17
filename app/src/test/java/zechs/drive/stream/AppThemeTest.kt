package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.utils.AppTheme

class AppThemeTest {
    @Test
    fun `existing persisted theme identifiers remain stable`() {
        assertEquals(AppTheme.TOKYO_NIGHT, AppTheme.fromValue(0))
        assertEquals(AppTheme.DRACULA, AppTheme.fromValue(1))
        assertEquals(AppTheme.NORD, AppTheme.fromValue(2))
        assertEquals(AppTheme.CATPPUCCIN_MOCHA, AppTheme.fromValue(3))
        assertEquals(AppTheme.KODI_ESTUARY, AppTheme.fromValue(4))
    }

    @Test
    fun `Obsidian is the default fallback without changing saved selections`() {
        assertEquals(AppTheme.OBSIDIAN, AppTheme.fromValue(5))
        assertEquals(AppTheme.OBSIDIAN, AppTheme.fromValue(Int.MAX_VALUE))
        assertEquals(AppTheme.OBSIDIAN, AppTheme.fromText(null))
        assertEquals(AppTheme.DRACULA, AppTheme.fromText("Dracula"))
    }

    @Test
    fun `Crunchyroll theme is value 6 with signature orange accent`() {
        assertEquals(AppTheme.CRUNCHYROLL, AppTheme.fromValue(6))
        assertEquals(AppTheme.CRUNCHYROLL, AppTheme.fromText("Crunchyroll"))
        assertEquals("#0C0D10", AppTheme.CRUNCHYROLL.bgBaseHex)
        assertEquals("#FF640A", AppTheme.CRUNCHYROLL.accentPrimaryHex)
    }
}
