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
    fun `Estuary preview colors match its XML palette`() {
        assertEquals("#030B14", AppTheme.KODI_ESTUARY.bgBaseHex)
        assertEquals("#0B1928", AppTheme.KODI_ESTUARY.bgSurfaceHex)
        assertEquals("#22D3EE", AppTheme.KODI_ESTUARY.accentPrimaryHex)
    }
}
