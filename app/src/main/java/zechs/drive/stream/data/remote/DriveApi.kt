package zechs.drive.stream.data.remote

import retrofit2.Response
import retrofit2.http.*
import zechs.drive.stream.data.model.DriveResponse
import zechs.drive.stream.data.model.FileUpdateRequest
import zechs.drive.stream.data.model.FilesResponse

interface DriveApi {

    @GET("/drive/v3/files")
    suspend fun getFiles(
        @Header("Authorization")
        accessToken: String,
        @Query("supportsAllDrives")
        supportsAllDrives: Boolean = true,
        @Query("includeItemsFromAllDrives")
        includeItemsFromAllDrives: Boolean = true,
        @Query("pageSize")
        pageSize: Int = 25,
        @Query("pageToken")
        pageToken: String? = null,
        @Query("fields")
        fields: String = "nextPageToken, files(id, name, size, mimeType, iconLink, thumbnailLink, shortcutDetails, starred)",
        @Query("orderBy")
        orderBy: String = "folder, name",
        @Query("q")
        q: String
    ): FilesResponse

    @GET("/drive/v3/drives")
    suspend fun getDrives(
        @Header("Authorization")
        accessToken: String,
        @Query("pageSize")
        pageSize: Int = 25,
        @Query("pageToken")
        pageToken: String? = null,
        @Query("fields")
        fields: String = "nextPageToken, drives(id, name, kind)"
    ): DriveResponse

    @GET("/drive/v3/files/{fileId}")
    suspend fun getFile(
        @Header("Authorization")
        accessToken: String,
        @Path("fileId")
        fileId: String,
        @Query("supportsAllDrives")
        supportsAllDrives: Boolean = true,
        @Query("fields")
        fields: String = "id, name, parents"
    ): zechs.drive.stream.data.model.FileDetailsResponse

    @PATCH("/drive/v3/files/{fileId}")
    suspend fun updateFile(
        @Header("Authorization")
        accessToken: String,
        @Path("fileId")
        fileId: String,
        @Query("supportsAllDrives")
        supportsAllDrives: Boolean = true,
        @Query("includeItemsFromAllDrives")
        includeItemsFromAllDrives: Boolean = true,
        @Body fileUpdateRequest: FileUpdateRequest
    ): Response<Unit>

    @Streaming
    @GET("/drive/v3/files/{fileId}?supportsAllDrives=true&alt=media")
    suspend fun downloadFile(
        @Header("Authorization")
        accessToken: String,
        @Path("fileId")
        fileId: String
    ): Response<okhttp3.ResponseBody>

}