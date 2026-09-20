package zechs.drive.stream.data.local

import javax.inject.Inject
import javax.inject.Singleton

/**
 * P0-03: apaga todos os dados Room vinculados a um perfil quando ele é
 * excluído, evitando vazamento de histórico/favoritos/fila entre perfis.
 */
@Singleton
class ProfileDataCleaner @Inject constructor(
    private val database: WatchListDatabase
) {
    suspend fun deleteAllDataForProfile(profileId: String): Int =
        database.getProfileCleanupDao().deleteAllDataForProfile(profileId)
}
