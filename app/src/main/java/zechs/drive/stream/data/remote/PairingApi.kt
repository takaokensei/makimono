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
}
