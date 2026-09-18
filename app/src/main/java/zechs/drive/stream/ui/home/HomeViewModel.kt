package zechs.drive.stream.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.WatchListRepository
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.utils.Event
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.state.Resource
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionManager: Lazy<SessionManager>,
    private val driveRepository: Lazy<DriveRepository>,
    private val watchListRepository: WatchListRepository,
    private val folderMetadataRepository: zechs.drive.stream.data.repository.FolderMetadataRepository,
    private val animePosterResolver: zechs.drive.stream.data.remote.AnimePosterResolver,
    private val tenraiAnimeService: zechs.drive.stream.data.remote.TenraiAnimeService,
    private val favoriteRepository: zechs.drive.stream.data.repository.FavoriteRepository
) : ViewModel() {

    companion object {
        const val TAG = "HomeViewModel"
    }

    private val _hasLoggedOut = MutableStateFlow(false)
    val hasLoggedOut = _hasLoggedOut.asStateFlow()

    // Kept for other callers/backwards-compat; Home itself now reads
    // exclusively from `recentWatches` below (see fragment_home.xml /
    // rvContinueWatchingShelf - the single-item hero card that used to read
    // this StateFlow was removed as a duplicate of the shelf's first item).
    private val _lastWatched = MutableStateFlow<WatchList?>(null)
    val lastWatched = _lastWatched.asStateFlow()

    private val _recentWatches = MutableStateFlow<List<WatchList>>(emptyList())
    val recentWatches = _recentWatches.asStateFlow()

    private val _starredFiles = MutableStateFlow<List<DriveFile>>(emptyList())
    val starredFiles = _starredFiles.asStateFlow()

    data class FeaturedAnime(
        val title: String,
        val titleJapanese: String? = null,
        val synopsis: String? = null,
        val genres: List<String> = emptyList(),
        val backdropUrl: String? = null,
        val folderId: String,
        val posterUrl: String? = null
    )

    private val _featuredAnime = MutableStateFlow<FeaturedAnime?>(null)
    val featuredAnime = _featuredAnime.asStateFlow()

    data class FileToken(
        val fileId: String,
        val fileName: String,
        val accessToken: String,
        val thumbnailLink: String? = null
    )

    private val _token = MutableLiveData<Event<Resource<FileToken>>>()
    val mpvFile: LiveData<Event<Resource<FileToken>>>
        get() = _token

    fun getLastWatched() = viewModelScope.launch {
        _lastWatched.value = watchListRepository.getLastWatched()
    }

    fun getRecentWatches(limit: Int = 10) = viewModelScope.launch {
        _recentWatches.value = watchListRepository.getRecentWatches(limit)
    }

    fun removeWatchItem(watchItem: WatchList) = viewModelScope.launch(Dispatchers.IO) {
        try {
            watchListRepository.deleteWatch(watchItem)
            val updated = _recentWatches.value.filter { it.videoId != watchItem.videoId }
            _recentWatches.value = updated
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error removing watch item", e)
        }
    }

    fun markWatchItemFinished(watchItem: WatchList) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val duration = if (watchItem.totalDuration > 0L) watchItem.totalDuration else 24 * 60 * 1000L
            val finished = watchItem.copy(
                watchedDuration = duration,
                totalDuration = duration
            )
            watchListRepository.insertWatch(finished)
            val updated = _recentWatches.value.filter { it.videoId != watchItem.videoId }
            _recentWatches.value = updated
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error marking watch item finished", e)
        }
    }

    fun recordFolderOpened(folderId: String, folderName: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            folderMetadataRepository.recordFolderOpened(folderId, folderName)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error recording folder opened", e)
        }
    }

    private var cachedOneBlackiId: String? = null

    fun getOneBlackiFolder(onResult: (folderId: String?, folderName: String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        if (!cachedOneBlackiId.isNullOrBlank()) {
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onResult(cachedOneBlackiId, "oneblacki")
            }
            return@launch
        }

        try {
            // 1. First priority: Exact match query for folder or shortcut named 'oneblacki'
            val exactResponse = driveRepository.get().getFiles(
                query = "name = 'oneblacki' and (mimeType = 'application/vnd.google-apps.folder' or mimeType = 'application/vnd.google-apps.shortcut') and trashed=false",
                pageToken = null,
                pageSize = 10
            )
            if (exactResponse is Resource.Success && exactResponse.data != null && exactResponse.data.files.isNotEmpty()) {
                val folder = exactResponse.data.files.firstOrNull {
                    it.name.trim().equals("oneblacki", ignoreCase = true)
                } ?: exactResponse.data.files.first()

                val targetId = if (folder.shortcutDetails.targetId != null && folder.shortcutDetails.targetMimeType == "application/vnd.google-apps.folder") {
                    folder.shortcutDetails.targetId
                } else {
                    folder.id
                }
                cachedOneBlackiId = targetId
                Log.d(TAG, "Found exact oneblacki folder: id=$targetId, name=${folder.name}")
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult(targetId, folder.name)
                }
                return@launch
            }

            // 2. Second priority: Query contains 'oneblacki' with strict exclusion of '1oneblacki'
            val response = driveRepository.get().getFiles(
                query = "name contains 'oneblacki' and (mimeType = 'application/vnd.google-apps.folder' or mimeType = 'application/vnd.google-apps.shortcut') and trashed=false",
                pageToken = null,
                pageSize = 50
            )
            if (response is Resource.Success && response.data != null) {
                val files = response.data.files
                val folder = files.firstOrNull {
                    it.name.trim().equals("oneblacki", ignoreCase = true)
                } ?: files.firstOrNull {
                    it.name.trim().equals("one blacki", ignoreCase = true)
                } ?: files.firstOrNull {
                    val n = it.name.trim().lowercase()
                    n.contains("oneblacki") && !n.contains("1oneblacki") && !n.startsWith("1")
                }

                if (folder != null) {
                    val targetId = if (folder.shortcutDetails.targetId != null && folder.shortcutDetails.targetMimeType == "application/vnd.google-apps.folder") {
                        folder.shortcutDetails.targetId
                    } else {
                        folder.id
                    }
                    cachedOneBlackiId = targetId
                    Log.d(TAG, "Found oneblacki folder (filtered): id=$targetId, name=${folder.name}")
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onResult(targetId, folder.name)
                    }
                    return@launch
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error finding oneblacki folder", e)
        }

        kotlinx.coroutines.withContext(Dispatchers.Main) {
            onResult(null, "oneblacki")
        }
    }

    fun getStarredFiles() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val response = driveRepository.get().getAllFiles(
                query = "starred=true and trashed=false",
                pageSize = 25
            )
            val driveStarred = if (response is Resource.Success && response.data != null) {
                response.data.map { it.toDriveFile() }
            } else {
                emptyList()
            }

            // Merge with local favorites (SEC-03)
            val localFavorites = favoriteRepository.getFavoritesSync()
            val localSyntheticFiles = localFavorites
                .filterNot { fav -> driveStarred.any { it.id == fav.folderId } }
                .map { fav ->
                    DriveFile(
                        id = fav.folderId,
                        name = fav.folderName.ifEmpty { "Favorito" },
                        size = null,
                        mimeType = "application/vnd.google-apps.folder",
                        iconLink = null,
                        thumbnailLink = null,
                        shortcutDetails = zechs.drive.stream.data.model.ShortcutDetails(),
                        starred = zechs.drive.stream.data.model.Starred.STARRED
                    )
                }

            val rawFiles = driveStarred + localSyntheticFiles

            if (rawFiles.isNotEmpty()) {
                // 1. Get cached metadata from local Room database
                val allMeta = folderMetadataRepository.getAllMetadata().associateBy { it.folderId }

                // 2. Sort by recency: last opened folders/files appear first (descending timestamp)
                val sortedFiles = rawFiles.sortedWith(
                    compareByDescending<DriveFile> { file ->
                        val targetId = if (file.isShortcut) file.shortcutDetails.targetId ?: file.id else file.id
                        allMeta[targetId]?.lastOpened ?: 0L
                    }
                )

                // 3. Attach cached posters
                val filesWithPosters = sortedFiles.map { file ->
                    val targetId = if (file.isShortcut) file.shortcutDetails.targetId ?: file.id else file.id
                    val cachedPoster = allMeta[targetId]?.posterUrl
                    if (!cachedPoster.isNullOrBlank()) {
                        file.copy(posterUrl = cachedPoster)
                    } else {
                        file
                    }
                }

                _starredFiles.value = filesWithPosters

                // 4. In background, resolve posters for folders missing a cached poster
                resolveMissingPosters(filesWithPosters)
            } else {
                _starredFiles.value = emptyList()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error fetching starred files", e)
        }
    }

    private fun resolveMissingPosters(files: List<DriveFile>) = viewModelScope.launch(Dispatchers.IO) {
        val updatedList = files.toMutableList()

        for (i in updatedList.indices) {
            val file = updatedList[i]
            val isFolder = file.isFolder || file.isShortcutFolder
            if (isFolder && file.posterUrl.isNullOrBlank()) {
                val targetId = if (file.isShortcut) file.shortcutDetails.targetId ?: file.id else file.id
                val poster = animePosterResolver.resolvePoster(file.name)
                if (!poster.isNullOrBlank()) {
                    folderMetadataRepository.updatePosterUrl(targetId, file.name, poster)
                    updatedList[i] = file.copy(posterUrl = poster)
                    // Emit incrementally so user sees covers populate as soon as fetched
                    _starredFiles.value = updatedList.toList()
                }
            }
        }
    }

    fun fetchToken(
        fileId: String,
        fileName: String,
        thumbnailLink: String? = null
    ) = viewModelScope.launch {
        _token.postValue(Event(Resource.Loading()))

        val client = sessionManager.get().fetchClient() ?: run {
            _token.postValue(Event(Resource.Error("Client not found")))
            return@launch
        }
        val tokenResponse = driveRepository.get().fetchAccessToken(client)

        when (tokenResponse) {
            is Resource.Success -> {
                val fileToken = FileToken(
                    fileId = fileId,
                    fileName = fileName,
                    accessToken = tokenResponse.data.accessToken,
                    thumbnailLink = thumbnailLink
                )
                _token.postValue(Event(Resource.Success(fileToken)))
            }
            is Resource.Error -> {
                _token.postValue(
                    Event(Resource.Error(tokenResponse.message ?: "Falha ao obter token"))
                )
            }
            else -> {}
        }
    }

    fun logOut() = viewModelScope.launch {
        delay(250L)
        sessionManager.get().resetDataStore()
        _hasLoggedOut.value = true
    }

    private val _animeLibrary = MutableStateFlow<List<DriveFile>>(emptyList())
    val animeLibrary = _animeLibrary.asStateFlow()

    private val _filteredAnimes = MutableStateFlow<List<DriveFile>>(emptyList())
    val filteredAnimes = _filteredAnimes.asStateFlow()

    private val _isLoadingAnime = MutableStateFlow(false)
    val isLoadingAnime = _isLoadingAnime.asStateFlow()

    fun loadAnimeLibrary(forceRefresh: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        if (forceRefresh) {
            cachedOneBlackiId = null
        }
        _isLoadingAnime.value = true
        getOneBlackiFolder { folderId, folderName ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    if (folderId != null) {
                        Log.d(TAG, "Carregando catálogo de animes da pasta: $folderName ($folderId)")
                        val query = "'$folderId' in parents and trashed=false"
                    val response = driveRepository.get().getAllFiles(
                        query = query,
                        pageSize = 100
                    )

                    if (response is Resource.Success && response.data != null) {
                        val rawFiles = response.data.map { it.toDriveFile() }
                        // Hide non-video / non-folder files (such as folder.ico, .apk, .ini, etc.)
                        val animeFiles = rawFiles.filter { file ->
                            val isMediaOrFolder = file.isFolder || file.isShortcutFolder || file.isVideoFile || file.isShortcutVideo
                            val nameLower = file.name.lowercase()
                            isMediaOrFolder &&
                                    !nameLower.endsWith(".ico") &&
                                    !nameLower.endsWith(".apk") &&
                                    !nameLower.endsWith(".exe") &&
                                    !nameLower.endsWith(".ini") &&
                                    !nameLower.endsWith(".txt") &&
                                    !nameLower.startsWith(".")
                        }
                        val allMeta = folderMetadataRepository.getAllMetadata().associateBy { it.folderId }

                        val mappedFiles = animeFiles.map { file ->
                            val targetId = if (file.isShortcut && file.shortcutDetails.targetId != null) {
                                file.shortcutDetails.targetId
                            } else file.id
                            val meta = allMeta[targetId] ?: allMeta[file.id]
                            if (meta?.posterUrl != null) {
                                file.copy(posterUrl = meta.posterUrl)
                            } else file
                        }

                        _animeLibrary.value = mappedFiles
                        _filteredAnimes.value = mappedFiles
                        _isLoadingAnime.value = false
                        resolveFeaturedSpotlight(mappedFiles)

                        // Asynchronously resolve posters for items missing them
                        val updatedList = mappedFiles.toMutableList()
                        for (i in updatedList.indices) {
                            val file = updatedList[i]
                            if (file.posterUrl.isNullOrBlank() && (file.isFolder || file.isShortcutFolder)) {
                                val poster = animePosterResolver.resolvePoster(file.name)
                                if (!poster.isNullOrBlank()) {
                                    val targetId = if (file.isShortcut && file.shortcutDetails.targetId != null) {
                                        file.shortcutDetails.targetId
                                    } else file.id
                                    folderMetadataRepository.updatePosterUrl(targetId, file.name, poster)
                                    updatedList[i] = file.copy(posterUrl = poster)
                                    _animeLibrary.value = updatedList.toList()
                                    _filteredAnimes.value = updatedList.toList()
                                }
                            }
                        }
                    } else {
                        _isLoadingAnime.value = false
                    }
                } else {
                    Log.w(TAG, "Pasta 'oneblacki' não encontrada no Google Drive.")
                    _isLoadingAnime.value = false
                }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Error loading anime library", e)
                    _isLoadingAnime.value = false
                }
            }
        }
    }

    private fun resolveFeaturedSpotlight(files: List<DriveFile>) = viewModelScope.launch(Dispatchers.IO) {
        val folders = files.filter { it.isFolder || it.isShortcutFolder }
        if (folders.isEmpty()) return@launch

        // Prefer starred anime, or the first folder
        val chosenFolder = folders.firstOrNull { it.starred == zechs.drive.stream.data.model.Starred.STARRED }
            ?: folders.first()

        val cleanTitle = AnimePosterResolver.cleanAnimeTitle(chosenFolder.name)
        val targetId = if (chosenFolder.isShortcut && chosenFolder.shortcutDetails.targetId != null) {
            chosenFolder.shortcutDetails.targetId
        } else chosenFolder.id

        try {
            val aniListMeta = animePosterResolver.resolveMetadata(cleanTitle)
            val results = if (aniListMeta == null) tenraiAnimeService.searchAnime(cleanTitle) else emptyList()
            val entry = results.firstOrNull()

            if (aniListMeta != null) {
                _featuredAnime.value = FeaturedAnime(
                    title = aniListMeta.titleRomaji ?: cleanTitle,
                    titleJapanese = aniListMeta.titleNative,
                    synopsis = aniListMeta.synopsis ?: "Assista a esta incrível série disponível na sua biblioteca.",
                    genres = aniListMeta.genres,
                    backdropUrl = aniListMeta.bannerUrl ?: aniListMeta.posterUrl,
                    folderId = targetId,
                    posterUrl = aniListMeta.posterUrl ?: chosenFolder.posterUrl
                )
            } else if (entry != null) {
                _featuredAnime.value = FeaturedAnime(
                    title = entry.titleEnglish ?: entry.title,
                    titleJapanese = entry.titleJapanese,
                    synopsis = entry.synopsis,
                    genres = entry.genres,
                    backdropUrl = entry.imageUrl,
                    folderId = targetId,
                    posterUrl = chosenFolder.posterUrl ?: entry.imageUrl
                )
            } else {
                _featuredAnime.value = FeaturedAnime(
                    title = chosenFolder.name,
                    titleJapanese = null,
                    synopsis = "Assista a esta incrível série disponível na sua biblioteca.",
                    genres = emptyList(),
                    backdropUrl = chosenFolder.posterUrl,
                    folderId = targetId,
                    posterUrl = chosenFolder.posterUrl
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error resolving featured spotlight", e)
            _featuredAnime.value = FeaturedAnime(
                title = chosenFolder.name,
                titleJapanese = null,
                synopsis = null,
                genres = emptyList(),
                backdropUrl = chosenFolder.posterUrl,
                folderId = targetId,
                posterUrl = chosenFolder.posterUrl
            )
        }
    }

    fun filterAnimes(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            _filteredAnimes.value = _animeLibrary.value
        } else {
            _filteredAnimes.value = _animeLibrary.value.filter {
                it.name.contains(trimmed, ignoreCase = true) ||
                AnimePosterResolver.cleanAnimeTitle(it.name).contains(trimmed, ignoreCase = true)
            }
        }
    }

    fun filterStarred(starredOnly: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        if (starredOnly) {
            val localFavs = favoriteRepository.getFavoritesSync().map { it.folderId }.toSet()
            val filtered = _animeLibrary.value.filter {
                it.starred == zechs.drive.stream.data.model.Starred.STARRED || localFavs.contains(it.id)
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                _filteredAnimes.value = filtered
            }
        } else {
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                _filteredAnimes.value = _animeLibrary.value
            }
        }
    }

    fun getResumeOrFirstEpisode(folder: DriveFile, onResult: (DriveFile?, Long) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val folderId = if (folder.isShortcut && folder.shortcutDetails.targetId != null) {
                folder.shortcutDetails.targetId
            } else folder.id

            val cleanAnimeName = AnimePosterResolver.cleanAnimeTitle(folder.name).trim().lowercase()

            // 1. Check local watch history to see if an episode of this anime was started and can be resumed
            val recentWatches = watchListRepository.getRecentWatches(100)
            val matchedWatch = recentWatches.firstOrNull { watch ->
                val watchNameClean = AnimePosterResolver.cleanAnimeTitle(watch.name).lowercase()
                watchNameClean.contains(cleanAnimeName) || cleanAnimeName.contains(watchNameClean)
            }

            if (matchedWatch != null && !matchedWatch.hasFinished()) {
                Log.d(TAG, "Resuming in-progress episode for ${folder.name}: ${matchedWatch.name} (id: ${matchedWatch.videoId}, pos: ${matchedWatch.watchedDuration})")
                val resumeFile = DriveFile(
                    id = matchedWatch.videoId,
                    name = matchedWatch.name,
                    size = null,
                    mimeType = "video/mp4",
                    iconLink = null,
                    thumbnailLink = matchedWatch.thumbnailLink,
                    shortcutDetails = zechs.drive.stream.data.model.ShortcutDetails(),
                    starred = zechs.drive.stream.data.model.Starred.UNSTARRED
                )
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult(resumeFile, matchedWatch.watchedDuration)
                }
                return@launch
            }

            // 2. Query the anime folder to find video files
            val response = driveRepository.get().getFiles(
                query = "'$folderId' in parents and trashed=false",
                pageToken = null,
                pageSize = 100
            )
            if (response is Resource.Success && response.data != null) {
                val files = response.data.files.map { it.toDriveFile() }
                val videos = files.filter { it.isVideoFile || it.isShortcutVideo }.sortedBy { it.name }

                if (videos.isNotEmpty()) {
                    val watchMap = recentWatches.associateBy { it.videoId }

                    // A: Video currently in progress
                    val inProgressVideo = videos.firstOrNull { v ->
                        val targetId = if (v.isShortcut && v.shortcutDetails.targetId != null) v.shortcutDetails.targetId else v.id
                        val w = watchMap[targetId]
                        w != null && !w.hasFinished()
                    }

                    if (inProgressVideo != null) {
                        val targetId = if (inProgressVideo.isShortcut && inProgressVideo.shortcutDetails.targetId != null) inProgressVideo.shortcutDetails.targetId else inProgressVideo.id
                        val resumeMs = watchMap[targetId]?.watchedDuration ?: 0L
                        val targetVideo = if (inProgressVideo.isShortcut && inProgressVideo.shortcutDetails.targetId != null) {
                            inProgressVideo.copy(id = inProgressVideo.shortcutDetails.targetId)
                        } else inProgressVideo

                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            onResult(targetVideo, resumeMs)
                        }
                        return@launch
                    }

                    // B: If previous episodes were finished, pick the next unwatched episode
                    val lastFinishedIndex = videos.indexOfLast { v ->
                        val targetId = if (v.isShortcut && v.shortcutDetails.targetId != null) v.shortcutDetails.targetId else v.id
                        val w = watchMap[targetId]
                        w != null && w.hasFinished()
                    }

                    val chosenVideo = if (lastFinishedIndex >= 0 && lastFinishedIndex + 1 < videos.size) {
                        videos[lastFinishedIndex + 1]
                    } else {
                        videos.first()
                    }

                    val targetVideo = if (chosenVideo.isShortcut && chosenVideo.shortcutDetails.targetId != null) {
                        chosenVideo.copy(id = chosenVideo.shortcutDetails.targetId)
                    } else chosenVideo

                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onResult(targetVideo, 0L)
                    }
                    return@launch
                }

                // If no video in root, check subfolders (e.g. Season 1)
                val subFolders = files.filter { it.isFolder || it.isShortcutFolder }.sortedBy { it.name }
                for (subFolder in subFolders) {
                    val subFolderId = if (subFolder.isShortcut && subFolder.shortcutDetails.targetId != null) {
                        subFolder.shortcutDetails.targetId
                    } else subFolder.id
                    val subResponse = driveRepository.get().getFiles(
                        query = "'$subFolderId' in parents and trashed=false",
                        pageToken = null,
                        pageSize = 100
                    )
                    if (subResponse is Resource.Success && subResponse.data != null) {
                        val subFiles = subResponse.data.files.map { it.toDriveFile() }
                        val subVideos = subFiles.filter { it.isVideoFile || it.isShortcutVideo }.sortedBy { it.name }
                        if (subVideos.isNotEmpty()) {
                            val watchMap = recentWatches.associateBy { it.videoId }

                            val inProgressSub = subVideos.firstOrNull { v ->
                                val targetId = if (v.isShortcut && v.shortcutDetails.targetId != null) v.shortcutDetails.targetId else v.id
                                val w = watchMap[targetId]
                                w != null && !w.hasFinished()
                            }

                            if (inProgressSub != null) {
                                val targetId = if (inProgressSub.isShortcut && inProgressSub.shortcutDetails.targetId != null) inProgressSub.shortcutDetails.targetId else inProgressSub.id
                                val resumeMs = watchMap[targetId]?.watchedDuration ?: 0L
                                val targetVideo = if (inProgressSub.isShortcut && inProgressSub.shortcutDetails.targetId != null) {
                                    inProgressSub.copy(id = inProgressSub.shortcutDetails.targetId)
                                } else inProgressSub

                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    onResult(targetVideo, resumeMs)
                                }
                                return@launch
                            }

                            val lastFinishedIndex = subVideos.indexOfLast { v ->
                                val targetId = if (v.isShortcut && v.shortcutDetails.targetId != null) v.shortcutDetails.targetId else v.id
                                val w = watchMap[targetId]
                                w != null && w.hasFinished()
                            }

                            val chosenSub = if (lastFinishedIndex >= 0 && lastFinishedIndex + 1 < subVideos.size) {
                                subVideos[lastFinishedIndex + 1]
                            } else {
                                subVideos.first()
                            }

                            val targetVideo = if (chosenSub.isShortcut && chosenSub.shortcutDetails.targetId != null) {
                                chosenSub.copy(id = chosenSub.shortcutDetails.targetId)
                            } else chosenSub

                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                onResult(targetVideo, 0L)
                            }
                            return@launch
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error resolving episode to play", e)
        }
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            onResult(null, 0L)
        }
    }

}
