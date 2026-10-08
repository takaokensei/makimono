package zechs.drive.stream.ui.home

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.ShortcutDetails
import zechs.drive.stream.data.model.Starred

@OptIn(ExperimentalCoroutinesApi::class)
class FeaturedPreviewSchedulerTest {
    @Test
    fun rapidFocusChangesPreviewOnlyTheLatestFolderAfterDebounce() = runTest {
        val previews = mutableListOf<String>()
        val scheduler = FeaturedPreviewScheduler(backgroundScope, debounceMillis = 250) {
            previews += it.id
        }

        scheduler.schedule(folder("first")) { true }
        advanceTimeBy(180)
        scheduler.schedule(folder("second")) { true }
        advanceTimeBy(249)
        runCurrent()

        assertTrue(previews.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("second"), previews)
    }

    @Test
    fun focusLeavingBeforeDebounceSuppressesPreview() = runTest {
        val previews = mutableListOf<String>()
        val scheduler = FeaturedPreviewScheduler(backgroundScope) { previews += it.id }
        var stillFocused = true
        scheduler.schedule(folder("series")) { stillFocused }

        stillFocused = false
        advanceTimeBy(250)
        runCurrent()

        assertTrue(previews.isEmpty())
    }

    @Test
    fun cancelSuppressesPendingPreview() = runTest {
        val previews = mutableListOf<String>()
        val scheduler = FeaturedPreviewScheduler(backgroundScope) { previews += it.id }
        scheduler.schedule(folder("series")) { true }

        scheduler.cancel()
        advanceTimeBy(250)
        runCurrent()

        assertTrue(previews.isEmpty())
    }

    private fun folder(id: String) = DriveFile(
        id = id,
        name = id,
        size = null,
        mimeType = "application/vnd.google-apps.folder",
        iconLink = null,
        shortcutDetails = ShortcutDetails(),
        starred = Starred.UNSTARRED
    )
}
