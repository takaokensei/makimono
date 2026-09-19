package zechs.drive.stream.data.remote

import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.GET
import zechs.drive.stream.data.model.LatestRelease

interface GithubApi {

    @Headers(
        "User-Agent: Makimono-App",
        "Accept: application/vnd.github+json"
    )
    @GET("repos/takaokensei/makimono/releases/latest")
    suspend fun getLatestRelease(
        @Header("Authorization") authorization: String? = null
    ): LatestRelease

}