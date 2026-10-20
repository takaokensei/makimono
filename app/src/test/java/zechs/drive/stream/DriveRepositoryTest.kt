package zechs.drive.stream

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import zechs.drive.stream.data.model.File
import zechs.drive.stream.data.model.FileCollectionResult
import zechs.drive.stream.data.model.DriveQueryContext
import zechs.drive.stream.data.model.FileDetailsResponse
import zechs.drive.stream.data.model.FilesResponse
import zechs.drive.stream.data.remote.DriveApi
import zechs.drive.stream.data.repository.DriveRepository
import zechs.drive.stream.data.repository.TokenProvider
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.utils.state.ResourceErrorKind

class DriveRepositoryTest {

    private lateinit var driveApi: DriveApi
    private lateinit var tokenProvider: TokenProvider
    private lateinit var repository: DriveRepository

    @Before
    fun setUp() {
        driveApi = mockk()
        tokenProvider = mockk {
            coEvery { validToken() } returns "valid_test_access_token"
            coEvery { refresh() } returns "refreshed_test_access_token"
        }
        repository = DriveRepository(driveApi, tokenProvider)
    }

    private fun createFile(id: String, name: String) = File(
        id = id,
        name = name,
        size = 1000L,
        mimeType = "video/mp4",
        iconLink = "https://example.com/icon.png",
        thumbnailLink = null
    )

    @Test
    fun driveDescriptionIsPreservedForFocusedEpisodeDetails() {
        val responseFile = createFile("episode-1", "Episode 01.mkv").copy(
            description = "A user-authored episode description."
        )

        assertEquals("A user-authored episode description.", responseFile.toDriveFile().description)
    }

    @Test
    fun getAllFiles_singlePage_returnsAllFiles() = runBlocking {
        val pageFiles = listOf(createFile("f1", "Movie 1.mp4"), createFile("f2", "Movie 2.mp4"))
        coEvery {
            driveApi.getFiles(
                accessToken = "Bearer valid_test_access_token",
                q = any(),
                pageSize = any(),
                pageToken = null,
                supportsAllDrives = any(),
                includeItemsFromAllDrives = any(),
                fields = any(),
                orderBy = any()
            )
        } returns FilesResponse(files = pageFiles, nextPageToken = null)

        val result = repository.getAllFiles(query = "trashed=false")
        assertTrue(result is Resource.Success)
        val files = (result as Resource.Success).data
        assertEquals(2, files.size)
        assertEquals("f1", files[0].id)
        assertEquals("f2", files[1].id)
    }

    @Test
    fun getAllFiles_multiplePages_accumulatesAcrossPages() = runBlocking {
        val page1 = listOf(createFile("f1", "Ep 1.mp4"))
        val page2 = listOf(createFile("f2", "Ep 2.mp4"))

        coEvery {
            driveApi.getFiles(
                accessToken = any(),
                q = any(),
                pageSize = any(),
                pageToken = null,
                supportsAllDrives = any(),
                includeItemsFromAllDrives = any(),
                fields = any(),
                orderBy = any()
            )
        } returns FilesResponse(files = page1, nextPageToken = "token_page_2")

        coEvery {
            driveApi.getFiles(
                accessToken = any(),
                q = any(),
                pageSize = any(),
                pageToken = "token_page_2",
                supportsAllDrives = any(),
                includeItemsFromAllDrives = any(),
                fields = any(),
                orderBy = any()
            )
        } returns FilesResponse(files = page2, nextPageToken = null)

        val result = repository.getAllFiles(query = "trashed=false")
        assertTrue(result is Resource.Success)
        val files = (result as Resource.Success).data
        assertEquals(2, files.size)
        assertEquals("f1", files[0].id)
        assertEquals("f2", files[1].id)
    }

