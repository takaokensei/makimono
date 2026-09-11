package zechs.drive.stream.data.model

import androidx.annotation.Keep
import com.squareup.moshi.Json

@Keep
data class AniSkipInterval(
    val startTime: Double,
    val endTime: Double
)

@Keep
data class AniSkipResult(
    val interval: AniSkipInterval,
    val skipType: String,
    val skipId: String? = null,
    val episodeLength: Double = 0.0
)

@Keep
data class AniSkipResponse(
    val found: Boolean = false,
    val results: List<AniSkipResult> = emptyList(),
    val message: String? = null,
    val statusCode: Int = 200
)
