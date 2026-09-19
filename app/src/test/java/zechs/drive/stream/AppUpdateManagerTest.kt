package zechs.drive.stream

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zechs.drive.stream.utils.AppUpdateManager
import java.io.File
import java.security.MessageDigest

class AppUpdateManagerTest {

    private val context: Context = mockk(relaxed = true)
    private val okHttpClient: OkHttpClient = mockk(relaxed = true)
    private lateinit var appUpdateManager: AppUpdateManager

    @Before
    fun setUp() {
        appUpdateManager = AppUpdateManager(context, okHttpClient)
    }

    @Test
    fun verifyChecksum_validHash_returnsTrue() = runTest {
        val testContent = "Makimono secure update content".toByteArray()
        val tempFile = File.createTempFile("test-valid-apk", ".apk").apply {
            writeBytes(testContent)
            deleteOnExit()
        }

        val md = MessageDigest.getInstance("SHA-256")
        val expectedHash = md.digest(testContent).joinToString("") { "%02x".format(it) }

        val result = appUpdateManager.verifyChecksum(tempFile, expectedHash)
        assertTrue(result)
        tempFile.delete()
    }

    @Test
    fun verifyChecksum_mismatchedHash_returnsFalse() = runTest {
        val testContent = "Makimono secure update content".toByteArray()
        val tempFile = File.createTempFile("test-invalid-apk", ".apk").apply {
            writeBytes(testContent)
            deleteOnExit()
        }

        val mismatchedHash = "0".repeat(64)
        val result = appUpdateManager.verifyChecksum(tempFile, mismatchedHash)
        assertFalse(result)
        tempFile.delete()
    }

    @Test
    fun verifyChecksum_nonExistentFile_returnsFalse() = runTest {
        val nonExistentFile = File("non_existent_update_${System.currentTimeMillis()}.apk")
        val result = appUpdateManager.verifyChecksum(nonExistentFile, "abc123")
        assertFalse(result)
    }

    @Test
    fun installApk_nonExistentFile_returnsFalse() {
        val nonExistentFile = File("non_existent_update_${System.currentTimeMillis()}.apk")
        val result = appUpdateManager.installApk(nonExistentFile)
        assertFalse(result)
    }
}
