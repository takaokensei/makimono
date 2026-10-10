package zechs.drive.stream

import android.content.Context
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.utils.TvWatchNextManager

class TvWatchNextManagerTest {

    @Test
    fun isTv_returnsTrue_whenLeanbackFeatureIsPresent() {
        val packageManager = mockk<PackageManager>()
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns true

        val context = mockk<Context>()
        every { context.packageManager } returns packageManager

        assertTrue(TvWatchNextManager.isTv(context))
    }

    @Test
    fun isTv_returnsFalse_whenNeitherLeanbackNorTelevisionIsPresent() {
        val packageManager = mockk<PackageManager>()
        every { packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) } returns false
        every { packageManager.hasSystemFeature("android.hardware.type.television") } returns false

        val context = mockk<Context>()
        every { context.packageManager } returns packageManager

        assertFalse(TvWatchNextManager.isTv(context))
    }

    @Test
    fun isTv_returnsFalse_whenPackageManagerThrowsException() {
        val context = mockk<Context>()
        every { context.packageManager } throws RuntimeException("PackageManager dead")

        assertFalse(TvWatchNextManager.isTv(context))
    }

    @Test
    fun watchList_hasFinished_correctlyIdentifiesCompletion() {
        val inProgress = WatchList(
            name = "Attack on Titan - Episode 1",
            videoId = "vid_001",
            watchedDuration = 600_000L,
            totalDuration = 1_440_000L
        )
        assertFalse(inProgress.hasFinished())

        val finished = WatchList(
            name = "Attack on Titan - Episode 1",
            videoId = "vid_001",
            watchedDuration = 1_400_000L,
            totalDuration = 1_440_000L
        )
        assertTrue(finished.hasFinished())
    }
}
