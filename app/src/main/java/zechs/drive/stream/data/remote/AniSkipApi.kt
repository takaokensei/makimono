package zechs.drive.stream.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import zechs.drive.stream.data.model.AniSkipResponse

interface AniSkipApi {

    @GET("v2/skip-times/{malId}/{episodeNumber}")
    suspend fun getSkipTimes(
        @Path("malId") malId: Long,
        @Path("episodeNumber") episodeNumber: Int,
        @Query("types[]") types: List<String> = listOf("op", "ed", "mixed-ed", "recap"),
        @Query("episodeLength") episodeLength: Double = 0.0
    ): Response<AniSkipResponse>
}
