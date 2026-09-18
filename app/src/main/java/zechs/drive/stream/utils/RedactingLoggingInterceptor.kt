package zechs.drive.stream.utils

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

/**
 * SEC-01: Redacting logging interceptor to prevent sensitive credentials
 * (Authorization tokens, refresh tokens, client secrets, session cookies)
 * from being leaked into Android Logcat or CI test logs.
 */
class RedactingLoggingInterceptor(
    level: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.HEADERS,
    private val logger: (String) -> Unit = { message -> Log.d(TAG, message) }
) : Interceptor {

    private val httpLoggingInterceptor = HttpLoggingInterceptor { message ->
        logger(redact(message))
    }.apply {
        this.level = level
        redactHeader("Authorization")
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
        redactHeader("X-Goog-Api-Key")
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        return httpLoggingInterceptor.intercept(chain)
    }

    companion object {
        const val TAG = "OkHttp"

        private val BEARER_REGEX = Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~+/-]+")
        private val CLIENT_SECRET_QUERY = Regex("(?i)(client_secret=)[^&\\s]+")
        private val REFRESH_TOKEN_QUERY = Regex("(?i)(refresh_token=)[^&\\s]+")
        private val ACCESS_TOKEN_QUERY = Regex("(?i)(access_token=)[^&\\s]+")
        private val CODE_QUERY = Regex("(?i)(code=)[^&\\s]+")
        private val JSON_SENSITIVE_KEYS = Regex("(?i)(\"(?:client_secret|refresh_token|access_token|code)\"\\s*:\\s*\")[^\"]+(\")")

        /**
         * Replaces any sensitive credential in [value] with [REDACTED].
         */
        fun redact(value: String): String {
            var result = value
            result = BEARER_REGEX.replace(result) { "${it.groupValues[1]}[REDACTED]" }
            result = CLIENT_SECRET_QUERY.replace(result, "$1[REDACTED]")
            result = REFRESH_TOKEN_QUERY.replace(result, "$1[REDACTED]")
            result = ACCESS_TOKEN_QUERY.replace(result, "$1[REDACTED]")
            result = CODE_QUERY.replace(result, "$1[REDACTED]")
            result = JSON_SENSITIVE_KEYS.replace(result, "$1[REDACTED]$2")
            return result
        }

        fun create(
            level: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.HEADERS,
            logger: (String) -> Unit = { message -> Log.d(TAG, message) }
        ): HttpLoggingInterceptor {
            return HttpLoggingInterceptor { message ->
                logger(redact(message))
            }.apply {
                this.level = level
                redactHeader("Authorization")
                redactHeader("Cookie")
                redactHeader("Set-Cookie")
                redactHeader("X-Goog-Api-Key")
            }
        }
    }
}
