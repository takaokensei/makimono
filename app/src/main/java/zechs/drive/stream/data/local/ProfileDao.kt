package zechs.drive.stream.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY createdAt, id")
    abstract suspend fun getAll(): List<ProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(profiles: List<ProfileEntity>)

    @Query("DELETE FROM profiles")
    abstract suspend fun clear()

    @Transaction
    open suspend fun replaceAll(profiles: List<ProfileEntity>) {
        require(profiles.isNotEmpty())
        clear()
        insert(profiles)
    }
}
