package zechs.drive.stream.data.repository

import retrofit2.HttpException
import zechs.drive.stream.utils.state.ResourceErrorKind
import java.io.IOException
import java.util.Locale

/** Maps provider failures into stable UI/domain categories without exposing provider payloads. */
internal object DriveFailureClassifier {
    fun fromHttp(statusCode: Int, reason: String? = null): ResourceErrorKind {
        val normalizedReason = reason.orEmpty().lowercase(Locale.ROOT)
        if (statusCode == 429 || normalizedReason.contains("ratelimit") ||
            normalizedReason.contains("quota") || normalizedReason.contains("userratelimit")
        ) {
            return ResourceErrorKind.RATE_LIMITED
        }
        return when (statusCode) {
            401 -> ResourceErrorKind.AUTHENTICATION
            403 -> ResourceErrorKind.ACCESS_DENIED
            404 -> ResourceErrorKind.NOT_FOUND
            in 500..599 -> ResourceErrorKind.SERVER
            else -> ResourceErrorKind.UNKNOWN
        }
    }

    fun fromException(error: Exception): ResourceErrorKind = when (error) {
        is HttpException -> fromHttp(error.code())
        is IOException -> ResourceErrorKind.NETWORK
        else -> ResourceErrorKind.UNKNOWN
    }
}
