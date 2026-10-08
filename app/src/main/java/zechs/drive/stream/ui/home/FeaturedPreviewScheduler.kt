package zechs.drive.stream.ui.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import zechs.drive.stream.data.model.DriveFile

/** Debounces hero previews while focus moves across Home shelves. */
internal class FeaturedPreviewScheduler(
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val onPreview: (DriveFile) -> Unit
) {
    private var pendingJob: Job? = null

    fun schedule(folder: DriveFile, isStillFocused: () -> Boolean) {
        if (!folder.isFolder && !folder.isShortcutFolder) return

        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(debounceMillis)
            if (isStillFocused()) onPreview(folder)
        }
    }

    fun cancel() {
        pendingJob?.cancel()
        pendingJob = null
    }

    private companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 250L
    }
}
