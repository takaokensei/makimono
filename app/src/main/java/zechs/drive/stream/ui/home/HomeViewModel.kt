package zechs.drive.stream.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import zechs.drive.stream.domain.common.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import zechs.drive.stream.data.local.CatalogEntry
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.model.WatchQueueItem
import zechs.drive.stream.data.local.FollowedFolder
import zechs.drive.stream.data.repository.CatalogRepository
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.WatchListRepository
import zechs.drive.stream.data.repository.WatchQueueRepository
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.utils.Event
import zechs.drive.stream.utils.ProfileManager
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.state.Resource
import javax.inject.Inject

internal fun mergeCatalogSnapshot(
    refreshedFiles: List<DriveFile>,
    cachedFiles: List<DriveFile>,
    isComplete: Boolean
): List<DriveFile> {
    if (isComplete) return refreshedFiles

    val refreshedIds = refreshedFiles.mapTo(mutableSetOf()) { it.id }
    return (refreshedFiles + cachedFiles.filterNot { it.id in refreshedIds })
        .distinctBy { it.id }
}

/**
 * The configured library folder is a container, never a catalog item. Drive
 * shortcuts can point back at that same container, so compare both file and
 * shortcut target ids before promoting entries to a title or hero.
 */
internal fun excludeLibraryRoot(
    files: List<DriveFile>,
    libraryRootId: String
): List<DriveFile> = files.filterNot { file ->
    file.id == libraryRootId || file.shortcutDetails.targetId == libraryRootId
}

fun filterStarredLibraryFolders(
    starredFiles: List<DriveFile>,
    libraryFiles: List<DriveFile>
): List<DriveFile> {
    val starredIds = starredFiles.flatMap { file ->
        listOfNotNull(file.id, file.shortcutDetails.targetId)
    }.toSet()
    return libraryFiles
        .filter { file ->
            (file.isFolder || file.isShortcutFolder) &&
                (file.id in starredIds || file.shortcutDetails.targetId?.let(starredIds::contains) == true)
        }
        .distinctBy { it.shortcutDetails.targetId ?: it.id }
}

/** Returns only series folders with a Drive timestamp, newest first. */
internal fun recentlyModifiedLibraryFolders(
    files: List<DriveFile>,
    limit: Int = 20
): List<DriveFile> = files
    .asSequence()
    .filter { it.isFolder || it.isShortcutFolder }
    .filter { !it.modifiedTime.isNullOrBlank() }
    .sortedByDescending { it.modifiedTime }
    .take(limit.coerceAtLeast(0))
    .toList()

