package zechs.drive.stream.utils

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import zechs.drive.stream.data.local.FavoriteFolder
import zechs.drive.stream.data.local.FollowedFolder
import zechs.drive.stream.data.model.UserProfile
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.model.WatchQueueItem
import zechs.drive.stream.data.repository.FavoriteRepository
import zechs.drive.stream.data.repository.FollowedFolderRepository
import zechs.drive.stream.data.repository.WatchListRepository
import zechs.drive.stream.data.repository.WatchQueueRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FEAT-07: Backup serialization and deserialization for profile data,
 * watch progress, favorites, watch queue, and settings.
 *
 * Guaranteed sanitized: Strictly excludes all credentials, OAuth tokens,
 * client secrets, and device nonces.
 */
@Singleton
class BackupSerializer @Inject constructor(
    private val profileManager: ProfileManager,
    private val watchListRepository: WatchListRepository,
    private val favoriteRepository: FavoriteRepository,
    private val watchQueueRepository: WatchQueueRepository,
    private val followedFolderRepository: FollowedFolderRepository,
    private val appSettings: AppSettings
) {

    companion object {
        const val CURRENT_BACKUP_VERSION = 1
        const val MAX_BACKUP_SIZE_BYTES = 5 * 1024 * 1024 // 5 MB safety limit

        private val SENSITIVE_PATTERNS = listOf(
            "client_secret",
            "refreshToken",
            "refresh_token",
            "access_token",
            "accessToken",
            "authorization",
            "Authorization",
            "Bearer ",
            "AIza",
            "drive.readonly"
        )

        /**
         * Returns true if any prohibited token or credential substring is present in the JSON.
         */
        fun containsSensitiveTokens(json: String): Boolean {
            return SENSITIVE_PATTERNS.any { pattern -> json.contains(pattern, ignoreCase = true) }
        }
    }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    enum class ImportMode {
        REPLACE,
        MERGE
    }

    data class ImportResult(
        val isSuccess: Boolean,
        val importedProfiles: Int = 0,
        val importedWatches: Int = 0,
        val importedFavorites: Int = 0,
        val importedQueue: Int = 0,
        val importedFollowed: Int = 0,
        val errorMessage: String? = null
    )

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    suspend fun exportBackupJson(): String {
        val profiles = profileManager.getProfiles()
        val profileBackups = mutableListOf<UserProfileBackup>()

        for (p in profiles) {
            val theme = appSettings.fetchTheme(p.id).text
            val player = appSettings.fetchPlayer(p.id).text
            val subSize = appSettings.fetchSubtitleSize(p.id)

            val watches = watchListRepository.getAllWatches(p.id).map {
                WatchListItemBackup(
                    name = it.name,
                    videoId = it.videoId,
                    watchedDuration = it.watchedDuration,
                    totalDuration = it.totalDuration,
                    thumbnailLink = it.thumbnailLink
                )
            }

            val favorites = favoriteRepository.getFavoritesSync(p.id).map {
                FavoriteItemBackup(
                    folderId = it.folderId,
                    folderName = it.folderName,
                    addedAt = it.addedAt
                )
            }

            val queue = watchQueueRepository.getQueueSync(p.id).map {
                WatchQueueItemBackup(
                    fileId = it.fileId,
                    name = it.name,
                    posterUrl = it.posterUrl,
                    orderIndex = it.orderIndex,
                    addedAt = it.addedAt
                )
            }

            val followed = followedFolderRepository.getFollowedSync(p.id).map {
                FollowedFolderBackup(
                    folderId = it.folderId,
                    folderName = it.folderName,
                    lastKnownCount = it.lastKnownCount,
                    followedAt = it.followedAt
                )
            }

            profileBackups.add(
                UserProfileBackup(
                    id = p.id,
                    name = p.name,
                    avatarResName = p.avatarResName,
                    isDefault = p.isDefault,
                    libraryRootId = p.libraryRootId,
                    libraryRootName = p.libraryRootName,
                    settings = ProfileSettingsBackup(
                        theme = theme,
                        player = player,
                        subtitleSize = subSize
                    ),
                    watchList = watches,
                    favorites = favorites,
                    watchQueue = queue,
                    followedFolders = followed
                )
            )
        }

        val backup = MakimonoBackup(
            version = CURRENT_BACKUP_VERSION,
            exportedAt = System.currentTimeMillis(),
            profiles = profileBackups
        )

        val json = gson.toJson(backup)
        if (containsSensitiveTokens(json)) {
            throw IllegalStateException("Backup export failed security check: sensitive tokens detected in payload")
        }
        return json
    }

    // -------------------------------------------------------------------------
    // Import
    // -------------------------------------------------------------------------

    suspend fun importBackupJson(jsonString: String, mode: ImportMode = ImportMode.MERGE): ImportResult {
        if (jsonString.length > MAX_BACKUP_SIZE_BYTES) {
            return ImportResult(isSuccess = false, errorMessage = "Tamanho do arquivo excede o limite máximo permitido (5MB)")
        }

        val backup: MakimonoBackup = try {
            gson.fromJson(jsonString, MakimonoBackup::class.java)
        } catch (e: JsonSyntaxException) {
            return ImportResult(isSuccess = false, errorMessage = "Arquivo JSON de backup inválido ou corrompido")
        }

        if (backup.version <= 0 || backup.version > CURRENT_BACKUP_VERSION) {
            return ImportResult(
                isSuccess = false,
                errorMessage = "Versão do backup não suportada: ${backup.version}"
            )
        }

        if (backup.profiles.isEmpty()) {
            return ImportResult(isSuccess = false, errorMessage = "Nenhum perfil encontrado no backup")
        }

        var watchCount = 0
        var favCount = 0
        var queueCount = 0
        var followedCount = 0

        val restoredProfiles = mutableListOf<UserProfile>()

        for (pb in backup.profiles) {
            val userProfile = UserProfile(
                id = pb.id,
                name = pb.name,
                avatarResName = pb.avatarResName,
                isDefault = pb.isDefault,
                libraryRootId = pb.libraryRootId,
                libraryRootName = pb.libraryRootName
            )
            restoredProfiles.add(userProfile)

            // Settings
            appSettings.saveTheme(AppTheme.fromText(pb.settings.theme), pb.id)
            appSettings.savePlayer(VideoPlayer.fromText(pb.settings.player), pb.id)
            appSettings.saveSubtitleSize(pb.settings.subtitleSize, pb.id)

            // WatchList
            for (w in pb.watchList) {
                watchListRepository.insertWatch(
                    WatchList(
                        name = w.name,
                        videoId = w.videoId,
                        watchedDuration = w.watchedDuration,
                        totalDuration = w.totalDuration,
                        thumbnailLink = w.thumbnailLink,
                        profileId = pb.id
                    ),
                    profileId = pb.id
                )
                watchCount++
            }

            // Favorites
            for (f in pb.favorites) {
                favoriteRepository.set(
                    folderId = f.folderId,
                    folderName = f.folderName,
                    isFavorite = true,
                    profileId = pb.id
                )
                favCount++
            }

            // Watch Queue
            for (q in pb.watchQueue) {
                watchQueueRepository.enqueueItem(
                    WatchQueueItem(
                        profileId = pb.id,
                        fileId = q.fileId,
                        name = q.name,
                        posterUrl = q.posterUrl,
                        orderIndex = q.orderIndex,
                        addedAt = q.addedAt
                    )
                )
                queueCount++
            }

            // Followed Folders
            for (ff in pb.followedFolders) {
                followedFolderRepository.follow(
                    folderId = ff.folderId,
                    folderName = ff.folderName,
                    profileId = pb.id
                )
                if (ff.lastKnownCount > 0) {
                    followedFolderRepository.updateLastKnownCount(
                        folderId = ff.folderId,
                        count = ff.lastKnownCount,
                        profileId = pb.id
                    )
                }
                followedCount++
            }
        }

        val finalProfiles = when (mode) {
            ImportMode.REPLACE -> restoredProfiles
            ImportMode.MERGE -> {
                val existing = profileManager.getProfiles().associateBy { it.id }.toMutableMap()
                for (rp in restoredProfiles) {
                    existing[rp.id] = rp
                }
                existing.values.toList()
            }
        }

        profileManager.restoreProfiles(finalProfiles)

        return ImportResult(
            isSuccess = true,
            importedProfiles = backup.profiles.size,
            importedWatches = watchCount,
            importedFavorites = favCount,
            importedQueue = queueCount,
            importedFollowed = followedCount
        )
    }
}

