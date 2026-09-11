package zechs.drive.stream.data.local


import androidx.room.*
import zechs.drive.stream.data.model.WatchList

@Dao
interface WatchListDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatch(watch: WatchList): Long

    @Query("SELECT * FROM `watch_list` WHERE videoId = :videoId LIMIT 1")
    suspend fun getWatch(videoId: String): WatchList?

    @Query("SELECT * FROM `watch_list` ORDER BY id DESC LIMIT 1")
    suspend fun getLastWatched(): WatchList?

    // Backs a real "Continuar assistindo" row (multiple in-progress titles),
    // instead of surfacing only the single most recent playback. Finished
    // items (>95% watched, see WatchList.hasFinished()) are excluded at the
    // repository layer since a Room expression column would duplicate that
    // business rule here.
    @Query("SELECT * FROM `watch_list` ORDER BY id DESC LIMIT :limit")
    suspend fun getRecentWatches(limit: Int): List<WatchList>

    @Delete
    suspend fun deleteWatch(watch: WatchList)

}