package zechs.drive.stream.domain.library

import org.junit.Assert.*
import org.junit.Test

class LibraryRootChangePolicyTest {
    @Test fun aChangedRootRefreshesTheSameProfile() {
        assertTrue(LibraryRootChangePolicy.needsRefresh("profile", "old-folder", "profile", "new-folder"))
    }
    @Test fun missingCacheLetsTheInitialLoadRunOnce() {
        assertFalse(LibraryRootChangePolicy.needsRefresh(null, null, "profile", "new-folder"))
    }
    @Test fun unchangedRootAndLegacyDriveRootDoNotReloadOnEveryResume() {
        assertFalse(LibraryRootChangePolicy.needsRefresh("profile", "root", "profile", null))
        assertFalse(LibraryRootChangePolicy.needsRefresh("profile", "folder", "profile", "folder"))
        assertTrue(LibraryRootChangePolicy.needsRefresh("old-profile", "folder", "new-profile", "folder"))
    }
}
