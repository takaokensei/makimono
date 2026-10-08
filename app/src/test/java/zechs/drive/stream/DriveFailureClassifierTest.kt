package zechs.drive.stream

import org.junit.Assert.assertEquals
import org.junit.Test
import zechs.drive.stream.data.repository.DriveFailureClassifier
import zechs.drive.stream.utils.state.ResourceErrorKind
import java.io.IOException

class DriveFailureClassifierTest {
    @Test
    fun httpStatusesMapToRecoveryCategories() {
        assertEquals(ResourceErrorKind.AUTHENTICATION, DriveFailureClassifier.fromHttp(401))
        assertEquals(ResourceErrorKind.ACCESS_DENIED, DriveFailureClassifier.fromHttp(403))
        assertEquals(ResourceErrorKind.NOT_FOUND, DriveFailureClassifier.fromHttp(404))
        assertEquals(ResourceErrorKind.RATE_LIMITED, DriveFailureClassifier.fromHttp(429))
        assertEquals(ResourceErrorKind.SERVER, DriveFailureClassifier.fromHttp(503))
        assertEquals(ResourceErrorKind.UNKNOWN, DriveFailureClassifier.fromHttp(418))
    }

    @Test
    fun providerQuotaReasonsOverrideGenericForbiddenStatus() {
        assertEquals(ResourceErrorKind.RATE_LIMITED, DriveFailureClassifier.fromHttp(403, "userRateLimitExceeded"))
        assertEquals(ResourceErrorKind.RATE_LIMITED, DriveFailureClassifier.fromHttp(403, "quotaExceeded"))
    }

    @Test
    fun ioFailuresMapToNetworkAndUnexpectedFailuresRemainUnknown() {
        assertEquals(ResourceErrorKind.NETWORK, DriveFailureClassifier.fromException(IOException("offline")))
        assertEquals(ResourceErrorKind.UNKNOWN, DriveFailureClassifier.fromException(IllegalStateException("bad state")))
    }
}
