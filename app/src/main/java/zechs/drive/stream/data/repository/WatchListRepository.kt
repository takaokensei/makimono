package zechs.drive.stream.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import zechs.drive.stream.data.local.WatchListDao
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.TvWatchNextManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchListRepository @Inject constructor(
    private val watchListDao: WatchListDao,
    private val profileManager: ProfileManager,
    @ApplicationContext private val context: Context? = null
) {

    private fun currentProfileId(): String = profileManager.getActiveProfile().id

    suspend fun insertWatch(
        watchList: WatchList,
        profileId: String = currentProfileId()
    ): Long {
        val watchWithProfile = if (watchList.profileId.isBlank()) {
            watchList.copy(profileId = profileId)
        } else {
            watchList
        }
        val rowId = watchListDao.upsertWatch(watchWithProfile)
        context?.let { ctx ->
            if (watchWithProfile.hasFinished()) {
                TvWatchNextManager.removeWatchNext(ctx, watchWithProfile.videoId)
            } else {
                TvWatchNextManager.updateWatchNext(ctx, watchWithProfile)
            }
        }
        return rowId
    }

    suspend fun getWatch(
        videoId: String,
        profileId: String = currentProfileId()
    ): WatchList? {
        return watchListDao.getWatch(videoId, profileId)
    }

    suspend fun getWatches(
        videoIds: List<String>,
        profileId: String = currentProfileId()
    ): List<WatchList> {
        return watchListDao.getWatches(profileId, videoIds)
    }

    suspend fun getLastWatched(
        profileId: String = currentProfileId()
    ): WatchList? {
        return watchListDao.getLastWatched(profileId)
    }

    /**
     * Returns up to [limit] most recent in-progress titles for a "Continuar assistindo"
     * row, filtering out anything already past the finished threshold
     * (see [WatchList.hasFinished]) since a completed episode has nothing left to resume.
     * Over-fetches by a small factor before filtering so the finished-item filter
     * doesn't starve the row down to fewer than [limit] entries when recent titles
     * happen to include several finished ones.
     */
    suspend fun getRecentWatches(
        limit: Int = 10,
        profileId: String = currentProfileId()
    ): List<WatchList> {
        return watchListDao.getRecentWatches(profileId, limit * 2)
            .filterNot { it.hasFinished() }
            .take(limit)
    }

    suspend fun deleteWatch(
        watch: WatchList
    ) {
        context?.let { ctx ->
            TvWatchNextManager.removeWatchNext(ctx, watch.videoId)
        }
        watchListDao.deleteWatch(watch)
    }

    suspend fun deleteWatchByVideoId(
        videoId: String,
        profileId: String = currentProfileId()
    ) {
        context?.let { ctx ->
            TvWatchNextManager.removeWatchNext(ctx, videoId)
        }
        watchListDao.deleteWatchByVideoId(videoId, profileId)
    }

    suspend fun getAllWatches(
        profileId: String = currentProfileId()
    ): List<WatchList> = watchListDao.getAllWatches(profileId)

    suspend fun getAllWatchesGlobal(): List<WatchList> =
        watchListDao.getAllWatchesGlobal()

    suspend fun updateDelays(
        videoId: String,
        audioDelayMs: Long,
        subtitleDelayMs: Long,
        profileId: String = currentProfileId()
    ): Int = watchListDao.updateDelays(videoId, profileId, audioDelayMs, subtitleDelayMs)

}