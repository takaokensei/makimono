package zechs.drive.stream.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.WatchList
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.WatchListRepository
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
    private val animePosterResolver: zechs.drive.stream.data.remote.AnimePosterResolver
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

    fun recordFolderOpened(folderId: String, folderName: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            folderMetadataRepository.recordFolderOpened(folderId, folderName)
        } catch (e: Exception) {
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
            val response = driveRepository.get().getFiles(
                query = "name contains 'oneblacki' and mimeType = 'application/vnd.google-apps.folder' and trashed=false",
                pageToken = null,
                pageSize = 5
            )
            if (response is Resource.Success && response.data != null) {
                val folder = response.data.files.firstOrNull {
                    it.name.contains("oneblacki", ignoreCase = true)
                }
                if (folder != null) {
                    cachedOneBlackiId = folder.id
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        onResult(folder.id, folder.name)
                    }
                    return@launch
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding oneblacki folder", e)
        }

        kotlinx.coroutines.withContext(Dispatchers.Main) {
            onResult(null, "oneblacki")
        }
    }

    fun getStarredFiles() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val response = driveRepository.get().getFiles(
                query = "starred=true and trashed=false",
                pageToken = null,
                pageSize = 25
            )
            if (response is Resource.Success && response.data != null) {
                val rawFiles = response.data.files.map { it.toDriveFile() }

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
            }
        } catch (e: Exception) {
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
                    accessToken = tokenResponse.data!!.accessToken,
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

}
