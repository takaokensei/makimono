package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.utils.ProfileManagementPolicy

class ProfileManagementPolicyTest {
    private val admin = UserProfile(id = "admin", name = "Admin", isAdmin = true)
    private val secondAdmin = UserProfile(id = "admin-2", name = "Admin 2", isAdmin = true)
    private val viewer = UserProfile(id = "viewer", name = "Viewer")

    @Test
    fun cannotDeleteOnlyProfile() {
        assertEquals(
            ProfileManagementPolicy.DeleteBlockReason.LAST_PROFILE,
            ProfileManagementPolicy.deleteBlockReason(admin.id, listOf(admin))
        )
    }

    @Test
    fun cannotDeleteOnlyAdministrator() {
        assertEquals(
            ProfileManagementPolicy.DeleteBlockReason.LAST_ADMINISTRATOR,
            ProfileManagementPolicy.deleteBlockReason(admin.id, listOf(admin, viewer))
        )
    }

    @Test
    fun canDeleteAdministratorWhenAnotherAdministratorRemains() {
        assertNull(ProfileManagementPolicy.deleteBlockReason(admin.id, listOf(admin, secondAdmin, viewer)))
    }

    @Test
    fun cannotDemoteOnlyAdministratorButCanDemoteOneOfSeveral() {
        assertFalse(ProfileManagementPolicy.canDemote(admin.id, listOf(admin, viewer), remainsAdmin = false))
        assertTrue(ProfileManagementPolicy.canDemote(admin.id, listOf(admin, secondAdmin), remainsAdmin = false))
        assertTrue(ProfileManagementPolicy.canDemote(viewer.id, listOf(admin, viewer), remainsAdmin = false))
    }

    @Test
    fun missingProfileCannotBeDeletedOrDemoted() {
        assertEquals(
            ProfileManagementPolicy.DeleteBlockReason.PROFILE_NOT_FOUND,
            ProfileManagementPolicy.deleteBlockReason("missing", listOf(admin))
        )
        assertFalse(ProfileManagementPolicy.canDemote("missing", listOf(admin), remainsAdmin = false))
    }
}
