package zechs.drive.stream.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryRootFolderQueryTest {
    @Test
    fun nullOrBlankParentUsesMyDriveRoot() {
        val expected = "'root' in parents and trashed=false and mimeType='application/vnd.google-apps.folder'"
        assertEquals(expected, LibraryRootFolderQuery.forParent(null))
        assertEquals(expected, LibraryRootFolderQuery.forParent(" "))
    }

    @Test
    fun childQueryUsesSelectedFolderId() {
        assertEquals(
            "'folder-oneblacki' in parents and trashed=false and mimeType='application/vnd.google-apps.folder'",
            LibraryRootFolderQuery.forParent("folder-oneblacki")
        )
    }

    @Test
    fun parentIdIsEscapedForDriveQuerySyntax() {
        assertEquals(
            "'folder\\'with\\\\escape' in parents and trashed=false and mimeType='application/vnd.google-apps.folder'",
            LibraryRootFolderQuery.forParent("folder'with\\escape")
        )
    }
}