    @Test
    fun getAllFilesDetailed_overlappingPagesDeduplicatesAndKeepsLatestMetadata() = runBlocking {
        val firstPage = listOf(
            createFile("f1", "Episode 01.mkv"),
            createFile("f2", "Episode 02 (old name).mkv")
        )
        val secondPage = listOf(
            createFile("f2", "Episode 02.mkv"),
            createFile("f3", "Episode 03.mkv")
        )
        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = any(), pageToken = null,
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } returns FilesResponse(files = firstPage, nextPageToken = "page-2")
        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = any(), pageToken = "page-2",
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } returns FilesResponse(files = secondPage, nextPageToken = null)

        val result = repository.getAllFilesDetailed(query = "trashed=false")

        assertTrue(result is Resource.Success)
        val collection = (result as Resource.Success).data
        assertTrue(collection.isComplete)
        assertEquals(listOf("f1", "f2", "f3"), collection.files.map { it.id })
        assertEquals("Episode 02.mkv", collection.files[1].name)
        assertEquals(2, collection.pagesFetched)
    }

    @Test
    fun getAllFilesDetailed_pageLimitReturnsCursorAndMarksIncomplete() = runBlocking {
        val page = listOf(createFile("f1", "Ep 1.mp4"))
        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = any(), pageToken = null,
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } returns FilesResponse(files = page, nextPageToken = "page-2")

        val result = repository.getAllFilesDetailed(
            query = "'season-42' in parents and trashed=false",
            pageSize = 12,
            maxPages = 1,
            orderBy = "name desc"
        )

        assertTrue(result is Resource.Success)
        val collection = (result as Resource.Success).data
        assertEquals(listOf("f1"), collection.files.map { it.id })
        assertFalse(collection.isComplete)
        assertEquals("page-2", collection.nextPageToken)
        assertEquals(1, collection.pagesFetched)
        assertEquals(
            DriveQueryContext("'season-42' in parents and trashed=false", 12, "name desc"),
            collection.queryContext
        )
        assertEquals(FileCollectionResult.IncompleteReason.PAGE_LIMIT, collection.incompleteReason)
    }

    @Test
    fun getAllFilesDetailed_moreThan2500FilesKeepsCursorAndSignalsIncomplete() = runBlocking {
        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = any(), pageToken = any(),
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } coAnswers {
            val pageToken = arg<String?>(4)
            val pageIndex = pageToken?.removePrefix("page-")?.toIntOrNull() ?: 0
            val requestedPageSize = arg<Int>(3)
            val files = (0 until requestedPageSize).map { index ->
                val number = pageIndex * 100 + index + 1
                createFile("file-$number", "Episode $number.mkv")
            }
            FilesResponse(files = files, nextPageToken = "page-${pageIndex + 1}")
        }

        val result = repository.getAllFilesDetailed(query = "trashed=false")

        assertTrue(result is Resource.Success)
        val collection = (result as Resource.Success).data
        assertEquals(2_500, collection.files.size)
        assertEquals(25, collection.pagesFetched)
        assertFalse(collection.isComplete)
        assertEquals("page-25", collection.nextPageToken)
        assertEquals(FileCollectionResult.IncompleteReason.FILE_LIMIT, collection.incompleteReason)
    }

    @Test
    fun getAllFiles_usesErrorWithPartialDataWhenPageLimitReached() = runBlocking {
        val page = listOf(createFile("f1", "Ep 1.mp4"))
        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = any(), pageToken = null,
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } returns FilesResponse(files = page, nextPageToken = "page-2")

        val result = repository.getAllFiles(query = "trashed=false", maxPages = 1)

        assertTrue(result is Resource.Error)
        val partialFiles = (result as Resource.Error).data
        assertEquals(listOf("f1"), partialFiles?.map { it.id })
        assertTrue(result.message.contains("incompleta"))
    }

    @Test
    fun getAllFilesDetailed_failureAfterPagesPreservesFilesAndFailedCursor() = runBlocking {
        val first = listOf(createFile("f1", "Ep 1.mp4"))
        val second = listOf(createFile("f2", "Ep 2.mp4"))
        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = any(), pageToken = null,
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } returns FilesResponse(files = first, nextPageToken = "page-2")
        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = any(), pageToken = "page-2",
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } returns FilesResponse(files = second, nextPageToken = "page-3")
        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = any(), pageToken = "page-3",
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } throws HttpException(Response.error<FilesResponse>(403, "{}".toResponseBody()))

        val result = repository.getAllFilesDetailed(query = "trashed=false")

        assertTrue(result is Resource.Error)
        val partial = (result as Resource.Error).data
        assertEquals(listOf("f1", "f2"), partial?.files?.map { it.id })
        assertFalse(partial!!.isComplete)
        assertEquals("page-3", partial.nextPageToken)
        assertEquals(2, partial.pagesFetched)
        assertEquals(FileCollectionResult.IncompleteReason.REQUEST_FAILED, partial.incompleteReason)
    }

    @Test
    fun getAllFiles_apiError_returnsResourceError() = runBlocking {
        val response = Response.error<FilesResponse>(
            401,
            "{\"error\":\"Unauthorized\"}".toResponseBody()
        )
        coEvery { tokenProvider.refresh() } returns null
        coEvery {
            driveApi.getFiles(
                accessToken = any(),
                q = any(),
                pageSize = any(),
                pageToken = any(),
                supportsAllDrives = any(),
                includeItemsFromAllDrives = any(),
                fields = any(),
                orderBy = any()
            )
        } throws HttpException(response)

        val result = repository.getAllFiles(query = "trashed=false")
        assertTrue(result is Resource.Error)
        assertEquals(ResourceErrorKind.AUTHENTICATION, result.errorKind)
        val message = (result as Resource.Error).message
        assertTrue(message.contains("conecte sua conta", ignoreCase = true))
    }

    @Test
    fun getFiles_nullAccessToken_returnsError() = runBlocking {
        val nullTokenProvider = mockk<TokenProvider> {
            coEvery { validToken() } returns null
        }
        val repo = DriveRepository(driveApi, nullTokenProvider)

        val result = repo.getFiles(query = "trashed=false", pageToken = null, pageSize = 25)
        assertTrue(result is Resource.Error)
        assertEquals(ResourceErrorKind.AUTHENTICATION, result.errorKind)
        assertEquals("Conecte sua conta do Google Drive para continuar.", result.message)
    }

    @Test
    fun doOnError_http403_returnsPermissionError() = runBlocking {
        val response = Response.error<FilesResponse>(
            403,
            "{\"error\":\"Forbidden\"}".toResponseBody()
        )
        coEvery {
            driveApi.getFiles(
                accessToken = any(),
                q = any(),
                pageSize = any(),
                pageToken = any(),
                supportsAllDrives = any(),
                includeItemsFromAllDrives = any(),
                fields = any(),
                orderBy = any()
            )
        } throws HttpException(response)

        val result = repository.getFiles(query = "trashed=false", pageToken = null, pageSize = 25)
        assertTrue(result is Resource.Error)
        val message = result.message ?: ""
        assertTrue(message.contains("Acesso negado") || message.contains("403"))
        assertEquals(ResourceErrorKind.ACCESS_DENIED, result.errorKind)
    }

    @Test
    fun doOnError_http429_returnsRateLimitError() = runBlocking {
        val response = Response.error<FilesResponse>(
            429,
            "{\"error\":\"Too Many Requests\"}".toResponseBody()
        )
        coEvery {
            driveApi.getFiles(
                accessToken = any(),
                q = any(),
                pageSize = any(),
                pageToken = any(),
                supportsAllDrives = any(),
                includeItemsFromAllDrives = any(),
                fields = any(),
                orderBy = any()
            )
        } throws HttpException(response)

        val result = repository.getFiles(query = "trashed=false", pageToken = null, pageSize = 25)
        assertTrue(result is Resource.Error)
        val message = result.message ?: ""
        assertTrue(message.contains("Muitas requisições") || message.contains("429"))
        assertEquals(ResourceErrorKind.RATE_LIMITED, result.errorKind)
    }

    @Test
    fun getFileSiblings_returnsFirstPageAndCursorImmediately() = runBlocking {
        coEvery {
            driveApi.getFile(
                accessToken = any(),
                fileId = "file_ep1",
                fields = any(),
                supportsAllDrives = any()
            )
        } returns FileDetailsResponse(id = "file_ep1", name = "Ep 01.mkv", parents = listOf("parent_folder_1"))

        val page1 = listOf(createFile("s1", "Episode 01.mkv"))
        val page2 = listOf(createFile("s2", "Episode 02.mkv"))

        coEvery {
            driveApi.getFiles(
                accessToken = any(),
                q = any(),
                pageSize = any(),
                pageToken = null,
                supportsAllDrives = any(),
                includeItemsFromAllDrives = any(),
                fields = any(),
                orderBy = any()
            )
        } returns FilesResponse(files = page1, nextPageToken = "token_p2")

        val siblings = repository.getFileSiblings("file_ep1")
        assertFalse(siblings.isComplete)
        assertEquals("token_p2", siblings.nextPageToken)
        assertEquals("parent_folder_1", siblings.sourceFolderId)
        assertEquals(1, siblings.items.size)
        assertEquals("s1", siblings.items[0].fileId)

        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = any(), pageToken = "token_p2",
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } returns FilesResponse(files = page2, nextPageToken = null)
        val followingPage = repository.getNextFileSiblingPage("parent_folder_1", "token_p2")
        assertTrue(followingPage.isComplete)
        assertEquals("s2", followingPage.items.single().fileId)
    }

    @Test
    fun getFolderSubtitles_returnsFirstPageAndCursorImmediately() = runBlocking {
        coEvery {
            driveApi.getFile(
                accessToken = any(),
                fileId = "file_ep1",
                fields = any(),
                supportsAllDrives = any()
            )
        } returns FileDetailsResponse(id = "file_ep1", name = "Ep 01.mkv", parents = listOf("parent_folder_1"))

        val page1 = listOf(
            File("sub1", "Episode 01.ass", 500L, "https://example.com/icon.png", "text/x-ssa"),
            File("ignored_txt", "README.txt", 100L, "https://example.com/icon.png", "text/plain")
        )
        val page2 = listOf(
            File("sub2", "Episode 02.srt", 400L, "https://example.com/icon.png", "text/plain")
        )

        coEvery {
            driveApi.getFiles(
                accessToken = any(),
                q = any(),
                pageSize = any(),
                pageToken = null,
                supportsAllDrives = any(),
                includeItemsFromAllDrives = any(),
                fields = any(),
                orderBy = any()
            )
        } returns FilesResponse(files = page1, nextPageToken = "sub_p2")

        val subtitles = repository.getFolderSubtitles("file_ep1")
        assertFalse(subtitles.isComplete)
        assertEquals("sub_p2", subtitles.nextPageToken)
        assertEquals("parent_folder_1", subtitles.sourceFolderId)
        assertEquals(1, subtitles.items.size)
        assertEquals("sub1", subtitles.items[0].id)

        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = any(), pageToken = "sub_p2",
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } returns FilesResponse(files = page2, nextPageToken = null)
        val followingPage = repository.getNextFolderSubtitlePage("parent_folder_1", "sub_p2")
        assertTrue(followingPage.isComplete)
        assertEquals("sub2", followingPage.items.single().id)
    }

    @Test
    fun getNextFileSiblingPageUsesCursorAndReturnsNextCursor() = runBlocking {
        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = 100, pageToken = "video_p2",
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } returns FilesResponse(
            files = listOf(createFile("ep12", "Series S01E12.mkv")),
            nextPageToken = "video_p3"
        )

        val page = repository.getNextFileSiblingPage("season_folder", "video_p2")

        assertFalse(page.isComplete)
        assertEquals("season_folder", page.sourceFolderId)
        assertEquals("video_p3", page.nextPageToken)
        assertEquals("ep12", page.items.single().fileId)
    }

    @Test
    fun getFileSiblingsUsesCallerFolderForShortcuts() = runBlocking {
        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = any(), pageToken = null,
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } returns FilesResponse(files = listOf(createFile("shortcut_ep1", "Series S01E01.mkv")), nextPageToken = null)

        val result = repository.getFileSiblings("target_file_ep1", "folder_with_shortcuts")

        assertTrue(result.isComplete)
        assertEquals("folder_with_shortcuts", result.sourceFolderId)
        assertEquals("shortcut_ep1", result.items.single().fileId)
    }

    @Test
    fun getNextFolderSubtitlePageFiltersAndReturnsCompletion() = runBlocking {
        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = 100, pageToken = "subtitle_p2",
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } returns FilesResponse(
            files = listOf(
                File("sub3", "Episode 03.pt-BR.ass", 300L, "https://example.com/icon.png", "text/x-ssa"),
                File("readme", "README.txt", 50L, "https://example.com/icon.png", "text/plain")
            ),
            nextPageToken = null
        )

        val page = repository.getNextFolderSubtitlePage("season_folder", "subtitle_p2")

        assertTrue(page.isComplete)
        assertEquals("season_folder", page.sourceFolderId)
        assertEquals(1, page.items.size)
        assertEquals("sub3", page.items.single().id)
    }

    @Test
    fun getFileSiblings_secondPageFailurePreservesCursorForRetry() = runBlocking {
        coEvery {
            driveApi.getFile(accessToken = any(), fileId = "file_ep1", fields = any(), supportsAllDrives = any())
        } returns FileDetailsResponse(id = "file_ep1", name = "Ep 01.mkv", parents = listOf("parent_folder_1"))
        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = any(), pageToken = null,
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } returns FilesResponse(files = listOf(createFile("ep1", "Episode 01.mkv")), nextPageToken = "page_2")
        val initial = repository.getFileSiblings("file_ep1")
        assertEquals("ep1", initial.items.single().fileId)
        assertEquals("page_2", initial.nextPageToken)

        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = any(), pageToken = "page_2",
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } throws HttpException(Response.error<FilesResponse>(403, "{}".toResponseBody()))
        val failedPage = repository.getNextFileSiblingPage("parent_folder_1", "page_2")

        assertFalse(failedPage.isComplete)
        assertTrue(failedPage.items.isEmpty())
        assertEquals("page_2", failedPage.nextPageToken)
        assertTrue(failedPage.message.orEmpty().isNotBlank())
    }

    @Test
    fun getFolderSubtitles_secondPageFailurePreservesCursorForRetry() = runBlocking {
        coEvery {
            driveApi.getFile(accessToken = any(), fileId = "file_ep1", fields = any(), supportsAllDrives = any())
        } returns FileDetailsResponse(id = "file_ep1", name = "Ep 01.mkv", parents = listOf("parent_folder_1"))
        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = any(), pageToken = null,
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } returns FilesResponse(
            files = listOf(File("sub1", "Episode 01.pt-BR.srt", 500L, "https://example.com/icon.png", "text/plain")),
            nextPageToken = "page_2"
        )
        val initial = repository.getFolderSubtitles("file_ep1")
        assertEquals("sub1", initial.items.single().id)
        assertEquals("page_2", initial.nextPageToken)

        coEvery {
            driveApi.getFiles(
                accessToken = any(), q = any(), pageSize = any(), pageToken = "page_2",
                supportsAllDrives = any(), includeItemsFromAllDrives = any(), fields = any(), orderBy = any()
            )
        } throws HttpException(Response.error<FilesResponse>(403, "{}".toResponseBody()))
        val failedPage = repository.getNextFolderSubtitlePage("parent_folder_1", "page_2")

        assertFalse(failedPage.isComplete)
        assertTrue(failedPage.items.isEmpty())
        assertEquals("page_2", failedPage.nextPageToken)
        assertTrue(failedPage.message.orEmpty().isNotBlank())
    }

    @Test
    fun retryWithBackoff_retriesOnTransientErrorAndSucceeds() = runBlocking {
        var callCount = 0
        val result = repository.retryWithBackoff(times = 3, initialDelayMs = 10L, maxDelayMs = 50L) {
            callCount++
            if (callCount < 2) {
                throw java.io.IOException("Temporary network glitch")
            }
            "success_payload"
        }
        assertEquals("success_payload", result)
        assertEquals(2, callCount)
    }
}