// -----------------------------------------------------------------------------
// DTOs
// -----------------------------------------------------------------------------

@Keep
data class MakimonoBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.4.24",
    val profiles: List<UserProfileBackup> = emptyList()
)

@Keep
data class UserProfileBackup(
    val id: String,
    val name: String,
    val avatarResName: String,
    val isDefault: Boolean,
    val libraryRootId: String? = null,
    val libraryRootName: String? = null,
    val settings: ProfileSettingsBackup = ProfileSettingsBackup(),
    val watchList: List<WatchListItemBackup> = emptyList(),
    val favorites: List<FavoriteItemBackup> = emptyList(),
    val watchQueue: List<WatchQueueItemBackup> = emptyList(),
    val followedFolders: List<FollowedFolderBackup> = emptyList()
)

@Keep
data class ProfileSettingsBackup(
    val theme: String = "kodi_estuary",
    val player: String = "exoplayer",
    val subtitleSize: Float = 16f
)

@Keep
data class WatchListItemBackup(
    val name: String,
    val videoId: String,
    val watchedDuration: Long,
    val totalDuration: Long,
    val thumbnailLink: String? = null
)

@Keep
data class FavoriteItemBackup(
    val folderId: String,
    val folderName: String,
    val addedAt: Long = 0L
)

@Keep
data class WatchQueueItemBackup(
    val fileId: String,
    val name: String,
    val posterUrl: String? = null,
    val orderIndex: Int = 0,
    val addedAt: Long = 0L
)

@Keep
data class FollowedFolderBackup(
    val folderId: String,
    val folderName: String,
    val lastKnownCount: Int = 0,
    val followedAt: Long = 0L
)
