package zechs.drive.stream.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.ui.main.StartupNavigationPolicy
import zechs.drive.stream.ui.main.StartupNavigationPolicy.Destination

class StartupNavigationPolicyTest {
    @Test fun disconnectedAlwaysShowsPairing() {
        assertEquals(Destination.PAIRING, StartupNavigationPolicy.destination(false))
        assertEquals(Destination.PAIRING, StartupNavigationPolicy.destination(false, true))
    }
    @Test fun newAuthorizationPreparesBeforeProfiles() {
        assertEquals(Destination.PREPARATION, StartupNavigationPolicy.destination(true, true))
    }
    @Test fun savedSessionReturnsToProfilesWithoutIntro() {
        assertEquals(Destination.PROFILES, StartupNavigationPolicy.destination(true))
    }
}
