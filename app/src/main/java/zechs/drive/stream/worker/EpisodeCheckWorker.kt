package zechs.drive.stream.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import zechs.drive.stream.R
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.FollowedFolderRepository
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.state.Resource

/**
 * FEAT-05: Periodic background worker that checks every followed folder for
 * new episodes by comparing the current Drive child-count to the cached value.
 *
 * Scheduling: Enqueued with [PeriodicWorkRequestBuilder] at app startup
 * (see [ThisApp.onCreate]) with a minimum interval of 12 h.
 *
 * Notification channel: [CHANNEL_ID] must exist before posting (created in
 * [ensureChannel]).  On API < 26 [NotificationChannel] is a no-op.
 *
 * Isolation: Each profile's followed list is checked independently so two
 * profiles can follow different folders without cross-contamination.
 */
@HiltWorker
class EpisodeCheckWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val driveRepository: DriveRepository,
    private val followedFolderRepository: FollowedFolderRepository,
    private val profileManager: ProfileManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "EpisodeCheckWorker"
        const val CHANNEL_ID = "new_episodes"

        /**
         * Drive Files.list query that counts direct children of a folder.
         * We only request 1 result (pageSize=1) but the totalItemCount from
         * the response is not directly available via the basic Drive v3 API —
         * instead we use getAllFiles with a small cap to get an accurate count
         * without loading all content bytes.
         *
         * In practice, most anime folders contain 1–200 episodes, so fetching
         * up to the repository safety cap of 25 pages. This avoids silently
         * missing new episodes in large folders.
         */
        private const val COUNT_PAGE_SIZE = 100
        private const val COUNT_MAX_PAGES = DriveRepository.MAX_PAGINATION_PAGES

        private fun folderChildrenQuery(folderId: String) =
            "'$folderId' in parents and trashed = false"
    }

    override suspend fun doWork(): Result {
        ensureChannel()

        // Read the persisted profile list instead of assuming profile IDs.
        val profileIds = profileManager.getProfiles().map { it.id }
        var anyFailure = false

        for (profileId in profileIds) {
            val followed = try {
                followedFolderRepository.getFollowedSync(profileId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                anyFailure = true
                continue
            }

            for (folder in followed) {
                val result = driveRepository.getAllFiles(
                    query = folderChildrenQuery(folder.folderId),
                    pageSize = COUNT_PAGE_SIZE,
                    maxPages = COUNT_MAX_PAGES
                )

                when (result) {
                    is Resource.Success -> {
                        val currentCount = result.data.size
                        val previousCount = folder.lastKnownCount

                        if (currentCount > previousCount && previousCount > 0) {
                            // New files detected — fire a notification
                            val newCount = currentCount - previousCount
                            postNotification(folder.folderName, newCount, folder.folderId)
                        }

                        // Always update the cached count so subsequent checks
                        // are anchored to the latest state, even on first run.
                        runCatching {
                            followedFolderRepository.updateLastKnownCount(
                                folderId = folder.folderId,
                                count = currentCount,
                                profileId = profileId
                            )
                        }
                    }

                    is Resource.Error -> {
                        // Non-fatal per folder: log and continue so one 403 on
                        // a shared folder doesn't block all other checks.
                        anyFailure = true
                    }

                    else -> {
                        // Resource.Loading should never be returned by getAllFiles
                        // (it's a one-shot suspend call), but the sealed class
                        // requires exhaustiveness — treat it as a transient skip.
                    }
                }
            }
        }

        // Retry on partial failures so transient network errors recover without
        // losing the check entirely.
        return if (anyFailure) Result.retry() else Result.success()
    }

    // -------------------------------------------------------------------------
    // Notification helpers
    // -------------------------------------------------------------------------

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_new_episodes_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notif_channel_new_episodes_desc)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun postNotification(folderName: String, newCount: Int, folderId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = context.getString(R.string.notif_new_episodes_title, folderName)
        val body = context.resources.getQuantityString(
            R.plurals.notif_new_episodes_body,
            newCount,
            newCount
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_24)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Use folderId hash as notification id so each folder gets its own
        // notification slot and updating the same folder replaces the previous one.
        nm.notify(folderId.hashCode(), notification)
    }
}
