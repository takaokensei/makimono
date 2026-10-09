package zechs.drive.stream.ui.files

import zechs.drive.stream.utils.AppLog
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import zechs.drive.stream.domain.common.AppError
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.Starred
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.DriveFailureClassifier
import zechs.drive.stream.ui.files.FilesFragment.Companion.TAG
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.ui.files.adapter.FilesPageMerger
import zechs.drive.stream.utils.Event
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.utils.state.ResourceErrorKind
import java.net.SocketTimeoutException
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.data.repository.FolderMetadataRepository
import javax.inject.Inject


@HiltViewModel
class FilesViewModel @Inject constructor(
    private val driveRepository: DriveRepository,
    private val sessionManager: SessionManager,
    private val folderMetadataRepository: FolderMetadataRepository,
    private val animePosterResolver: AnimePosterResolver,
    private val favoriteRepository: zechs.drive.stream.data.repository.FavoriteRepository
) : ViewModel() {

    fun recordFolderOpened(folderId: String, folderName: String) = viewModelScope.launch(Dispatchers.IO) {
        try {
            folderMetadataRepository.recordFolderOpened(folderId, folderName)
        } catch (e: Exception) {
            AppLog.e(TAG, "Error recording folder opened", e)
        }
    }

    fun getFirstEpisodeInFolder(folderId: String, onResult: (DriveFile?) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val response = driveRepository.getAllFilesDetailed(
                query = "'$folderId' in parents and trashed=false",
                pageSize = 100
            )
            val listing = when (response) {
                is Resource.Success -> response.data
                is Resource.Error -> response.data
                is Resource.Loading -> null
            }
            if (listing?.isComplete == true && listing.files.isNotEmpty()) {
                val videoFiles = listing.files.map { it.toDriveFile() }
                    .filter { it.isVideoFile || it.isShortcutVideo }
                    .sortedWith { a, b -> zechs.drive.stream.utils.EpisodeParser.naturalCompare(a.name, b.name) }
                val firstEp = videoFiles.firstOrNull()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult(firstEp)
                }
                return@launch
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Error resolving first episode in folder $folderId", e)
        }
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            onResult(null)
        }
    }

    private val _filesList = MutableLiveData<Resource<List<FilesDataModel>>>()
    val filesList: LiveData<Resource<List<FilesDataModel>>>
        get() = _filesList

    private val _uiState = MutableStateFlow<FilesUiState>(FilesUiState.Loading)
    val uiState: StateFlow<FilesUiState> = _uiState.asStateFlow()

    var lastFocusedPosition: Int? = null

    fun onEvent(event: FilesEvent) {
        when (event) {
            is FilesEvent.QueryFiles -> queryFiles(event.query)
            is FilesEvent.SelectSeason -> { /* no-op selection */ }
            is FilesEvent.SaveFocus -> { lastFocusedPosition = event.position }
            is FilesEvent.Retry -> queryFiles(activeQuery)
        }
    }

    private val _token = MutableLiveData<Event<Resource<FileToken>>>()
    val mpvFile: LiveData<Event<Resource<FileToken>>>
        get() = _token


    private var nextPageToken: String? = null
    @Volatile
    private var response: MutableList<FilesDataModel>? = null
    @Volatile
    private var responseGeneration = 0L
    private var posterResolutionJob: Job? = null
    @Volatile
    private var pageRequestJob: Job? = null
    @Volatile
    private var pageRequestGeneration = 0L
    private var activeQuery: String? = null
    private var hasActiveQuery = false
    private val pageSize = 25

    var hasLoaded = false

    var hasFailed = false
        private set

    var shouldResumePageRequest = false
        private set

    var isLastPage = nextPageToken == null
        private set

    fun queryFiles(query: String?): Job {
        if (!hasActiveQuery || query != activeQuery) {
            pageRequestGeneration++
            pageRequestJob?.cancel()
            pageRequestJob = null
            activeQuery = query
            hasActiveQuery = true
            nextPageToken = null
            isLastPage = false
            hasLoaded = false
            hasFailed = false
            shouldResumePageRequest = false
            responseGeneration++
            response = null
            posterResolutionJob?.cancel()
            _filesList.postValue(Resource.Loading())
            _uiState.value = FilesUiState.Loading
        }

        pageRequestJob?.takeIf { it.isActive }?.let { return it }

        // Replace the retry action with the normal paging indicator while retrying
        // the same Drive cursor. Loaded rows and the cursor remain untouched.
        response?.indexOfLast { it is FilesDataModel.PageError }?.takeIf { it >= 0 }?.let { index ->
            val updated = response.orEmpty().toMutableList().apply {
                this[index] = FilesDataModel.Loading
            }
            response = updated
            _filesList.postValue(Resource.Success(updated.toList()))
        }

        val requestGeneration = ++pageRequestGeneration
        shouldResumePageRequest = false
        var requestJob: Job? = null
        val newJob = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            _filesList.postValue(Resource.Loading())
            try {
                ensureActive()
                if (query != null) {
                    getQuery(query, requestGeneration)
                } else {
                    getTeamDrives(requestGeneration)
                }
            } catch (cancel: CancellationException) {
                AppLog.d(TAG, cancel.message ?: "CancellationException")
            } catch (timeout: SocketTimeoutException) {
                publishPageError("Server timed out", requestGeneration, ResourceErrorKind.NETWORK)
                AppLog.d(TAG, timeout.message ?: "SocketTimeoutException")
            } catch (e: Exception) {
                publishPageError(
                    e.message ?: "Something went wrong",
                    requestGeneration,
                    DriveFailureClassifier.fromException(e)
                )
                AppLog.e(TAG, "Something went wrong", e)
            } finally {
                if (pageRequestJob === requestJob) pageRequestJob = null
            }
        }
        requestJob = newJob
        pageRequestJob = newJob
        newJob.start()
        return newJob
    }

    /** Cancel network work when this folder leaves the foreground; its last successful cursor is retained. */
    fun cancelPendingPageRequest() {
        val request = pageRequestJob ?: return
        if (!request.isActive) return

        pageRequestGeneration++
        pageRequestJob = null
        request.cancel()

        val current = response ?: return
        val retained = current.filterNot { it == FilesDataModel.Loading }.toMutableList()
        if (retained.size != current.size) {
            response = retained
            shouldResumePageRequest = retained.isNotEmpty() && !isLastPage
            _filesList.postValue(Resource.Success(retained.toList()))
        }
    }

    private suspend fun getQuery(query: String, requestGeneration: Long) {
        val filesResponse = driveRepository.getFiles(
            query = query,
            pageToken = nextPageToken,
            pageSize = pageSize
        )
        currentCoroutineContext().ensureActive()
        if (requestGeneration != pageRequestGeneration) return

        when (filesResponse) {
            is Resource.Success -> {
                val files = filesResponse.data

                AppLog.d(TAG, files.toString())

                val driveFiles = files.files.map { it.toDriveFile() }
                val favoriteTargetsByFileId = driveFiles.associate { file ->
                    file.id to ((if (file.isShortcut) file.shortcutDetails.targetId else null) ?: file.id)
                }
                val favoriteTargetIds = favoriteRepository.getFavoriteIds(favoriteTargetsByFileId.values)
                currentCoroutineContext().ensureActive()
                if (requestGeneration != pageRequestGeneration) return
                nextPageToken = files.nextPageToken
                isLastPage = nextPageToken == null
                val filesList = driveFiles
                    .map { driveFile ->
                        val targetId = favoriteTargetsByFileId.getValue(driveFile.id)
                        val starredStatus = if (targetId in favoriteTargetIds) Starred.STARRED else driveFile.starred
                        FilesDataModel.File(driveFile.copy(starred = starredStatus))
                    }
                    .distinctBy { it.driveFile.id }

                postSuccess(filesList, requestGeneration)
            }
            is Resource.Error -> {
                publishPageError(filesResponse.message, requestGeneration, filesResponse.errorKind)
            }
            else -> {}
        }
    }

    private suspend fun getTeamDrives(requestGeneration: Long) {
        val drivesResponse = driveRepository.getDrives(
            pageToken = nextPageToken,
            pageSize = pageSize
        )
        currentCoroutineContext().ensureActive()
        if (requestGeneration != pageRequestGeneration) return

        when (drivesResponse) {
            is Resource.Success -> {
                val teamDrives = drivesResponse.data

                AppLog.d(TAG, teamDrives.toString())

                nextPageToken = teamDrives.nextPageToken
                isLastPage = nextPageToken == null

                val sharedDrives = teamDrives.drives
                    .map { FilesDataModel.File(it.toDriveFile()) }
                    .distinctBy { it.driveFile.id }

                postSuccess(sharedDrives, requestGeneration)
            }
            is Resource.Error -> {
                publishPageError(drivesResponse.message, requestGeneration, drivesResponse.errorKind)
            }
            else -> {}
        }
    }

    private fun postSuccess(
        filesList: List<FilesDataModel.File>,
        requestGeneration: Long
    ) {
        if (requestGeneration != pageRequestGeneration) return
        hasFailed = false
        shouldResumePageRequest = false
        val updated = FilesPageMerger.merge(response, filesList)

        // before submitting add Loading
        // if list is not at last page
        if (!isLastPage) {
            updated.add(FilesDataModel.Loading)
        }

        response = updated
        val initialVisibleCount = updated.count { it is FilesDataModel.File }
        if (initialVisibleCount == 0 && isLastPage) {
            _uiState.value = FilesUiState.Empty()
        } else {
            _uiState.value = FilesUiState.Content(
                files = updated.toList(),
                fileCount = initialVisibleCount,
                isFirstLoad = !hasLoaded
            )
        }
        val generation = ++responseGeneration
        posterResolutionJob?.cancel()
        posterResolutionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val allMeta = folderMetadataRepository.getAllMetadata().associateBy { it.folderId }
                ensureActive()
                if (generation != responseGeneration) return@launch

                val current = response ?: return@launch
                val withCachedPosters = current.map { item ->
                    if (item !is FilesDataModel.File) return@map item
                    val file = item.driveFile
                    val isFolder = file.isFolder || file.isShortcutFolder
                    if (!isFolder || !file.posterUrl.isNullOrBlank()) return@map item

                    val targetId = if (file.isShortcut) file.shortcutDetails.targetId ?: file.id else file.id
                    val cached = allMeta[targetId]?.posterUrl
                    if (cached.isNullOrBlank()) item else FilesDataModel.File(file.copy(posterUrl = cached))
                }.toMutableList()

                if (generation != responseGeneration) return@launch
                response = withCachedPosters
                _filesList.postValue(Resource.Success(withCachedPosters.toList()))
                val visibleCount = withCachedPosters.count { it is FilesDataModel.File }
                if (visibleCount == 0 && isLastPage) {
                    _uiState.value = FilesUiState.Empty()
                } else {
                    _uiState.value = FilesUiState.Content(
                        files = withCachedPosters.toList(),
                        fileCount = visibleCount,
                        isFirstLoad = !hasLoaded
                    )
                }

                resolveMissingPosters(generation)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                AppLog.e(TAG, "Error enriching Drive folder posters", e)
                if (generation == responseGeneration) {
                    response?.let { _filesList.postValue(Resource.Success(it.toList())) }
                }
            }
        }
    }

    private fun publishPageError(
        message: String?,
        requestGeneration: Long,
        errorKind: ResourceErrorKind? = ResourceErrorKind.UNKNOWN
    ) {
        if (requestGeneration != pageRequestGeneration) return
        hasFailed = true
        shouldResumePageRequest = false
        responseGeneration++
        posterResolutionJob?.cancel()
        val currentFiles = response
            ?.filterNot { it == FilesDataModel.Loading }
            ?.toMutableList()
        val hasVisibleFiles = currentFiles.orEmpty().any { it is FilesDataModel.File }
        if (currentFiles != null && hasVisibleFiles && !isLastPage) {
            currentFiles.removeAll { it is FilesDataModel.PageError }
            currentFiles += FilesDataModel.PageError(errorKind ?: ResourceErrorKind.UNKNOWN)
            response = currentFiles
            _filesList.postValue(
                Resource.Error(
                    message = message ?: "Something went wrong",
                    data = currentFiles.toList(),
                    errorKind = errorKind ?: ResourceErrorKind.UNKNOWN
                )
            )
            return
        }
        if (currentFiles != null) response = currentFiles
        val appErr = when (errorKind) {
            ResourceErrorKind.NETWORK ->
                AppError.Network(message ?: "Falha de rede. Verifique sua conexão.")
            ResourceErrorKind.AUTHENTICATION, ResourceErrorKind.RATE_LIMITED ->
                AppError.Authentication(message ?: "Sessão expirada. Reconecte sua conta.")
            ResourceErrorKind.NOT_FOUND ->
                AppError.NotFound(message ?: "Pasta ou arquivo não encontrado.")
            else ->
                AppError.Unknown(message ?: "Erro ao carregar arquivos.")
        }
        _uiState.value = FilesUiState.Error(appErr, currentFiles?.toList().orEmpty())
        _filesList.postValue(
            Resource.Error(
                message ?: "Something went wrong",
                currentFiles?.toList(),
                errorKind ?: ResourceErrorKind.UNKNOWN
            )
        )
    }

    private suspend fun resolveMissingPosters(generation: Long) = coroutineScope {
        val candidateFiles = response.orEmpty()
            .filterIsInstance<FilesDataModel.File>()
            .map { it.driveFile }
            .filter { (it.isFolder || it.isShortcutFolder) && it.posterUrl.isNullOrBlank() }
            .take(100)

        candidateFiles.chunked(6).forEach { chunk ->
            currentCoroutineContext().ensureActive()
            if (generation != responseGeneration) return@coroutineScope

            val results = chunk.map { file ->
                async {
                    val targetId = if (file.isShortcut) file.shortcutDetails.targetId ?: file.id else file.id
                    val poster = animePosterResolver.resolvePoster(file.name)
                    if (!poster.isNullOrBlank()) {
                        folderMetadataRepository.updatePosterUrl(targetId, file.name, poster)
                        file.id to poster
                    } else null
                }
            }.awaitAll().filterNotNull()

            if (results.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                if (generation != responseGeneration) return@coroutineScope
                val latest = response ?: return@coroutineScope
                val enriched = latest.toMutableList()
                var changed = false
                results.forEach { (fileId, poster) ->
                    val index = enriched.indexOfFirst {
                        it is FilesDataModel.File && it.driveFile.id == fileId
                    }
                    val current = enriched.getOrNull(index) as? FilesDataModel.File
                    if (index >= 0 && current != null && current.driveFile.posterUrl.isNullOrBlank()) {
                        enriched[index] = FilesDataModel.File(current.driveFile.copy(posterUrl = poster))
                        changed = true
                    }
                }
                if (changed) {
                    response = enriched
                    _filesList.postValue(Resource.Success(enriched.toList()))
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

    fun fetchToken(file: DriveFile) = viewModelScope.launch {
        _token.postValue(Event(Resource.Loading()))

        val client = sessionManager.fetchClient() ?: run {
            _token.postValue(Event(Resource.Error("Client not found")))
            return@launch
        }
        val tokenResponse = driveRepository.fetchAccessToken(client)

        when (tokenResponse) {
            is Resource.Success -> {
                val fileToken = FileToken(
                    fileId = file.id,
                    fileName = file.name,
                    accessToken = tokenResponse.data.accessToken,
                    thumbnailLink = file.thumbnailLink
                )
                _token.postValue(Event(Resource.Success(fileToken)))
            }
            is Resource.Error -> {
                _token.postValue(
                    Event(Resource.Error(tokenResponse.message))
                )
            }
            else -> {}
        }
    }

    private val _fileUpdate = MutableSharedFlow<String>()
    val fileUpdate: SharedFlow<String>
        get() = _fileUpdate.asSharedFlow()

    fun starFile(
        file: DriveFile,
        starred: Boolean
    ) = viewModelScope.launch(Dispatchers.IO) {
        fun updateFileState(starredStarred: Starred) {
            val list = response
            val index = list?.indexOfFirst {
                if (it is FilesDataModel.File) {
                    it.driveFile.id == file.id
                } else false
            } ?: -1
            if (list != null && index != -1) {
                val newFile = FilesDataModel.File(
                    file.copy(starred = starredStarred)
                )
                list[index] = newFile
                _filesList.postValue(Resource.Success(list))
            }
        }

        try {
            updateFileState(Starred.LOADING)
            val targetId = (if (file.isShortcut) file.shortcutDetails.targetId else null) ?: file.id
            favoriteRepository.set(targetId, file.name, starred)
            if (starred) {
                folderMetadataRepository.recordFolderOpened(targetId, file.name)
            }
            updateFileState(if (starred) Starred.STARRED else Starred.UNSTARRED)
        } catch (cancel: CancellationException) {
            updateFileState(if (starred) Starred.UNSTARRED else Starred.STARRED)
            _fileUpdate.emit("Unable to update favorite")
            AppLog.d(TAG, cancel.message ?: "CancellationException")
        } catch (e: Exception) {
            updateFileState(if (starred) Starred.UNSTARRED else Starred.STARRED)
            _fileUpdate.emit(e.message ?: "Something went wrong")
            AppLog.e(TAG, "Something went wrong", e)
        }
    }

}