internal fun filterFollowedLibraryFolders(
    followedFolders: List<FollowedFolder>,
    libraryFiles: List<DriveFile>
): List<DriveFile> {
    val byId = buildMap {
        libraryFiles.forEach { file ->
            put(file.id, file)
            file.shortcutDetails.targetId?.let { put(it, file) }
        }
    }
    return followedFolders
        .sortedByDescending { it.followedAt }
        .mapNotNull { byId[it.folderId] }
        .filter { it.isFolder || it.isShortcutFolder }
        .distinctBy { it.shortcutDetails.targetId ?: it.id }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessionManager: Lazy<SessionManager>,
    private val driveRepository: Lazy<DriveRepository>,
    private val watchListRepository: WatchListRepository,
    private val folderMetadataRepository: zechs.drive.stream.data.repository.FolderMetadataRepository,
    private val animePosterResolver: zechs.drive.stream.data.remote.AnimePosterResolver,
    private val tenraiAnimeService: zechs.drive.stream.data.remote.TenraiAnimeService,
    private val favoriteRepository: zechs.drive.stream.data.repository.FavoriteRepository,
    private val catalogRepository: CatalogRepository,
    private val watchQueueRepository: WatchQueueRepository,
    private val profileManager: ProfileManager
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

    private val _watchHistory = MutableStateFlow<List<WatchList>>(emptyList())
    val watchHistory = _watchHistory.asStateFlow()

    private val _starredFiles = MutableStateFlow<List<DriveFile>>(emptyList())
    val starredFiles = _starredFiles.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchNotice = MutableStateFlow<String?>(null)
    val searchNotice: StateFlow<String?> = _searchNotice.asStateFlow()

    private var searchJob: Job? = null

    val watchQueue: StateFlow<List<WatchQueueItem>> =
        watchQueueRepository.observeQueue()
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    private val _animeLibrary = MutableStateFlow<List<DriveFile>>(emptyList())
    val animeLibrary = _animeLibrary.asStateFlow()

    private val _filteredAnimes = MutableStateFlow<List<DriveFile>>(emptyList())
    val filteredAnimes = _filteredAnimes.asStateFlow()

    private val _isLoadingAnime = MutableStateFlow(false)
    val isLoadingAnime = _isLoadingAnime.asStateFlow()

    sealed class HomeState {
        object Loading : HomeState()
        object Content : HomeState()
        data class Empty(val message: String) : HomeState()
        data class Error(val message: String, val isOffline: Boolean = false, val isAuthError: Boolean = false) : HomeState()
    }

    private val _homeState = MutableStateFlow<HomeState>(HomeState.Loading)
    val homeState = _homeState.asStateFlow()

    private val _libraryLoadError = MutableStateFlow<String?>(null)
    val libraryLoadError = _libraryLoadError.asStateFlow()

    data class FeaturedAnime(
        val title: String,
        val titleJapanese: String? = null,
        val synopsis: String? = null,
        val genres: List<String> = emptyList(),
        val backdropUrl: String? = null,
        val folderId: String,
        val posterUrl: String? = null,
        val year: Int? = null,
        val episodeCount: Int? = null,
        val score: Float? = null,
        val ageRating: String? = null,
        val isFocusedPreview: Boolean = false,
        val clearLogoUrl: String? = null
    )

    private val _featuredAnime = MutableStateFlow<FeaturedAnime?>(null)
    val featuredAnime = _featuredAnime.asStateFlow()
    private var featuredSpotlightJob: Job? = null
    private var featuredRequestId = 0L
    private var focusedFeaturedFolderId: String? = null
    @Volatile private var cachedFolderMetaMap: Map<String, zechs.drive.stream.data.local.FolderMetadata> = emptyMap()

    var lastFocusedShelfIndex: Int? = null
        private set
    var lastFocusedItemIndex: Int? = null
        private set

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading(isFirstLoad = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private fun updateUnifiedUiState() {
        val currentHomeState = _homeState.value
        val currentLibrary = _animeLibrary.value
        val currentFiltered = _filteredAnimes.value
        val currentRecent = _recentWatches.value
        val currentHistory = _watchHistory.value
        val currentStarred = _starredFiles.value
        val currentQueue = watchQueue.value
        val currentFeatured = _featuredAnime.value

        _uiState.value = when (currentHomeState) {
            is HomeState.Loading -> {
                if (currentLibrary.isNotEmpty() || currentRecent.isNotEmpty() || currentQueue.isNotEmpty()) {
                    HomeUiState.Content(
                        featuredAnime = currentFeatured,
                        continueWatching = currentRecent,
                        watchHistory = currentHistory,
                        starredFiles = currentStarred,
                        libraryFiles = currentLibrary,
                        filteredAnimes = currentFiltered,
                        watchQueue = currentQueue,
                        lastFocusedShelfIndex = lastFocusedShelfIndex,
                        lastFocusedItemIndex = lastFocusedItemIndex
                    )
                } else {
                    HomeUiState.Loading(isFirstLoad = currentLibrary.isEmpty())
                }
            }
            is HomeState.Empty -> {
                HomeUiState.Empty(currentHomeState.message)
            }
            is HomeState.Error -> {
                val appError = when {
                    currentHomeState.isOffline -> AppError.Network(currentHomeState.message)
                    currentHomeState.isAuthError -> AppError.Authentication(currentHomeState.message)
                    else -> AppError.Server(currentHomeState.message)
                }
                HomeUiState.Error(
                    error = appError,
                    message = currentHomeState.message
                )
            }
            is HomeState.Content -> {
                if (currentLibrary.isEmpty() && currentRecent.isEmpty() && currentQueue.isEmpty() && currentStarred.isEmpty()) {
                    HomeUiState.Empty("Nenhum anime ou pasta encontrado na sua biblioteca.")
                } else {
                    HomeUiState.Content(
                        featuredAnime = currentFeatured,
                        continueWatching = currentRecent,
                        watchHistory = currentHistory,
                        starredFiles = currentStarred,
                        libraryFiles = currentLibrary,
                        filteredAnimes = currentFiltered,
                        watchQueue = currentQueue,
                        lastFocusedShelfIndex = lastFocusedShelfIndex,
                        lastFocusedItemIndex = lastFocusedItemIndex
                    )
                }
            }
        }
    }

    init {
        viewModelScope.launch {
            _homeState.collect { updateUnifiedUiState() }
        }
        viewModelScope.launch {
            _animeLibrary.collect { updateUnifiedUiState() }
        }
        viewModelScope.launch {
            _filteredAnimes.collect { updateUnifiedUiState() }
        }
        viewModelScope.launch {
            _recentWatches.collect { updateUnifiedUiState() }
        }
        viewModelScope.launch {
            _watchHistory.collect { updateUnifiedUiState() }
        }
        viewModelScope.launch {
            _starredFiles.collect { updateUnifiedUiState() }
        }
        viewModelScope.launch {
            _featuredAnime.collect { updateUnifiedUiState() }
        }
        viewModelScope.launch {
            watchQueue.collect { updateUnifiedUiState() }
        }
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.SaveFocus -> {
                lastFocusedShelfIndex = event.shelfIndex
                lastFocusedItemIndex = event.itemIndex
                val current = _uiState.value
                if (current is HomeUiState.Content) {
                    _uiState.value = current.copy(
                        lastFocusedShelfIndex = event.shelfIndex,
                        lastFocusedItemIndex = event.itemIndex
                    )
                }
            }
            is HomeEvent.Search -> {
                filterAnimes(event.query)
            }
            is HomeEvent.Refresh -> {
                loadAnimeLibrary(forceRefresh = true)
                getStarredFiles()
                getRecentWatches()
                getWatchHistory()
            }
            is HomeEvent.RemoveWatchItem -> {
                removeWatchItem(event.item)
            }
            is HomeEvent.RestoreWatchItem -> {
                restoreWatchItem(event.item)
            }
            is HomeEvent.MarkWatchItemFinished -> {
                markWatchItemFinished(event.item)
            }
            is HomeEvent.FocusFeatured -> {
                val file = _animeLibrary.value.firstOrNull {
                    it.id == event.folderId || it.shortcutDetails.targetId == event.folderId
                }
                if (file != null) {
                    previewFeatured(file)
                }
            }
        }
    }

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

    fun getWatchHistory(limit: Int = 100) = viewModelScope.launch {
        _watchHistory.value = watchListRepository.getAllWatches()
            .sortedByDescending { it.id ?: 0 }
            .take(limit)
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

    fun restoreWatchItem(watchItem: WatchList) = viewModelScope.launch(Dispatchers.IO) {
        try {
            watchListRepository.insertWatch(watchItem)
            _recentWatches.value = watchListRepository.getRecentWatches()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error restoring watch item", e)
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

    private var cachedLibraryRootId: String? = null
    private var cachedLibraryRootProfileId: String? = null

    fun getLibraryRootFolder(onResult: (folderId: String?, folderName: String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val activeProfileId = profileManager.getActiveProfile().id
        if (!cachedLibraryRootId.isNullOrBlank() && cachedLibraryRootProfileId == activeProfileId) {
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onResult(cachedLibraryRootId, profileManager.getLibraryRoot().second ?: "Biblioteca")
            }
            return@launch
        }

        try {
            val (configuredId, configuredName) = profileManager.getLibraryRoot()
            if (!configuredId.isNullOrBlank()) {
                cachedLibraryRootId = configuredId
                cachedLibraryRootProfileId = activeProfileId
                withContext(Dispatchers.Main) {
                    onResult(configuredId, configuredName ?: "Biblioteca")
                }
                return@launch
            }

            if (!configuredName.isNullOrBlank()) {
                val escapedName = configuredName.replace("'", "\\'")
                val response = driveRepository.get().getAllFilesDetailed(
                    query = "name = '$escapedName' and (mimeType = 'application/vnd.google-apps.folder' or mimeType = 'application/vnd.google-apps.shortcut') and trashed=false",
                    pageSize = 25
                )
                val listing = when (response) {
                    is Resource.Success -> response.data
                    is Resource.Error -> response.data
                    is Resource.Loading -> null
                }
                val folder = listing?.files?.firstOrNull()
                if (folder != null) {
                    val resolvedId = if (folder.shortcutDetails.targetId != null) {
                        folder.shortcutDetails.targetId
                    } else {
                        folder.id
                    }
                    cachedLibraryRootId = resolvedId
                    cachedLibraryRootProfileId = activeProfileId
                    withContext(Dispatchers.Main) { onResult(resolvedId, folder.name) }
                    return@launch
                }
                val error = (response as? Resource.Error)?.message
                Log.w(TAG, error ?: "Configured library folder not found: $configuredName")
                // An explicitly configured folder must never silently widen the library
                // to the entire Drive when lookup fails or returns an incomplete page.
                withContext(Dispatchers.Main) { onResult(null, configuredName) }
                return@launch
            }

            // An unset profile uses Drive root. The user can choose a narrower
            // folder from Settings without recompiling the app.
            cachedLibraryRootId = "root"
            cachedLibraryRootProfileId = activeProfileId
            withContext(Dispatchers.Main) { onResult("root", "Meu Drive") }
            return@launch
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error resolving configured library root", e)
        }

        kotlinx.coroutines.withContext(Dispatchers.Main) {
            onResult(null, "Biblioteca")
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
        val targets = updatedList.indices.take(60).filter { i ->
            val file = updatedList[i]
            val isFolder = file.isFolder || file.isShortcutFolder
            isFolder && file.posterUrl.isNullOrBlank()
        }

        targets.chunked(6).forEach { chunk ->
            coroutineScope {
                chunk.map { index ->
                    async {
                        val file = updatedList[index]
                        val targetId = if (file.isShortcut) file.shortcutDetails.targetId ?: file.id else file.id
                        val poster = animePosterResolver.resolvePoster(file.name)
                        if (!poster.isNullOrBlank()) {
                            folderMetadataRepository.updatePosterUrl(targetId, file.name, poster)
                            index to poster
                        } else null
                    }
                }.awaitAll()
            }.filterNotNull().forEach { (index, poster) ->
                updatedList[index] = updatedList[index].copy(posterUrl = poster)
            }
            _starredFiles.value = updatedList.toList()
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



    fun loadAnimeLibrary(forceRefresh: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        if (forceRefresh) {
            cachedLibraryRootId = null
            cachedLibraryRootProfileId = null
        }
        _isLoadingAnime.value = true
        _libraryLoadError.value = null
        _homeState.value = HomeState.Loading
        _featuredAnime.value = null
        getLibraryRootFolder { folderId, folderName ->
            viewModelScope.launch(Dispatchers.IO) loadLibrary@{
                try {
                    if (folderId != null) {
                        Log.d(TAG, "Carregando catálogo de animes da pasta: $folderName ($folderId)")

                        // Offline-first: if local catalog cache has children, present them immediately
                        val cachedChildren = catalogRepository.getChildren(folderId)
                        val cachedDriveFiles = excludeLibraryRoot(cachedChildren.map { entry ->
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
                        }, folderId)
                        if (cachedChildren.isNotEmpty() && _animeLibrary.value.isEmpty()) {
                            _animeLibrary.value = cachedDriveFiles
                            _filteredAnimes.value = cachedDriveFiles
                            resolveFeaturedSpotlight(cachedDriveFiles)
                        }

                        val query = "'$folderId' in parents and trashed=false"
                        val response = driveRepository.get().getAllFilesDetailed(
                            query = query,
                            pageSize = 100
                        )
                        val listing = when (response) {
                            is Resource.Success -> response.data
                            is Resource.Error -> response.data
                            is Resource.Loading -> null
                        }

                        if (listing == null || (response is Resource.Error && listing.files.isEmpty())) {
                            val message = (response as? Resource.Error)?.message
                                ?: "Não foi possível carregar a biblioteca."
                            _libraryLoadError.value = message
                            _isLoadingAnime.value = false
                            val isOffline = message.contains("network", ignoreCase = true)
                            val isAuthError = message.contains("401", ignoreCase = true) ||
                                message.contains("403", ignoreCase = true)
                            _homeState.value = HomeState.Error(
                                message = message,
                                isOffline = isOffline,
                                isAuthError = isAuthError
                            )
                            return@loadLibrary
                        }

                        val listingWarning = if (listing.isComplete) null else when (listing.incompleteReason) {
                            zechs.drive.stream.data.model.FileCollectionResult.IncompleteReason.PAGE_LIMIT ->
                                "Catálogo parcial: o limite de páginas do Drive foi atingido."
                            zechs.drive.stream.data.model.FileCollectionResult.IncompleteReason.FILE_LIMIT ->
                                "Catálogo parcial: o limite de arquivos do Drive foi atingido."
                            else -> "Catálogo parcial: não foi possível carregar todos os itens do Drive."
                        }
                        val rawFiles = excludeLibraryRoot(
                            listing.files.map { it.toDriveFile() },
                            folderId
                        )
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
                            cachedFolderMetaMap = allMeta

                            val mappedFiles = animeFiles.map { file ->
                                val targetId = if (file.isShortcut && file.shortcutDetails.targetId != null) {
                                    file.shortcutDetails.targetId
                                } else file.id
                                val meta = allMeta[targetId] ?: allMeta[file.id]
                                if (meta != null) {
                                    file.copy(
                                        posterUrl = meta.posterUrl ?: file.posterUrl,
                                        isMovie = meta.isMovie
                                    )
                                } else file
                            }

                        // A partial network result must not erase items from the last complete
                        // offline catalog. Fresh Drive values win when an id exists in both.
                        val displayFiles = mergeCatalogSnapshot(
                            refreshedFiles = mappedFiles,
                            cachedFiles = cachedDriveFiles,
                            isComplete = listing.isComplete
                        )

                            _animeLibrary.value = displayFiles
                            _filteredAnimes.value = displayFiles
                            _isLoadingAnime.value = false
                            resolveFeaturedSpotlight(displayFiles)

                            // Never replace a complete offline snapshot with a truncated response.
                            if (listing.isComplete) {
                                catalogRepository.cacheFolderContents(
                                    parentId = folderId,
                                    entries = mappedFiles.map {
                                        CatalogEntry(
                                            id = it.id,
                                            name = it.name,
                                            mimeType = it.mimeType,
                                            parentId = folderId,
                                            posterUrl = it.posterUrl
                                        )
                                    }
                                )
                            }

                            // Resolve posters and metadata concurrently in batches of 6
                            val updatedList = displayFiles.toMutableList()
                            val unresolvedIndices = updatedList.indices.take(120).filter { i ->
                                val file = updatedList[i]
                                val targetId = if (file.isShortcut && file.shortcutDetails.targetId != null) {
                                    file.shortcutDetails.targetId
                                } else file.id
                                val meta = allMeta[targetId] ?: allMeta[file.id]
                                (file.isFolder || file.isShortcutFolder) && (meta?.posterUrl.isNullOrBlank() || meta?.bannerUrl.isNullOrBlank())
                            }

                            unresolvedIndices.chunked(6).forEach { chunk ->
                                coroutineScope {
                                    chunk.map { index ->
                                        async {
                                            val file = updatedList[index]
                                            val meta = animePosterResolver.resolveMetadata(file.name)
                                            val poster = meta?.posterUrl
                                            if (meta != null && !poster.isNullOrBlank()) {
                                                val targetId = if (file.isShortcut && file.shortcutDetails.targetId != null) {
                                                    file.shortcutDetails.targetId
                                                } else file.id
                                                folderMetadataRepository.saveAnimeMetadata(
                                                    folderId = targetId,
                                                    folderName = file.name,
                                                    metadata = meta
                                                )
                                                Triple(index, poster, meta.isMovie)
                                            } else null
                                        }
                                    }.awaitAll()
                                }.filterNotNull().forEach { (index, poster, isMovie) ->
                                    updatedList[index] = updatedList[index].copy(posterUrl = poster, isMovie = isMovie)
                                }
                                _animeLibrary.value = updatedList.toList()
                                applyCurrentFilter()
                            }
                            _homeState.value = HomeState.Content
                            if (listingWarning != null) _libraryLoadError.value = listingWarning
                } else {
                    Log.w(TAG, "Pasta de biblioteca não encontrada no Google Drive.")
                    _isLoadingAnime.value = false
                    _homeState.value = HomeState.Empty("Nenhum anime encontrado na biblioteca.")
                }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e(TAG, "Error loading anime library", e)
                    _libraryLoadError.value = "Erro ao sincronizar com o Google Drive. Verifique a conexão e tente novamente."
                    _isLoadingAnime.value = false
                    val isOffline = e.message?.contains("network", ignoreCase = true) == true
                    _homeState.value = HomeState.Error(
                        message = "Erro ao sincronizar com o Google Drive. Verifique a conexão e tente novamente.",
                        isOffline = isOffline
                    )
                }
            }
        }
    }

    fun previewFeatured(file: DriveFile) {
        val folder = if (file.isFolder || file.isShortcutFolder) file else return
        val targetId = folder.shortcutDetails.targetId ?: folder.id
        if (focusedFeaturedFolderId == targetId) return
        focusedFeaturedFolderId = targetId
        resolveFeaturedSpotlight(listOf(folder), isFocusedPreview = true)
    }

    private fun resolveFeaturedSpotlight(
        files: List<DriveFile>,
        isFocusedPreview: Boolean = false
    ) {
        if (!isFocusedPreview && focusedFeaturedFolderId != null) return
        val folders = files.filter { it.isFolder || it.isShortcutFolder }
        if (folders.isEmpty()) return

        // Prefer starred anime, or the first folder
        val chosenFolder = folders.firstOrNull { it.starred == zechs.drive.stream.data.model.Starred.STARRED }
            ?: folders.first()

        val cleanTitle = AnimePosterResolver.cleanAnimeTitle(chosenFolder.name)
        val targetId = if (chosenFolder.isShortcut && chosenFolder.shortcutDetails.targetId != null) {
            chosenFolder.shortcutDetails.targetId
        } else chosenFolder.id
        val fallbackArtUrl = chosenFolder.posterUrl ?: chosenFolder.thumbnailLink

        if (isFocusedPreview) focusedFeaturedFolderId = targetId
        val requestId = ++featuredRequestId
        featuredSpotlightJob?.cancel()

        // Show cached hero metadata immediately so that high-res backdrop, synopsis, and Japanese title render with zero pop-in
        val cachedHero = cachedFolderMetaMap[targetId]
        val initialBackdrop = cachedHero?.bannerUrl ?: fallbackArtUrl
        val initialPoster = cachedHero?.posterUrl ?: fallbackArtUrl
        val initialTitle = cachedHero?.titleEnglish ?: cachedHero?.titleRomaji ?: chosenFolder.name

        _featuredAnime.value = FeaturedAnime(
            title = initialTitle,
            titleJapanese = cachedHero?.titleJapanese,
            synopsis = cachedHero?.synopsis,
            genres = cachedHero?.genres?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
            backdropUrl = initialBackdrop,
            folderId = targetId,
            posterUrl = initialPoster,
            year = cachedHero?.year,
            episodeCount = cachedHero?.episodeCount,
            score = cachedHero?.score,
            isFocusedPreview = isFocusedPreview,
            clearLogoUrl = cachedHero?.clearLogoUrl
        )

        featuredSpotlightJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val dbMeta = cachedHero ?: folderMetadataRepository.getMetadata(targetId)
                val aniListMeta = if (dbMeta != null && !dbMeta.bannerUrl.isNullOrBlank() && !dbMeta.synopsis.isNullOrBlank()) {
                    null
                } else {
                    animePosterResolver.resolveMetadata(cleanTitle)
                }
                val results = if (aniListMeta == null && dbMeta?.synopsis.isNullOrBlank()) tenraiAnimeService.searchAnime(cleanTitle) else emptyList()
                val entry = results.firstOrNull()
                if (requestId != featuredRequestId) return@launch

                if (aniListMeta != null) {
                    folderMetadataRepository.saveAnimeMetadata(targetId, chosenFolder.name, aniListMeta)
                    _featuredAnime.value = FeaturedAnime(
                        title = aniListMeta.titleEnglish ?: aniListMeta.titleRomaji ?: cleanTitle,
                        titleJapanese = aniListMeta.titleNative,
                        synopsis = aniListMeta.synopsis,
                        genres = aniListMeta.genres,
                        backdropUrl = aniListMeta.bannerUrl ?: aniListMeta.posterUrl ?: fallbackArtUrl,
                        folderId = targetId,
                        posterUrl = aniListMeta.posterUrl ?: fallbackArtUrl,
                        year = aniListMeta.year,
                        episodeCount = aniListMeta.episodeCount,
                        score = aniListMeta.score,
                        isFocusedPreview = isFocusedPreview,
                        clearLogoUrl = aniListMeta.clearLogoUrl
                    )
                } else if (entry != null && dbMeta?.synopsis.isNullOrBlank()) {
                    _featuredAnime.value = FeaturedAnime(
                        title = entry.titleEnglish ?: entry.title,
                        titleJapanese = entry.titleJapanese,
                        synopsis = entry.synopsis,
                        genres = entry.genres,
                        backdropUrl = entry.imageUrl ?: fallbackArtUrl,
                        folderId = targetId,
                        posterUrl = chosenFolder.posterUrl ?: entry.imageUrl ?: fallbackArtUrl,
                        year = entry.year,
                        episodeCount = entry.episodes,
                        score = entry.score?.toFloat(),
                        ageRating = entry.rating,
                        isFocusedPreview = isFocusedPreview
                    )
                } else {
                    _featuredAnime.value = FeaturedAnime(
                        title = chosenFolder.name,
                        backdropUrl = fallbackArtUrl,
                        folderId = targetId,
                        posterUrl = fallbackArtUrl,
                        isFocusedPreview = isFocusedPreview
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Error resolving featured spotlight", e)
                if (requestId == featuredRequestId) {
                    _featuredAnime.value = FeaturedAnime(
                        title = chosenFolder.name,
                        backdropUrl = fallbackArtUrl,
                        folderId = targetId,
                        posterUrl = fallbackArtUrl,
                        isFocusedPreview = isFocusedPreview
                    )
                }
            }
        }
    }

    fun filterAnimes(query: String) {
        val trimmed = query.trim()
        _searchQuery.value = trimmed
        _searchNotice.value = null
        searchJob?.cancel()
        if (trimmed.isBlank()) {
            _isSearching.value = false
            _filteredAnimes.value = _animeLibrary.value
        } else {
            _filteredAnimes.value = _animeLibrary.value.filter {
                it.name.contains(trimmed, ignoreCase = true) ||
                AnimePosterResolver.cleanAnimeTitle(it.name).contains(trimmed, ignoreCase = true)
            }
            searchJob = viewModelScope.launch {
                delay(350L)
                performGlobalSearch(trimmed)
            }
        }
    }

    private suspend fun performGlobalSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return

        try {
            _isSearching.value = true

            // 1. Check local offline catalog cache first
            val cached = catalogRepository.searchCatalog(trimmed)
            if (cached.isNotEmpty() && _searchQuery.value == trimmed) {
                val cachedFiles = cached.map { entry ->
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
                mergeSearchResults(cachedFiles)
            }

            // 2. Global Google Drive search with pagination
            val sanitized = trimmed.replace("'", "\\'")
            val driveQuery = "name contains '$sanitized' and (mimeType = 'application/vnd.google-apps.folder' or mimeType = 'application/vnd.google-apps.shortcut' or mimeType contains 'video/') and trashed=false"
            val response = driveRepository.get().getAllFilesDetailed(
                query = driveQuery,
                pageSize = 50
            )
            val listing = when (response) {
                is Resource.Success -> response.data
                is Resource.Error -> response.data
                is Resource.Loading -> null
            }
            if (listing != null && _searchQuery.value == trimmed) {
                val remoteFiles = listing.files.map { it.toDriveFile() }.filter { file ->
                    val isFolder = file.isFolder || file.isShortcutFolder
                    val isVideo = file.isVideoFile || file.isShortcutVideo
                    (isFolder || isVideo) && !file.name.startsWith(".")
                }
                mergeSearchResults(remoteFiles)
                if (!listing.isComplete) {
                    _searchNotice.value = when (listing.incompleteReason) {
                        zechs.drive.stream.data.model.FileCollectionResult.IncompleteReason.PAGE_LIMIT ->
                            "Busca parcial: o limite de páginas foi atingido. Refine os termos para encontrar outros itens."
                        zechs.drive.stream.data.model.FileCollectionResult.IncompleteReason.FILE_LIMIT ->
                            "Busca parcial: o limite de arquivos foi atingido. Refine os termos para encontrar outros itens."
                        else -> "Busca parcial: não foi possível carregar todos os resultados do Drive."
                    }
                } else if (response is Resource.Error) {
                    _searchNotice.value = "Busca parcial: não foi possível carregar todos os resultados do Drive."
                }
            } else if (response is Resource.Error && _searchQuery.value == trimmed) {
                _searchNotice.value = response.message
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error executing global search for: $query", e)
        } finally {
            if (currentCoroutineContext().isActive) {
                _isSearching.value = false
            }
        }
    }

    private fun mergeSearchResults(newResults: List<DriveFile>) {
        val current = _filteredAnimes.value.toMutableList()
        val existingIds = current.map { it.id }.toSet()
        val toAdd = newResults.filterNot { existingIds.contains(it.id) }
        if (toAdd.isNotEmpty()) {
            current.addAll(toAdd)
            _filteredAnimes.value = current
            resolveMissingPosters(toAdd)
        }
    }

    fun addToQueue(file: DriveFile) = viewModelScope.launch(Dispatchers.IO) {
        try {
            if (!watchQueueRepository.isInQueue(file.id)) {
                watchQueueRepository.enqueue(
                    fileId = file.id,
                    name = file.name,
                    posterUrl = file.posterUrl ?: file.thumbnailLink
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error adding to watch queue", e)
        }
    }

    fun starFile(file: DriveFile, starred: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val targetId = if (file.isShortcut) file.shortcutDetails.targetId ?: file.id else file.id
            favoriteRepository.set(targetId, file.name, starred)
            if (starred) {
                folderMetadataRepository.recordFolderOpened(targetId, file.name)
            }
            val status = if (starred) zechs.drive.stream.data.model.Starred.STARRED
            else zechs.drive.stream.data.model.Starred.UNSTARRED
            val updatedLibrary = _animeLibrary.value.map { item ->
                if (item.id == file.id || item.shortcutDetails.targetId == targetId) item.copy(starred = status) else item
            }
            _animeLibrary.value = updatedLibrary
            _filteredAnimes.value = _filteredAnimes.value.map { item ->
                if (item.id == file.id || item.shortcutDetails.targetId == targetId) item.copy(starred = status) else item
            }
            val currentStarred = _starredFiles.value.filterNot {
                it.id == targetId || it.shortcutDetails.targetId == targetId
            }
            val libraryFavorite = updatedLibrary.firstOrNull {
                it.id == file.id || it.shortcutDetails.targetId == targetId
            }
            _starredFiles.value = if (starred && libraryFavorite != null) {
                currentStarred + libraryFavorite.copy(starred = zechs.drive.stream.data.model.Starred.STARRED)
            } else currentStarred
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error updating Home favorite", e)
        }
    }

    fun removeFromQueue(fileId: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            watchQueueRepository.dequeue(fileId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error removing from watch queue", e)
        }
    }

    fun reorderQueue(fromPosition: Int, toPosition: Int) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val currentQueue = watchQueue.value.toMutableList()
            if (fromPosition in currentQueue.indices && toPosition in currentQueue.indices) {
                val item = currentQueue.removeAt(fromPosition)
                currentQueue.add(toPosition, item)
                watchQueueRepository.reorder(currentQueue)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Error reordering watch queue", e)
        }
    }

    enum class LibraryFilter {
        ALL,
        ANIMES_ONLY,
        MOVIES_ONLY,
        STARRED_ONLY
    }

    private var currentFilter = LibraryFilter.ALL

    fun filterStarred(starredOnly: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        currentFilter = if (starredOnly) LibraryFilter.STARRED_ONLY else LibraryFilter.ALL
        applyCurrentFilter()
    }

    fun filterMovies(moviesOnly: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        currentFilter = if (moviesOnly) LibraryFilter.MOVIES_ONLY else LibraryFilter.ANIMES_ONLY
        applyCurrentFilter()
    }

    fun isMovieFile(file: DriveFile): Boolean {
        if (file.isMovie) return true
        val lower = file.name.lowercase(java.util.Locale.ROOT)
        return lower.contains("spider-man") ||
                lower.contains("homem-aranha") ||
                lower.contains("batman") ||
                lower.contains("avengers") ||
                lower.contains("vingadores") ||
                lower.contains("[movie]") ||
                lower.contains("(movie)") ||
                lower.contains("filme") ||
                lower.contains("oppenheimer") ||
                lower.contains("deadpool") ||
                lower.contains("star wars")
    }

    private suspend fun applyCurrentFilter() {
        val all = _animeLibrary.value
        val filtered = when (currentFilter) {
            LibraryFilter.STARRED_ONLY -> {
                val localFavs = favoriteRepository.getFavoritesSync().map { it.folderId }.toSet()
                all.filter {
                    it.starred == zechs.drive.stream.data.model.Starred.STARRED || localFavs.contains(it.id)
                }
            }
            LibraryFilter.MOVIES_ONLY -> all.filter { isMovieFile(it) }
            LibraryFilter.ANIMES_ONLY -> all.filter { !isMovieFile(it) }
            LibraryFilter.ALL -> all
        }
        withContext(Dispatchers.Main) {
            _filteredAnimes.value = filtered
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
            val response = driveRepository.get().getAllFilesDetailed(
                query = "'$folderId' in parents and trashed=false",
                pageSize = 100
            )
            val listing = when (response) {
                is Resource.Success -> response.data
                is Resource.Error -> response.data
                is Resource.Loading -> null
            }
            if (listing?.isComplete == true) {
                val files = listing.files.map { it.toDriveFile() }
                val videos = files.filter { it.isVideoFile || it.isShortcutVideo }
                    .sortedWith { a, b -> zechs.drive.stream.utils.EpisodeParser.naturalCompare(a.name, b.name) }

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
                    val subFolders = files.filter { it.isFolder || it.isShortcutFolder }
                        .sortedWith { a, b -> zechs.drive.stream.utils.EpisodeParser.naturalCompare(a.name, b.name) }
                for (subFolder in subFolders) {
                    val subFolderId = if (subFolder.isShortcut && subFolder.shortcutDetails.targetId != null) {
                        subFolder.shortcutDetails.targetId
                    } else subFolder.id
                    val subResponse = driveRepository.get().getAllFilesDetailed(
                        query = "'$subFolderId' in parents and trashed=false",
                        pageSize = 100
                    )
                    val subListing = when (subResponse) {
                        is Resource.Success -> subResponse.data
                        is Resource.Error -> subResponse.data
                        is Resource.Loading -> null
                    }
                    if (subListing == null || !subListing.isComplete) {
                        Log.w(TAG, "Episode folder listing incomplete; opening the series page instead: $subFolderId")
                        withContext(Dispatchers.Main) { onResult(null, 0L) }
                        return@launch
                    }
                    if (subListing.files.isNotEmpty()) {
                        val subFiles = subListing.files.map { it.toDriveFile() }
                        val subVideos = subFiles.filter { it.isVideoFile || it.isShortcutVideo }
                            .sortedWith { a, b -> zechs.drive.stream.utils.EpisodeParser.naturalCompare(a.name, b.name) }
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
