package zechs.drive.stream.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import zechs.drive.stream.data.model.DriveAccessTokenResponse
import zechs.drive.stream.data.model.PairingCreateRequest
import zechs.drive.stream.data.model.PairingCreateResponse
import zechs.drive.stream.data.model.PairingStatusResponse
import zechs.drive.stream.data.model.SeedboxCreateJobRequest
import zechs.drive.stream.data.model.SeedboxCreateJobResponse
import zechs.drive.stream.data.model.SeedboxJobsListResponse
import zechs.drive.stream.data.model.SeedboxStatusResponse
import zechs.drive.stream.data.model.SeedboxTokenResponse

interface PairingApi {
    @POST("v1/pairings")
    suspend fun createPairing(@Body request: PairingCreateRequest): PairingCreateResponse

    @GET("v1/pairings/{pairingId}")
    suspend fun getPairingStatus(
        @Path("pairingId") pairingId: String,
        @Header("X-Device-Secret") deviceSecret: String
    ): PairingStatusResponse

    @GET("v1/drive-token")
    suspend fun getDriveToken(@Header("Authorization") authorization: String): DriveAccessTokenResponse

    @POST("v1/logout")
    suspend fun logout(@Header("Authorization") authorization: String): Response<Unit>

    @POST("v1/disconnect-all")
    suspend fun disconnectAll(@Header("Authorization") authorization: String): Response<Unit>

    @DELETE("v1/pairings/{pairingId}/cancel")
    suspend fun cancelPairing(
        @Path("pairingId") pairingId: String,
        @Header("X-Device-Secret") deviceSecret: String
    ): Response<Unit>

    @POST("v1/seedbox/token")
    suspend fun createSeedboxToken(@Header("Authorization") authorization: String): SeedboxTokenResponse

    @GET("v1/seedbox/status")
    suspend fun getSeedboxStatus(@Header("Authorization") authorization: String): SeedboxStatusResponse

    @POST("v1/seedbox/jobs")
    suspend fun createSeedboxJob(
        @Header("Authorization") authorization: String,
        @Body request: SeedboxCreateJobRequest
    ): SeedboxCreateJobResponse

    @GET("v1/seedbox/jobs")
    suspend fun getSeedboxJobs(@Header("Authorization") authorization: String): SeedboxJobsListResponse

    @DELETE("v1/seedbox/jobs/{jobId}")
    suspend fun cancelSeedboxJob(
        @Header("Authorization") authorization: String,
        @Path("jobId") jobId: String
    ): Response<Unit>

    @DELETE("v1/seedbox/jobs")
    suspend fun cancelAllSeedboxJobs(
        @Header("Authorization") authorization: String
    ): Response<Unit>
}
