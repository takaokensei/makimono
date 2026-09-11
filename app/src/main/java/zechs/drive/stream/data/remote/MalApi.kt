package zechs.drive.stream.data.remote

import retrofit2.Response
import retrofit2.http.*
import zechs.drive.stream.data.model.MalAnimeNode
import zechs.drive.stream.data.model.MalAnimeSearchResponse
import zechs.drive.stream.data.model.MalTokenResponse
import zechs.drive.stream.data.model.MalUserProfile
import zechs.drive.stream.data.model.MalUserListStatus

interface MalApi {

    @GET("v2/anime")
    suspend fun searchAnime(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
        @Query("fields") fields: String = "id,title,main_picture,num_episodes,status,synopsis,mean,my_list_status",
        @Header("Authorization") authHeader: String? = null,
        @Header("X-MAL-CLIENT-ID") clientIdHeader: String? = null
    ): Response<MalAnimeSearchResponse>

    @GET("v2/anime/{anime_id}")
    suspend fun getAnimeDetails(
        @Path("anime_id") animeId: Long,
        @Query("fields") fields: String = "id,title,main_picture,num_episodes,status,synopsis,mean,my_list_status",
        @Header("Authorization") authHeader: String? = null,
        @Header("X-MAL-CLIENT-ID") clientIdHeader: String? = null
    ): Response<MalAnimeNode>

    @FormUrlEncoded
    @PATCH("v2/anime/{anime_id}/my_list_status")
    suspend fun updateListStatus(
        @Path("anime_id") animeId: Long,
        @Header("Authorization") authHeader: String,
        @Field("status") status: String? = null,
        @Field("num_watched_episodes") numWatchedEpisodes: Int? = null,
        @Field("score") score: Int? = null
    ): Response<MalUserListStatus>

    @GET("v2/users/@me")
    suspend fun getCurrentUser(
        @Header("Authorization") authHeader: String
    ): Response<MalUserProfile>

    @FormUrlEncoded
    @POST("https://myanimelist.net/v1/oauth2/token")
    suspend fun exchangeToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("redirect_uri") redirectUri: String
    ): Response<MalTokenResponse>

    @FormUrlEncoded
    @POST("https://myanimelist.net/v1/oauth2/token")
    suspend fun refreshToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String
    ): Response<MalTokenResponse>

}
