package zechs.drive.stream.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.model.MediaSource

class LocalMediaRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val repository = LocalMediaRepository(context)

    @Test
    fun getRequiredPermission_returnsAppropriatePermission() {
        val perm = repository.getRequiredPermission()
        assertTrue(
            perm == android.Manifest.permission.READ_MEDIA_VIDEO ||
            perm == android.Manifest.permission.READ_EXTERNAL_STORAGE
        )
    }

    @Test
    fun hasPermission_whenGranted_returnsTrue() {
        val perm = repository.getRequiredPermission()
        every { context.checkPermission(perm, any(), any()) } returns PackageManager.PERMISSION_GRANTED

        assertTrue(repository.hasPermission())
    }

    @Test
    fun hasPermission_whenDenied_returnsFalse() {
        val perm = repository.getRequiredPermission()
        every { context.checkPermission(perm, any(), any()) } returns PackageManager.PERMISSION_DENIED

        assertFalse(repository.hasPermission())
    }

    @Test
    fun localBucketPrefix_isConsistent() {
        assertEquals("local_folder:", LocalMediaRepository.LOCAL_BUCKET_PREFIX)
    }
}
