package zechs.drive.stream.data.local

import androidx.annotation.Keep
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * FEAT-04: Offline Catalog Cache Entry.
 * Caches Drive files/folders and poster metadata locally to eliminate redundant network roundtrips,
 * lower Drive API quota consumption, and support offline browsing.
 */
@Keep
@Entity(tableName = "catalog_entry")
data class CatalogEntry(
    @PrimaryKey val id: String,
    val name: String,
    val mimeType: String,
    val parentId: String? = null,
    val posterUrl: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
