package zechs.drive.stream.data.remote

import retrofit2.http.GET
import zechs.drive.stream.data.model.LatestRelease

interface GithubApi {

    @GET("repos/takaokensei/makimono/releases/latest")
    suspend fun getLatestRelease(): LatestRelease

}