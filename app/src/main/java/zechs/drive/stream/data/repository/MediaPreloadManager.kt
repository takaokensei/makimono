package zechs.drive.stream.data.repository

import android.content.Context
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import zechs.drive.stream.data.local.CatalogEntry
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.data.remote.EpisodeMetadataResolver
import zechs.drive.stream.utils.AppLog
import zechs.drive.stream.utils.MediaImageLoader
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.state.Resource
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates parallel background preloading and cache warming for:
 * 1. Series/Movie metadata (synopsis, score, genres, year, age rating) in Room.
 * 2. High-resolution poster and backdrop images in Glide cache.
 * 3. Episode files in CatalogRepository.
 * 4. Episode metadata and synopses in EpisodeMetadataResolver.
 * 5. Episode thumbnail cards in Glide cache.
 *
 * Implements a strict cache-first policy: once saved in Room, metadata is never
 * re-requested across the network.
 */
@Singleton
class MediaPreloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveRepository: Lazy<DriveRepository>,
    private val folderMetadataRepository: FolderMetadataRepository,
    private val animePosterResolver: AnimePosterResolver,
    private val episodeMetadataResolver: EpisodeMetadataResolver,
    private val catalogRepository: CatalogRepository,
    private val watchListRepository: WatchListRepository,
    private val franchiseGroupingManager: FranchiseGroupingManager,
    private val profileManager: ProfileManager
) {

    companion object {
        private const val TAG = "MediaPreloadManager"
        private const val METADATA_CONCURRENCY = 6
        private const val EPISODE_CONCURRENCY = 4
    }

    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preloadedMetadataFolders = ConcurrentHashMap<String, Boolean>()
    private val preloadedEpisodeFolders = ConcurrentHashMap<String, Boolean>()
    private val metadataFoldersInFlight = ConcurrentHashMap<String, Boolean>()
    private val episodeFoldersInFlight = ConcurrentHashMap<String, Boolean>()

    fun startPreload(libraryFiles: List<DriveFile>? = null) {
        preloadScope.launch {
            try {
                preloadInternal(libraryFiles)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                AppLog.e(TAG, "Error in background preload pipeline", e)
            }
        }
    }

    internal suspend fun preloadInternal(libraryFiles: List<DriveFile>?) {
        val files = if (!libraryFiles.isNullOrEmpty()) {
            libraryFiles
        } else {
            resolveLibraryFiles()
        }

        if (files.isEmpty()) {
            AppLog.d(TAG, "No library files available to preload.")
            return
        }

        AppLog.i(TAG, "Starting parallel background preload for ${files.size} library items...")

        // Phase 1: Parallel Bounded Metadata Preload (All items)
        preloadMetadataParallel(files)

        // Phase 2: Parallel Bounded Episodes, Synopses, and Thumbnails Preload (Priority series)
        preloadPriorityEpisodesAndThumbnails(files)

        AppLog.i(TAG, "Background preloading completed.")
    }

    private suspend fun resolveLibraryFiles(): List<DriveFile> {
        val (rootId, _) = profileManager.getLibraryRoot()
        if (rootId.isNullOrBlank()) return emptyList()

        val cachedChildren = catalogRepository.getChildren(rootId)
        if (cachedChildren.isNotEmpty()) {
            return cachedChildren.map { entry ->
                DriveFile(
                    id = entry.id,
                    name = entry.name,
                    size = null,
                    mimeType = entry.mimeType,
                    iconLink = null,
                    thumbnailLink = entry.posterUrl,
                    posterUrl = entry.posterUrl,
                    shortcutDetails = zechs.drive.stream.data.model.ShortcutDetails(),
                    starred = zechs.drive.stream.data.model.Starred.UNSTARRED
                )
            }
        }

        // Fallback: query Drive if offline catalog is not yet populated
        val res = driveRepository.get().getAllFilesDetailed(
            query = "'$rootId' in parents and trashed=false",
            pageSize = 100
        )
        val listing = when (res) {
            is Resource.Success -> res.data.files.map { it.toDriveFile() }
            is Resource.Error -> res.data?.files?.map { it.toDriveFile() }
            is Resource.Loading -> null
        }
        return listing.orEmpty()
    }

    internal suspend fun preloadMetadataParallel(files: List<DriveFile>) {
        val mediaItems = files.filter { file ->
            val isMediaOrFolder = file.isFolder || file.isShortcutFolder || file.isVideoFile || file.isShortcutVideo
            val lower = file.name.lowercase()
            isMediaOrFolder &&
                !lower.endsWith(".ico") &&
                !lower.endsWith(".apk") &&
                !lower.endsWith(".ini") &&
                !lower.endsWith(".exe") &&
                !lower.endsWith(".txt")
        }

        val allRoomMeta = folderMetadataRepository.getAllMetadata().associateBy { it.folderId }
        val semaphore = Semaphore(METADATA_CONCURRENCY)

        coroutineScope {
            mediaItems.map { file ->
                async {
                    semaphore.withPermit {
                        val targetId = if (file.isShortcut) {
                            file.shortcutDetails.targetId ?: file.id
                        } else file.id

                        if (preloadedMetadataFolders.containsKey(targetId) ||
                            metadataFoldersInFlight.putIfAbsent(targetId, true) != null
                        ) return@withPermit

                        try {
                            val existing = allRoomMeta[targetId] ?: folderMetadataRepository.getMetadata(targetId)
                            // Strict Cache-First Check:
                            if (existing != null && !existing.synopsis.isNullOrBlank() && existing.score != null) {
                                // Warm Glide cache for already cached images
                                MediaImageLoader.preloadPoster(context, existing.posterUrl)
                                MediaImageLoader.preloadBackdrop(context, existing.bannerUrl)
                                preloadedMetadataFolders[targetId] = true
                                return@withPermit
                            }

                            // Otherwise resolve from AniList/Cinemeta
                            try {
                                val cleanName = AnimePosterResolver.cleanAnimeTitle(file.name)
                                if (!cleanName.lowercase().contains("download")) {
                                    val resolved = animePosterResolver.resolveMetadata(file.name)
                                    if (resolved != null && !resolved.posterUrl.isNullOrBlank()) {
                                        folderMetadataRepository.saveAnimeMetadata(targetId, file.name, resolved)
                                        MediaImageLoader.preloadPoster(context, resolved.posterUrl)
                                        MediaImageLoader.preloadBackdrop(context, resolved.bannerUrl)
                                    }
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                AppLog.w(TAG, "Failed to resolve metadata for '${file.name}': ${e.message}")
                            }

                            // Preload franchise members if any
                            val hub = franchiseGroupingManager.getFranchiseHubForFolder(targetId)
                            if (hub != null) {
                                for (member in hub.members) {
                                    val memberMeta = folderMetadataRepository.getMetadata(member.folderId)
                                    if (memberMeta == null || memberMeta.score == null) {
                                        try {
                                            val mResolved = animePosterResolver.resolveMetadata(member.arcTitle)
                                            if (mResolved != null) {
                                                folderMetadataRepository.saveAnimeMetadata(member.folderId, member.arcTitle, mResolved)
                                                MediaImageLoader.preloadPoster(context, mResolved.posterUrl)
                                                MediaImageLoader.preloadBackdrop(context, mResolved.bannerUrl)
                                            }
                                        } catch (e: Exception) {
                                            if (e is CancellationException) throw e
                                        }
                                    }
                                }
                            }

                            preloadedMetadataFolders[targetId] = true
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            AppLog.w(TAG, "Failed metadata preload for '${file.name}': ${e.message}")
                        } finally {
                            metadataFoldersInFlight.remove(targetId)
                        }
                    }
                }
            }.awaitAll()
        }
    }

    internal suspend fun preloadPriorityEpisodesAndThumbnails(files: List<DriveFile>) {
        val priorityFolders = mutableListOf<Pair<String, String>>()

        // 1. Continue Watching items
        try {
            val recents = watchListRepository.getRecentWatches(10)
            for (watch in recents) {
                val matched = files.firstOrNull { it.id == watch.videoId || it.name.equals(watch.name, true) }
                if (matched != null && (matched.isFolder || matched.isShortcutFolder)) {
                    val targetId = if (matched.isShortcut) matched.shortcutDetails.targetId ?: matched.id else matched.id
                    priorityFolders.add(targetId to matched.name)
                }
            }
        } catch (_: Exception) {}

        // 2. Top items in the catalog (first 10 folders/series)
        files.filter { it.isFolder || it.isShortcutFolder }.take(10).forEach { folder ->
            val targetId = if (folder.isShortcut) folder.shortcutDetails.targetId ?: folder.id else folder.id
            priorityFolders.add(targetId to folder.name)
        }

        val distinctPriority = priorityFolders.distinctBy { it.first }
        if (distinctPriority.isEmpty()) return

        val semaphore = Semaphore(EPISODE_CONCURRENCY)

        coroutineScope {
            distinctPriority.map { (folderId, folderName) ->
                async {
                    semaphore.withPermit {
                        if (preloadedEpisodeFolders.containsKey(folderId) ||
                            episodeFoldersInFlight.putIfAbsent(folderId, true) != null
                        ) return@withPermit
                        try {
                            preloadSingleSeriesEpisodesAndThumbnails(folderId, folderName)
                            preloadedEpisodeFolders[folderId] = true
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            AppLog.w(TAG, "Failed episode preload for '$folderName': ${e.message}")
                        } finally {
                            episodeFoldersInFlight.remove(folderId)
                        }
                    }
                }
            }.awaitAll()
        }
    }

    internal suspend fun preloadSingleSeriesEpisodesAndThumbnails(folderId: String, folderName: String) {
        // 1. Check if folder's video files are already cached in catalogRepository
        val cachedEntries = catalogRepository.getChildren(folderId)
        if (cachedEntries.isEmpty()) {
            val driveRes = driveRepository.get().getAllFilesDetailed(
                query = "'$folderId' in parents and trashed=false",
                pageSize = 100
            )
            val directCollection = when (driveRes) {
                is Resource.Success -> driveRes.data
                is Resource.Error -> driveRes.data
                is Resource.Loading -> null
            }
            if (directCollection != null && directCollection.files.isNotEmpty()) {
                val entries = directCollection.files.map { file ->
                    CatalogEntry(
                        id = file.id,
                        name = file.name,
                        mimeType = file.mimeType,
                        parentId = folderId,
                        posterUrl = file.thumbnailLink
                    )
                }
                catalogRepository.cacheFolderContents(folderId, entries)
            }
        }

        // 2. Fetch TVmaze episodes for this show title
        val cleanTitle = AnimePosterResolver.cleanAnimeTitle(folderName)
        val episodes = episodeMetadataResolver.fetchShowEpisodes(cleanTitle)

        // 3. Preload all episode thumbnail cards into Glide cache
        for (ep in episodes) {
            val thumbUrl = ep.previewImageUrl
            if (!thumbUrl.isNullOrBlank()) {
                MediaImageLoader.preloadCard(context, thumbUrl)
            }
        }
    }
}
