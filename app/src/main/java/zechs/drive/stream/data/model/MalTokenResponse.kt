package zechs.drive.stream.data.model

import androidx.annotation.Keep
import com.squareup.moshi.Json

@Keep
data class MalTokenResponse(
    @Json(name = "token_type") val tokenType: String,
    @Json(name = "expires_in") val expiresIn: Long,
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String
)
