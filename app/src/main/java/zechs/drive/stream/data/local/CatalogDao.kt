package zechs.drive.stream.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * FEAT-04: DAO for offline catalog cache.
 */
@Dao
interface CatalogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<CatalogEntry>)

    @Query("SELECT * FROM catalog_entry WHERE parentId = :parentId ORDER BY name ASC")
    suspend fun getChildren(parentId: String): List<CatalogEntry>

    @Query("SELECT * FROM catalog_entry WHERE parentId = :parentId ORDER BY name ASC")
    fun observeChildren(parentId: String): Flow<List<CatalogEntry>>

    @Query("SELECT * FROM catalog_entry WHERE name LIKE '%' || :query || '%' LIMIT :limit")
    suspend fun searchCatalog(query: String, limit: Int = 50): List<CatalogEntry>

    @Query("DELETE FROM catalog_entry WHERE parentId = :parentId")
    suspend fun clearFolder(parentId: String)

    @Query("DELETE FROM catalog_entry")
    suspend fun clearAll()
}
