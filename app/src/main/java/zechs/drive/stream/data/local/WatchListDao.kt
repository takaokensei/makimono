package zechs.drive.stream.data.local


import androidx.room.*
import zechs.drive.stream.data.model.WatchList

@Dao
interface WatchListDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatch(watch: WatchList): Long

    @Query("SELECT * FROM `watch_list` WHERE videoId = :videoId AND profileId = :profileId LIMIT 1")
    suspend fun getWatch(videoId: String, profileId: String): WatchList?

    @Query("SELECT * FROM `watch_list` WHERE profileId = :profileId ORDER BY id DESC LIMIT 1")
    suspend fun getLastWatched(profileId: String): WatchList?

    // Backs a real "Continuar assistindo" row (multiple in-progress titles),
    // instead of surfacing only the single most recent playback. Finished
    // items (>95% watched, see WatchList.hasFinished()) are excluded at the
    // repository layer since a Room expression column would duplicate that
    // business rule here.
    @Query("SELECT * FROM `watch_list` WHERE profileId = :profileId ORDER BY id DESC LIMIT :limit")
    suspend fun getRecentWatches(profileId: String, limit: Int): List<WatchList>

    @Query("SELECT * FROM `watch_list` WHERE profileId = :profileId AND videoId IN (:videoIds)")
    suspend fun getWatches(profileId: String, videoIds: List<String>): List<WatchList>

    @Delete
    suspend fun deleteWatch(watch: WatchList)

    @Query("DELETE FROM `watch_list` WHERE videoId = :videoId AND profileId = :profileId")
    suspend fun deleteWatchByVideoId(videoId: String, profileId: String)

    @Query("SELECT * FROM `watch_list` WHERE profileId = :profileId")
    suspend fun getAllWatches(profileId: String): List<WatchList>

    @Query("SELECT * FROM `watch_list`")
    suspend fun getAllWatchesGlobal(): List<WatchList>
}