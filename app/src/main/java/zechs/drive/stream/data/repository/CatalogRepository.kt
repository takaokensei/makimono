package zechs.drive.stream.data.repository

import kotlinx.coroutines.flow.Flow
import zechs.drive.stream.data.local.CatalogDao
import zechs.drive.stream.data.local.CatalogEntry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FEAT-04: Repository for Offline Catalog Cache.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val catalogDao: CatalogDao
) {
    suspend fun cacheFolderContents(parentId: String, entries: List<CatalogEntry>) {
        catalogDao.clearFolder(parentId)
        catalogDao.insertAll(entries)
    }

    suspend fun getChildren(parentId: String): List<CatalogEntry> {
        return catalogDao.getChildren(parentId)
    }

    fun observeChildren(parentId: String): Flow<List<CatalogEntry>> {
        return catalogDao.observeChildren(parentId)
    }

    suspend fun searchCatalog(query: String, limit: Int = 50): List<CatalogEntry> {
        return catalogDao.searchCatalog(query, limit)
    }

    suspend fun clearAll() {
        catalogDao.clearAll()
    }
}
