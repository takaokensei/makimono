package zechs.drive.stream.data.repository

import android.util.Log
import dagger.Lazy
import zechs.drive.stream.data.model.*
import zechs.drive.stream.data.remote.DriveApi
import zechs.drive.stream.data.remote.TokenApi
import zechs.drive.stream.utils.SessionManager
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.utils.state.ResourceErrorKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveRepository @Inject constructor(
    private val driveApi: DriveApi,
    private val tokenApi: Lazy<TokenApi>,
    private val sessionManager: SessionManager
) {

    companion object {
        private const val TAG = "DriveRepository"

        /**
         * Safety cap on pagination to prevent runaway API calls on very large shared drives.
         * At the default page size of 100 this covers up to 2 500 files per query — well above
         * any practical anime-streaming library while still bounding latency and quota usage.
        */
        const val MAX_PAGINATION_PAGES = 25
        const val MAX_PAGINATED_FILES = 2_500
    }

    suspend fun <T> retryWithBackoff(
        times: Int = 3,
        initialDelayMs: Long = 300L,
        maxDelayMs: Long = 2000L,
        factor: Double = 2.0,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMs
        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val isTransient = when (e) {
                    is IOException -> true
                    is HttpException -> e.code() in listOf(429, 500, 502, 503, 504)
                    else -> false
                }
                if (!isTransient) throw e
                Log.w(TAG, "Transient network error on attempt ${attempt + 1}, retrying in ${currentDelay}ms...", e)
                delay(currentDelay)
                currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelayMs)
            }
        }
        return block()
    }

    private suspend fun getOrFetchAccessToken(): Resource<String> {
        val client = sessionManager.fetchClient()
            ?: return Resource.Error(
                "Conecte sua conta do Google Drive para continuar.",
                errorKind = ResourceErrorKind.AUTHENTICATION
            )
        return when (val tokenResponse = fetchAccessToken(client)) {
            is Resource.Success -> Resource.Success(tokenResponse.data.accessToken)
            is Resource.Error -> Resource.Error(
                tokenResponse.message,
                errorKind = tokenResponse.errorKind ?: ResourceErrorKind.UNKNOWN
            )
            is Resource.Loading -> Resource.Loading()
        }
    }

    suspend fun getFiles(
        query: String,
        pageToken: String?,
        pageSize: Int,
        orderBy: String = "folder, name"
    ): Resource<FilesResponse> {
        val accessToken = when (val tokenRes = getOrFetchAccessToken()) {
            is Resource.Success -> tokenRes.data
            is Resource.Error -> return Resource.Error(
                tokenRes.message,
                errorKind = tokenRes.errorKind ?: ResourceErrorKind.UNKNOWN
            )
            is Resource.Loading -> return Resource.Loading()
        }
        return try {
            val files = requestFiles(accessToken, query, pageToken, pageSize, orderBy)
            Resource.Success(files)
        } catch (unauthorized: HttpException) {
            if (unauthorized.code() != 401) return doOnError(unauthorized)

            val client = sessionManager.fetchClient()
                ?: return doOnError(unauthorized)
            val refreshed = fetchAccessToken(client, forceRefresh = true)
            if (refreshed is Resource.Success) {
                try {
                    Resource.Success(
                        requestFiles(refreshed.data.accessToken, query, pageToken, pageSize, orderBy)
                    )
                } catch (retryError: Exception) {
                    doOnError(retryError)
                }
            } else {
                Resource.Error(
                    refreshed.message ?: "Sessão do Google Drive expirada. Conecte sua conta novamente.",
                    errorKind = refreshed.errorKind ?: ResourceErrorKind.AUTHENTICATION
                )
            }
        } catch (e: Exception) {
            doOnError(e)
        }
    }

    private suspend fun requestFiles(
        accessToken: String,
        query: String,
        pageToken: String?,
        pageSize: Int,
        orderBy: String
    ): FilesResponse = retryWithBackoff {
        driveApi.getFiles(
            q = query,
            pageSize = pageSize,
            pageToken = pageToken,
            accessToken = "Bearer $accessToken",
            orderBy = orderBy
        )
    }

    /**
     * Compatibility API for consumers that require a complete list. A bounded or failed
     * traversal is returned as an error; any fetched files remain attached as partial data.
     */
    suspend fun getAllFiles(
        query: String,
        pageSize: Int = 100,
        maxPages: Int = MAX_PAGINATION_PAGES,
        orderBy: String = "folder, name"
    ): Resource<List<File>> {
        return when (val result = getAllFilesDetailed(query, pageSize, maxPages, orderBy)) {
            is Resource.Success -> {
                if (result.data.isComplete) {
                    Resource.Success(result.data.files)
                } else {
                    Resource.Error(
                        message = incompleteListingMessage(result.data.incompleteReason),
                        data = result.data.files
                    )
                }
            }
            is Resource.Error -> Resource.Error(result.message, result.data?.files, result.errorKind ?: ResourceErrorKind.UNKNOWN)
            is Resource.Loading -> Resource.Loading()
        }
    }

    /**
     * Fetches a bounded number of Drive pages and always reports whether the result is complete.
     * An error after one or more successful pages carries those files and the cursor for recovery.
     */
    suspend fun getAllFilesDetailed(
        query: String,
        pageSize: Int = 100,
        maxPages: Int = MAX_PAGINATION_PAGES,
        orderBy: String = "folder, name"
    ): Resource<FileCollectionResult> {
        // A Drive page boundary is not a stable snapshot: files may move or be updated while
        // pagination is in progress. Keep IDs unique while preserving their first-seen order.
        // Replacing an existing value retains that order but uses the latest metadata returned.
        val collected = linkedMapOf<String, File>()
        var pageToken: String? = null
        var pagesFetched = 0

        val boundedPages = maxPages.coerceIn(1, MAX_PAGINATION_PAGES)
        val boundedPageSize = pageSize.coerceIn(1, 100)
        val queryContext = DriveQueryContext(
            query = query,
            pageSize = boundedPageSize,
            orderBy = orderBy
        )

        repeat(boundedPages) {
            val remaining = MAX_PAGINATED_FILES - collected.size
            if (remaining <= 0) {
                return Resource.Success(
                    FileCollectionResult(
                        files = collected.values.toList(),
                        isComplete = pageToken.isNullOrBlank(),
                        nextPageToken = pageToken,
                        pagesFetched = pagesFetched,
                        queryContext = queryContext,
                        incompleteReason = if (pageToken.isNullOrBlank()) null
                        else FileCollectionResult.IncompleteReason.FILE_LIMIT
                    )
                )
            }

            val effectivePageSize = minOf(boundedPageSize, remaining)
            when (val response = getFiles(query, pageToken, effectivePageSize, orderBy)) {
                is Resource.Success -> {
                    response.data.files.forEach { file ->
                        if (file.id in collected) {
                            collected[file.id] = file
                        } else if (collected.size < MAX_PAGINATED_FILES) {
                            collected[file.id] = file
                        }
                    }
                    pagesFetched++
                    pageToken = response.data.nextPageToken
                    if (pageToken.isNullOrBlank()) {
                        return Resource.Success(
                            FileCollectionResult(
                                files = collected.values.toList(),
                                isComplete = true,
                                nextPageToken = null,
                                pagesFetched = pagesFetched,
                                queryContext = queryContext
                            )
                        )
                    }
                }
                is Resource.Error -> {
                    return Resource.Error(
                        message = response.message,
                        data = FileCollectionResult(
                            files = collected.values.toList(),
                            isComplete = false,
                            nextPageToken = pageToken,
                            pagesFetched = pagesFetched,
                            queryContext = queryContext,
                            incompleteReason = FileCollectionResult.IncompleteReason.REQUEST_FAILED
                        ),
                        errorKind = response.errorKind ?: ResourceErrorKind.UNKNOWN
                    )
                }
                is Resource.Loading -> return Resource.Loading()
            }
        }

        return Resource.Success(
            FileCollectionResult(
                files = collected.values.toList(),
                isComplete = false,
                nextPageToken = pageToken,
                pagesFetched = pagesFetched,
                queryContext = queryContext,
                incompleteReason = if (collected.size >= MAX_PAGINATED_FILES) {
                    FileCollectionResult.IncompleteReason.FILE_LIMIT
                } else {
                    FileCollectionResult.IncompleteReason.PAGE_LIMIT
                }
            )
        )
    }

    private fun incompleteListingMessage(reason: FileCollectionResult.IncompleteReason?): String = when (reason) {
        FileCollectionResult.IncompleteReason.PAGE_LIMIT ->
            "A listagem atingiu o limite de páginas e está incompleta. Continue usando o próximo marcador."
        FileCollectionResult.IncompleteReason.FILE_LIMIT ->
            "A listagem atingiu o limite de arquivos e está incompleta. Continue usando o próximo marcador."
        FileCollectionResult.IncompleteReason.REQUEST_FAILED, null ->
            "Não foi possível concluir a listagem do Google Drive. Tente novamente."
    }

    suspend fun getDrives(
        pageToken: String?,
        pageSize: Int,
    ): Resource<DriveResponse> {
        val accessToken = when (val tokenRes = getOrFetchAccessToken()) {
            is Resource.Success -> tokenRes.data
            is Resource.Error -> return Resource.Error(
                tokenRes.message,
                errorKind = tokenRes.errorKind ?: ResourceErrorKind.UNKNOWN
            )
            is Resource.Loading -> return Resource.Loading()
        }
        return try {
            val drives = driveApi.getDrives(
                pageSize = pageSize,
                pageToken = pageToken,
                accessToken = "Bearer $accessToken"
            )
            Resource.Success(drives)
        } catch (e: Exception) {
            doOnError(e)
        }
    }

    /**
     *
     * Retrieve access token from datastore.
     *
     * Note: Token is automatically refreshed
     * if it's expired.
     *
     * Note: Everytime token is refreshed it's
     * updated in datastore.
     *
     * @param forceRefresh forcefully refresh the token.
     *
     */
    suspend fun fetchAccessToken(
        client: DriveClient,
        forceRefresh: Boolean = false
    ): Resource<TokenResponse> {
        if (!forceRefresh) {
            val tokenResponse = sessionManager.fetchAccessToken()
            tokenResponse?.let {
                val currentTimeInSeconds = System.currentTimeMillis() / 1000
                if (currentTimeInSeconds >= it.expiresIn) {
                    Log.d(TAG, "Access token has expired. Trying to refresh")
                } else {
                    Log.d(TAG, "Access token is valid")
                    return Resource.Success(data = it)
                }
            }
        } else {
            Log.d(TAG, "Force refreshing access token")
        }

        val refreshToken = sessionManager.fetchRefreshToken()
            ?: return Resource.Error(
                "Sessão do Google Drive ausente ou expirada. Conecte sua conta para continuar.",
                errorKind = ResourceErrorKind.AUTHENTICATION
            )

        return try {
            val token = tokenApi.get().getAccessToken(
                clientId = client.clientId,
                clientSecret = client.clientSecret,
                refreshToken = refreshToken
            )
            Log.d(TAG, "Received access token (len=${token.accessToken.length})")
            sessionManager.saveAccessToken(token)
            Resource.Success(token)
        } catch (e: Exception) {
            if (e is HttpException && e.code() == 400) {
                val errorBody = try { e.response()?.errorBody()?.string() } catch (_: Exception) { null }
                Log.e(TAG, "Google OAuth 400 Bad Request: $errorBody", e)
                Resource.Error(
                    "Sessão do Google Drive expirada ou revogada. Faça login novamente para continuar.",
                    errorKind = ResourceErrorKind.AUTHENTICATION
                )
            } else {
                doOnError(e)
            }
        }
    }

    /**
     *
     * Exchange authorization code for refresh token.
     *
     * Note: Refresh token and access token both
     * are saved in datastore upon successfully request.
     *
     * @param authorizationCode Auth code received from Sign-in flow.
     *
     */
    suspend fun fetchRefreshToken(
        client: DriveClient,
        authorizationCode: String
    ): Resource<AuthorizationResponse> {
        return try {
            Log.d(TAG, "Requesting refresh token from authorization code")
            val token = tokenApi.get().getRefreshToken(
                clientId = client.clientId,
                clientSecret = client.clientSecret,
                redirectUri = client.redirectUri,
                authCode = authorizationCode
            )

            Log.d(TAG, "Received refresh token (len=${token.refreshToken.length})")

            // saving in data store
            sessionManager.saveClient(client)
            sessionManager.saveRefreshToken(token.refreshToken)
            sessionManager.saveAccessToken(token.toTokenResponse())

            Resource.Success(token)
        } catch (e: Exception) {
            doOnError(e)
        }
    }

    private inline fun <reified T> doOnError(e: Exception): Resource<T> {
        if (e is CancellationException) throw e
        Log.e(TAG, "Drive API call failed: ${e.message}", e)
        val error = when (e) {
            is HttpException -> {
                when (e.code()) {
                    401 -> "Sessão expirada ou não autorizada (401)"
                    403 -> "Acesso negado ou limite de requisições excedido (403)"
                    404 -> "Recurso não encontrado no Google Drive (404)"
                    in 500..599 -> "Servidor do Google Drive indisponível (${e.code()})"
                    else -> "Erro na comunicação com o Google Drive (${e.code()})"
                }
            }
            is SocketTimeoutException -> "Tempo limite de conexão esgotado"
            is IOException -> "Falha de conexão com a rede: ${e.localizedMessage ?: "Verifique sua internet"}"
            else -> e.localizedMessage ?: "Ocorreu um erro desconhecido."
        }
        val errorKind = when (e) {
            is HttpException -> DriveFailureClassifier.fromHttp(e.code(), googleFailureReason(e))
            else -> DriveFailureClassifier.fromException(e)
        }
        return Resource.Error(error, errorKind = errorKind)
    }

    private fun googleFailureReason(error: HttpException): String? = runCatching {
        val body = error.response()?.errorBody()?.string() ?: return@runCatching null
        val errors = org.json.JSONObject(body).optJSONObject("error")?.optJSONArray("errors")
        errors?.optJSONObject(0)?.optString("reason")?.takeIf { it.isNotBlank() }
    }.getOrNull()

    private suspend fun <T> executeWithTokenRetry(block: suspend (accessToken: String) -> T): T {
        val accessToken = when (val tokenRes = getOrFetchAccessToken()) {
            is Resource.Success -> tokenRes.data
            is Resource.Error -> throw IOException(tokenRes.message ?: "Access token is null")
            is Resource.Loading -> throw IOException("Loading access token")
        }
        return try {
            block(accessToken)
        } catch (unauthorized: HttpException) {
            if (unauthorized.code() != 401) throw unauthorized
            val client = sessionManager.fetchClient() ?: throw unauthorized
            val refreshed = fetchAccessToken(client, forceRefresh = true)
            if (refreshed is Resource.Success) {
                block(refreshed.data.accessToken)
            } else {
                throw unauthorized
            }
        }
    }

    suspend fun updateFile(
        fileId: String,
        starred: Boolean
    ): Resource<Unit> {
        return try {
            executeWithTokenRetry { token ->
                val update = driveApi.updateFile(
                    fileId = fileId,
                    fileUpdateRequest = FileUpdateRequest(starred = starred),
                    accessToken = "Bearer $token"
                )
                if (update.isSuccessful) {
                    Resource.Success(Unit)
                } else {
                    val code = update.code()
                    if (code == 401) throw HttpException(update)
                    val errorMsg = when (code) {
                        403 -> "Permissão insuficiente (necessário escopo de escrita no Google Drive)"
                        404 -> "Item não encontrado no Drive"
                        else -> "Falha ao atualizar ($code)"
                    }
                    Log.e(TAG, "updateFile error ($code): ${update.errorBody()?.use { it.string() }}")
                    Resource.Error(errorMsg)
                }
            }
        } catch (e: Exception) {
            doOnError(e)
        }
    }

    suspend fun getFileSiblings(fileId: String, parentFolderId: String? = null): DriveFolderListing<PlaylistItem> {
        return try {
            val parentId = parentFolderId ?: run {
                val file = executeWithTokenRetry { token ->
                    retryWithBackoff {
                        driveApi.getFile(
                            accessToken = "Bearer $token",
                            fileId = fileId,
                            fields = "id, name, parents"
                        )
                    }
                }
                file.parents?.firstOrNull()
            }
                ?: return DriveFolderListing(emptyList(), isComplete = true)
            val query = "'$parentId' in parents and trashed=false and mimeType != 'application/vnd.google-apps.folder'"
            when (val result = getFiles(query, pageToken = null, pageSize = 100, orderBy = "name")) {
                is Resource.Success -> result.data.toPlaylistPage(parentId)
                is Resource.Error -> DriveFolderListing(
                    items = emptyList(), isComplete = false,
                    message = result.message ?: "Não foi possível carregar a playlist do Drive.",
                    sourceFolderId = parentId
                )
                is Resource.Loading -> DriveFolderListing(
                    items = emptyList(), isComplete = false,
                    message = "A playlist ainda está carregando.", sourceFolderId = parentId
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to get sibling files for fileId=$fileId: ${e.message}")
            DriveFolderListing(emptyList(), isComplete = false, message = "Não foi possível carregar a playlist do Drive.")
        }
    }

    suspend fun getNextFileSiblingPage(parentFolderId: String, pageToken: String): DriveFolderListing<PlaylistItem> {
        val query = "'$parentFolderId' in parents and trashed=false and mimeType != 'application/vnd.google-apps.folder'"
        return when (val result = getFiles(query, pageToken, pageSize = 100, orderBy = "name")) {
            is Resource.Success -> result.data.toPlaylistPage(parentFolderId)
            is Resource.Error -> DriveFolderListing(
                items = emptyList(), isComplete = false,
                message = result.message ?: "Não foi possível continuar a playlist do Drive.",
                nextPageToken = pageToken, sourceFolderId = parentFolderId
            )
            is Resource.Loading -> DriveFolderListing(
                items = emptyList(), isComplete = false,
                message = "A playlist ainda está carregando.",
                nextPageToken = pageToken, sourceFolderId = parentFolderId
            )
        }
    }

    suspend fun getFolderSubtitles(fileId: String, parentFolderId: String? = null): DriveFolderListing<SubtitleItem> {
        return try {
            val parentId = parentFolderId ?: run {
                val file = executeWithTokenRetry { token ->
                    retryWithBackoff {
                        driveApi.getFile(
                            accessToken = "Bearer $token",
                            fileId = fileId,
                            fields = "id, name, parents"
                        )
                    }
                }
                file.parents?.firstOrNull()
            }
                ?: return DriveFolderListing(emptyList(), isComplete = true)
            val query = "'$parentId' in parents and trashed=false and mimeType != 'application/vnd.google-apps.folder'"
            when (val result = getFiles(query, pageToken = null, pageSize = 100, orderBy = "name")) {
                is Resource.Success -> result.data.toSubtitlePage(parentId)
                is Resource.Error -> DriveFolderListing(
                    items = emptyList(), isComplete = false,
                    message = result.message ?: "Não foi possível carregar as legendas do Drive.",
                    sourceFolderId = parentId
                )
                is Resource.Loading -> DriveFolderListing(
                    items = emptyList(), isComplete = false,
                    message = "As legendas ainda estão carregando.", sourceFolderId = parentId
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to get subtitles for fileId=$fileId: ${e.message}")
            DriveFolderListing(emptyList(), isComplete = false, message = "Não foi possível carregar as legendas do Drive.")
        }
    }

    suspend fun getNextFolderSubtitlePage(parentFolderId: String, pageToken: String): DriveFolderListing<SubtitleItem> {
        val query = "'$parentFolderId' in parents and trashed=false and mimeType != 'application/vnd.google-apps.folder'"
        return when (val result = getFiles(query, pageToken, pageSize = 100, orderBy = "name")) {
            is Resource.Success -> result.data.toSubtitlePage(parentFolderId)
            is Resource.Error -> DriveFolderListing(
                items = emptyList(), isComplete = false,
                message = result.message ?: "Não foi possível continuar a leitura das legendas do Drive.",
                nextPageToken = pageToken, sourceFolderId = parentFolderId
            )
            is Resource.Loading -> DriveFolderListing(
                items = emptyList(), isComplete = false,
                message = "As legendas ainda estão carregando.",
                nextPageToken = pageToken, sourceFolderId = parentFolderId
            )
        }
    }

    private fun FilesResponse.toPlaylistPage(parentFolderId: String): DriveFolderListing<PlaylistItem> {
        val items = files.map { it.toDriveFile() }.filter { it.isVideoFile }
            .map { file ->
                val effectiveId = if (file.isShortcut && !file.shortcutDetails.targetId.isNullOrEmpty()) {
                    file.shortcutDetails.targetId
                } else file.id
                PlaylistItem(effectiveId, file.name, file.thumbnailLink)
            }.sortedWith { a, b ->
                zechs.drive.stream.utils.SeasonEpisodeGrouper.compareItems(
                    a.title, zechs.drive.stream.utils.EpisodeParser.parse(a.title),
                    b.title, zechs.drive.stream.utils.EpisodeParser.parse(b.title)
                )
            }
        return DriveFolderListing(
            items = items,
            isComplete = nextPageToken.isNullOrBlank(),
            nextPageToken = nextPageToken,
            sourceFolderId = parentFolderId
        )
    }

    private fun FilesResponse.toSubtitlePage(parentFolderId: String): DriveFolderListing<SubtitleItem> {
        val items = files.filter { file ->
            val name = file.name.lowercase()
            name.endsWith(".ass") || name.endsWith(".srt") || name.endsWith(".vtt") ||
                    name.endsWith(".ssa") || name.endsWith(".sub")
        }.map { file -> SubtitleItem(id = file.id, name = file.name) }
        return DriveFolderListing(
            items = items,
            isComplete = nextPageToken.isNullOrBlank(),
            nextPageToken = nextPageToken,
            sourceFolderId = parentFolderId
        )
    }

    suspend fun downloadSubtitle(sub: SubtitleItem, cacheDir: java.io.File): java.io.File? {
        val safeName = sub.name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val destFile = java.io.File(cacheDir, "subtitles/${sub.id}_$safeName")
        if (destFile.exists() && destFile.length() > 0) {
            Log.d(TAG, "Subtitle already in cache: ${destFile.absolutePath}")
            return destFile
        }
        return try {
            executeWithTokenRetry { token ->
                val response = driveApi.downloadFile("Bearer $token", sub.id)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    destFile.parentFile?.mkdirs()
                    body.byteStream().use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Log.d(TAG, "Subtitle downloaded successfully (${destFile.length()} bytes): ${destFile.absolutePath}")
                    destFile
                } else {
                    if (response.code() == 401) throw HttpException(response)
                    Log.w(TAG, "Failed to download subtitle fileId=${sub.id}: code=${response.code()}")
                    null
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Exception downloading subtitle ${sub.name}", e)
            null
        }
    }

}
