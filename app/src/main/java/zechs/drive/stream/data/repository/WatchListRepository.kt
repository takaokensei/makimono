package zechs.drive.stream.data.repository

import zechs.drive.stream.data.local.WatchListDao
import zechs.drive.stream.data.model.WatchList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchListRepository @Inject constructor(
    private val watchListDao: WatchListDao
) {

    suspend fun insertWatch(
        watchList: WatchList
    ) = watchListDao.upsertWatch(watchList)

    suspend fun getWatch(videoId: String): WatchList? {
        return watchListDao.getWatch(videoId)
    }

    suspend fun getLastWatched(): WatchList? {
        return watchListDao.getLastWatched()
    }

    /**
     * Returns up to [limit] most recent in-progress titles for a "Continuar assistindo"
     * row, filtering out anything already past the finished threshold
     * (see [WatchList.hasFinished]) since a completed episode has nothing left to resume.
     * Over-fetches by a small factor before filtering so the finished-item filter
     * doesn't starve the row down to fewer than [limit] entries when recent titles
     * happen to include several finished ones.
     */
    suspend fun getRecentWatches(limit: Int = 10): List<WatchList> {
        return watchListDao.getRecentWatches(limit * 2)
            .filterNot { it.hasFinished() }
            .take(limit)
    }

    suspend fun deleteWatch(
        watch: WatchList
    ) = watchListDao.deleteWatch(watch)

}