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

/**
 * FEAT-05: Periodic background worker that checks every followed folder for
 * new episodes by comparing the current count of unique playable video children
 * to the cached value. A partial Drive traversal is never used as a new baseline.
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
         * instead we use getAllFilesDetailed to get an accurate count
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

        var anyFailure = false
        // Read the persisted profile list instead of assuming profile IDs.
        val profileIds = try {
            profileManager.getProfiles().map { it.id }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return Result.retry()
        }

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
                val decision = try {
                    EpisodeCountPolicy.evaluate(
                        previousVideoCount = folder.lastKnownCount,
                        result = driveRepository.getAllFilesDetailed(
                            query = folderChildrenQuery(folder.folderId),
                            pageSize = COUNT_PAGE_SIZE,
                            maxPages = COUNT_MAX_PAGES
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    anyFailure = true
                    continue
                }

                when (decision) {
                    is EpisodeCountPolicy.Decision.Complete -> {
                        if (decision.newEpisodeCount > 0) {
                            postNotification(folder.folderName, decision.newEpisodeCount, folder.folderId)
                        }

                        try {
                            followedFolderRepository.updateLastKnownCount(
                                folderId = folder.folderId,
                                count = decision.videoCount,
                                profileId = profileId
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            anyFailure = true
                        }
                    }

                    EpisodeCountPolicy.Decision.Retry -> anyFailure = true
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
