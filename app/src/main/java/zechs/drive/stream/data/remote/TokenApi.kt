package zechs.drive.stream.data.remote

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import zechs.drive.stream.data.model.AuthorizationResponse
import zechs.drive.stream.data.model.TokenResponse

interface TokenApi {

    // P2-01: o endpoint de token do Google exige application/x-www-form-urlencoded
    // (RFC 6749). O antigo envio de @Body JSON funcionava apenas por tolerancia
    // do servidor — fragilidade silenciosa removida.
    @FormUrlEncoded
    @POST("/o/oauth2/token")
    suspend fun getAccessToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String
    ): TokenResponse

    @FormUrlEncoded
    @POST("/o/oauth2/token")
    suspend fun getRefreshToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") authCode: String,
        @Field("redirect_uri") redirectUri: String
    ): AuthorizationResponse

}