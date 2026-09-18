package zechs.drive.stream.data.repository

import kotlinx.coroutines.flow.Flow
import zechs.drive.stream.data.local.WatchQueueDao
import zechs.drive.stream.data.model.WatchQueueItem
import zechs.drive.stream.utils.ProfileManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FEAT-03: Repository for Watch Queue ("Assistir Depois").
 * Manages per-profile playback queues.
 */
@Singleton
class WatchQueueRepository @Inject constructor(
    private val watchQueueDao: WatchQueueDao,
    private val profileManager: ProfileManager
) {
    private fun currentProfileId(): String = profileManager.getActiveProfile().id

    suspend fun enqueue(
        fileId: String,
        name: String,
        posterUrl: String? = null,
        profileId: String = currentProfileId()
    ): Long {
        val current = watchQueueDao.getQueueSync(profileId)
        val nextIndex = (current.maxOfOrNull { it.orderIndex } ?: 0) + 1
        return watchQueueDao.enqueue(
            WatchQueueItem(
                profileId = profileId,
                fileId = fileId,
                name = name,
                posterUrl = posterUrl,
                orderIndex = nextIndex,
                addedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun dequeue(fileId: String, profileId: String = currentProfileId()) {
        watchQueueDao.dequeue(fileId, profileId)
    }

    fun observeQueue(profileId: String = currentProfileId()): Flow<List<WatchQueueItem>> {
        return watchQueueDao.observeQueue(profileId)
    }

    suspend fun getQueueSync(profileId: String = currentProfileId()): List<WatchQueueItem> {
        return watchQueueDao.getQueueSync(profileId)
    }

    suspend fun isInQueue(fileId: String, profileId: String = currentProfileId()): Boolean {
        return watchQueueDao.isInQueue(fileId, profileId)
    }

    suspend fun getQueueCount(profileId: String = currentProfileId()): Int {
        return watchQueueDao.getQueueCount(profileId)
    }

    suspend fun clearQueue(profileId: String = currentProfileId()) {
        watchQueueDao.clearQueue(profileId)
    }
}
