package zechs.drive.stream.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.domain.profile.ProfileSetupPolicy

class ProfileSetupPolicyTest {
    @Test fun existingProfilesWithNoStepAreComplete() { assertEquals(0, ProfileSetupPolicy.normalize(0)) }
    @Test fun knownStepsSurviveProcessRestart() {
        for (step in 1..3) assertEquals(step, ProfileSetupPolicy.normalize(step))
    }
    @Test fun unknownStoredVersionDoesNotTrapAnExistingProfile() { assertEquals(0, ProfileSetupPolicy.normalize(10)) }
    @Test fun backMovesOneStepAndNeverUnderflows() {
        assertEquals(2, ProfileSetupPolicy.previous(3))
        assertEquals(1, ProfileSetupPolicy.previous(1))
    }
}
