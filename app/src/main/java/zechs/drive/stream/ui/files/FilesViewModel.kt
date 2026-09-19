package zechs.drive.stream.ui.files

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import zechs.drive.stream.data.model.DriveFile
import zechs.drive.stream.data.model.Starred
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.ui.files.FilesFragment.Companion.TAG
import zechs.drive.stream.ui.files.adapter.FilesDataModel
import zechs.drive.stream.utils.Event
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.state.Resource
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
            Log.e(TAG, "Error recording folder opened", e)
        }
    }

    fun getFirstEpisodeInFolder(folderId: String, onResult: (DriveFile?) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        try {
            val res = driveRepository.getAllFiles(
                query = "'$folderId' in parents and mimeType contains 'video/' and trashed=false",
                pageSize = 10
            )
            if (res is Resource.Success && res.data.isNotEmpty()) {
                val videoFiles = res.data.map { it.toDriveFile() }
                    .sortedWith { a, b -> zechs.drive.stream.utils.EpisodeParser.naturalCompare(a.name, b.name) }
                val firstEp = videoFiles.firstOrNull()
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onResult(firstEp)
                }
                return@launch
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving first episode in folder $folderId", e)
        }
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            onResult(null)
        }
    }

    private val _filesList = MutableLiveData<Resource<List<FilesDataModel>>>()
    val filesList: LiveData<Resource<List<FilesDataModel>>>
        get() = _filesList

    private val _token = MutableLiveData<Event<Resource<FileToken>>>()
    val mpvFile: LiveData<Event<Resource<FileToken>>>
        get() = _token


    private var nextPageToken: String? = null
    private var response: MutableList<FilesDataModel>? = null
    private val pageSize = 25

    var hasLoaded = false

    var hasFailed = false
        private set

    var isLastPage = nextPageToken == null
        private set

    fun queryFiles(query: String?) = viewModelScope.launch(Dispatchers.IO) {
        _filesList.postValue(Resource.Loading())
        try {

            /**
             * Ensures that current scope is active
             * else throw CancellationException
             */
            ensureActive()

            if (query != null) {
                getQuery(query)
            } else {
                getTeamDrives()
            }
            hasFailed = false
        } catch (cancel: CancellationException) {
            Log.d(TAG, cancel.message ?: "CancellationException")
        } catch (timeout: SocketTimeoutException) {
            _filesList.postValue(Resource.Error("Server timed out"))
            Log.d(TAG, timeout.message ?: "SocketTimeoutException")
            hasFailed = true
        } catch (e: Exception) {
            _filesList.postValue(Resource.Error(e.message ?: "Something went wrong"))
            Log.e(TAG, "Something went wrong", e)
            hasFailed = true
        }
    }

    private suspend fun getQuery(query: String) {
        val filesResponse = driveRepository.getFiles(
            query = query,
            pageToken = nextPageToken,
            pageSize = pageSize
        )

        when (filesResponse) {
            is Resource.Success -> {
                val files = filesResponse.data

                Log.d(TAG, files.toString())

                nextPageToken = files.nextPageToken
                isLastPage = nextPageToken == null

                val filesDataModel = mutableListOf<FilesDataModel>()

                val filesList = files.files
                    .map {
                        val driveFile = it.toDriveFile()
                        val targetId = (if (driveFile.isShortcut) driveFile.shortcutDetails.targetId else null) ?: driveFile.id
                        val isFav = favoriteRepository.isFavorite(targetId)
                        val starredStatus = if (isFav) Starred.STARRED else driveFile.starred
                        FilesDataModel.File(driveFile.copy(starred = starredStatus))
                    }
                    .distinctBy { it.driveFile.id }

                postSuccess(filesDataModel, filesList)
            }
            is Resource.Error -> {
                _filesList.postValue(
                    Resource.Error(message = filesResponse.message)
                )
            }
            else -> {}
        }
    }

    private suspend fun getTeamDrives() {
        val drivesResponse = driveRepository.getDrives(
            pageToken = nextPageToken,
            pageSize = pageSize
        )

        when (drivesResponse) {
            is Resource.Success -> {
                val teamDrives = drivesResponse.data

                Log.d(TAG, teamDrives.toString())

                nextPageToken = teamDrives.nextPageToken
                isLastPage = nextPageToken == null

                val filesDataModel = mutableListOf<FilesDataModel>()
                val sharedDrives = teamDrives.drives
                    .map { FilesDataModel.File(it.toDriveFile()) }
                    .distinctBy { it.driveFile.id }

                postSuccess(filesDataModel, sharedDrives)
            }
            is Resource.Error -> {
                _filesList.postValue(Resource.Error(drivesResponse.message))
            }
            else -> {}
        }
    }

    private fun postSuccess(
        filesDataModel: MutableList<FilesDataModel>,
        filesList: List<FilesDataModel.File>
    ) {
        val existing = response
        val updated: MutableList<FilesDataModel> = if (existing == null) {
            filesDataModel.addAll(filesList)
            filesDataModel
        } else {
            // append new list of files
            existing.addAll(filesList)

            // return new list and remove all Loading
            existing.filter {
                it != FilesDataModel.Loading
            }.toMutableList()
        }

        // before submitting add Loading
        // if list is not at last page
        if (!isLastPage) {
            updated.add(FilesDataModel.Loading)
        }

        response = updated

        attachCachedPostersAndResolveMissing()
    }

    private fun attachCachedPostersAndResolveMissing() = viewModelScope.launch(Dispatchers.IO) {
        try {
            val allMeta = folderMetadataRepository.getAllMetadata().associateBy { it.folderId }
            val list = response
            if (list != null) {
                for (i in list.indices) {
                    val item = list[i]
                    if (item is FilesDataModel.File) {
                        val file = item.driveFile
                        val isFolder = file.isFolder || file.isShortcutFolder
                        if (isFolder && file.posterUrl.isNullOrBlank()) {
                            val targetId = if (file.isShortcut) file.shortcutDetails.targetId ?: file.id else file.id
                            val cached = allMeta[targetId]?.posterUrl
                            if (!cached.isNullOrBlank()) {
                                list[i] = FilesDataModel.File(file.copy(posterUrl = cached))
                            }
                        }
                    }
                }
            }
            val snapshot = response
            if (snapshot != null) {
                _filesList.postValue(Resource.Success(snapshot.toList()))
            }

            resolveMissingPosters()
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching cached posters", e)
            val fallback = response
            if (fallback != null) {
                _filesList.postValue(Resource.Success(fallback))
            }
        }
    }

    private fun resolveMissingPosters() = viewModelScope.launch(Dispatchers.IO) {
        val currentList = response ?: return@launch
        for (i in currentList.indices.take(100)) {
            val item = currentList.getOrNull(i)
            if (item is FilesDataModel.File) {
                val file = item.driveFile
                val isFolder = file.isFolder || file.isShortcutFolder
                if (isFolder && file.posterUrl.isNullOrBlank()) {
                    val targetId = if (file.isShortcut) file.shortcutDetails.targetId ?: file.id else file.id
                    val poster = animePosterResolver.resolvePoster(file.name)
                    if (!poster.isNullOrBlank()) {
                        folderMetadataRepository.updatePosterUrl(targetId, file.name, poster)
                        if (i < currentList.size && currentList[i] is FilesDataModel.File) {
                            currentList[i] = FilesDataModel.File(file.copy(posterUrl = poster))
                            _filesList.postValue(Resource.Success(currentList.toList()))
                        }
                    }
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
            Log.d(TAG, cancel.message ?: "CancellationException")
        } catch (e: Exception) {
            updateFileState(if (starred) Starred.UNSTARRED else Starred.STARRED)
            _fileUpdate.emit(e.message ?: "Something went wrong")
            Log.e(TAG, "Something went wrong", e)
        }
    }

}
