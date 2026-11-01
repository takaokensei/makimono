package zechs.drive.stream.ui.home

import org.junit.Assert.*
import org.junit.Test
import zechs.drive.stream.data.model.*

class CatalogSectionsTest {
    private fun item(source: MediaSource) = DriveFile("id", "BLEACH", null,
        "application/vnd.google-apps.folder", null, shortcutDetails = ShortcutDetails(),
        starred = Starred.UNSTARRED, source = source)
    @Test fun discoveryNeverLeaksIntoHome() {
        val sections = catalogSections(listOf(item(MediaSource.DRIVE), item(MediaSource.CATALOG_SEEDBOX)), false)
        assertEquals(1, sections.library.size)
        assertTrue(sections.discovery.isEmpty())
    }
    @Test fun searchKeepsUnavailableTitlesInTheirOwnSection() {
        val sections = catalogSections(listOf(item(MediaSource.DRIVE), item(MediaSource.CATALOG_SEEDBOX)), true)
        assertEquals(MediaSource.DRIVE, sections.library.single().source)
        assertEquals(MediaSource.CATALOG_SEEDBOX, sections.discovery.single().source)
    }
}
