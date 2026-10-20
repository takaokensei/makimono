package zechs.drive.stream.data.model

import androidx.annotation.Keep
import com.squareup.moshi.Json

@Keep
data class PairingCreateRequest(
    @Json(name = "device_name") val deviceName: String,
    @Json(name = "client_version") val clientVersion: String,
    @Json(name = "protocol_version") val protocolVersion: Int = 1
)

@Keep
data class PairingCreateResponse(
    @Json(name = "pairing_id") val pairingId: String,
    @Json(name = "device_secret") val deviceSecret: String,
    @Json(name = "confirm_code") val confirmCode: String,
    @Json(name = "qr_url") val qrUrl: String,
    @Json(name = "expires_in") val expiresIn: Long,
    @Json(name = "poll_interval_seconds") val pollIntervalSeconds: Long = 3
)

@Keep
data class PairingStatusResponse(
    val state: String,
    @Json(name = "session_token") val sessionToken: String? = null,
    @Json(name = "expires_in") val expiresIn: Long? = null
)

@Keep
data class DriveAccessTokenResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "expires_in") val expiresIn: Long
)
