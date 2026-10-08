package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import zechs.drive.stream.ui.files.DriveFolderNavigationPolicy

class DriveFolderNavigationPolicyTest {
    @Test
    fun queryProvidesInitialAncestorWhenNoPathWasPassed() {
        assertEquals(
            DriveFolderNavigationPolicy.Decision.Navigate(listOf("oneblacki", "bakemono")),
            DriveFolderNavigationPolicy.decide(
                folderPath = null,
                currentQuery = "'oneblacki' in parents and trashed=false",
                targetFolderId = "bakemono"
            )
        )
    }

    @Test
    fun shortcutToCurrentOrAncestorFolderIsRejected() {
        assertEquals(
            DriveFolderNavigationPolicy.Decision.Cycle,
            DriveFolderNavigationPolicy.decide(
                folderPath = listOf("root", "oneblacki", "series"),
                currentQuery = null,
                targetFolderId = "oneblacki"
            )
        )
    }

    @Test
    fun nestedFolderAddsTargetWithoutLosingAncestors() {
        assertEquals(
            DriveFolderNavigationPolicy.Decision.Navigate(listOf("root", "oneblacki", "series", "season")),
            DriveFolderNavigationPolicy.decide(
                folderPath = listOf("root", "oneblacki", "series"),
                currentQuery = null,
                targetFolderId = "season"
            )
        )
    }

    @Test
    fun parentIdParserUnescapesDriveQueryIdentifiers() {
        assertEquals("folder'one\\two", DriveFolderNavigationPolicy.parentFolderId("'folder\\'one\\\\two' in parents"))
        assertNull(DriveFolderNavigationPolicy.parentFolderId("mimeType='video/mp4'"))
    }
}
