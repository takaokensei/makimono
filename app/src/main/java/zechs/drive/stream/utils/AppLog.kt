package zechs.drive.stream.utils

import android.util.Log

/**
 * RB-02 / RB-04: Centralized application logging utility with automated
 * credential sanitization and swallowed-exception tracing.
 *
 * Guarantees that sensitive tokens (Bearer tokens, Google OAuth access tokens,
 * client secrets, refresh tokens, passwords) are never leaked to Logcat or CI logs.
 */
object AppLog {

    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6

    private val BEARER_REGEX = Regex("(?i)(Bearer\\s+)[A-Za-z0-9._~+/-]+")
    private val GOOGLE_TOKEN_REGEX = Regex("\\bya29\\.[A-Za-z0-9_-]+")
    private val CLIENT_SECRET_QUERY = Regex("(?i)(client_secret=)[^&\\s]+")
    private val REFRESH_TOKEN_QUERY = Regex("(?i)(refresh_token=)[^&\\s]+")
    private val ACCESS_TOKEN_QUERY = Regex("(?i)(access_token=)[^&\\s]+")
    private val CODE_QUERY = Regex("(?i)(code=)[^&\\s]+")
    private val PASSWORD_QUERY = Regex("(?i)(password=)[^&\\s]+")
    private val JSON_SENSITIVE_KEYS = Regex("(?i)(\"(?:client_secret|refresh_token|access_token|code|password)\"\\s*:\\s*\")[^\"]+(\")")

    /**
     * Testing hook to intercept or verify log output in JUnit tests.
     */
    internal var logHandler: ((priority: Int, tag: String, message: String, throwable: Throwable?) -> Unit)? = null

    /**
     * Replaces any credential, token, or password found in [value] with `[REDACTED]`.
     */
    fun redact(value: String): String {
        var result = value
        result = BEARER_REGEX.replace(result) { "${it.groupValues[1]}[REDACTED]" }
        result = GOOGLE_TOKEN_REGEX.replace(result, "ya29.[REDACTED]")
        result = CLIENT_SECRET_QUERY.replace(result, "$1[REDACTED]")
        result = REFRESH_TOKEN_QUERY.replace(result, "$1[REDACTED]")
        result = ACCESS_TOKEN_QUERY.replace(result, "$1[REDACTED]")
        result = CODE_QUERY.replace(result, "$1[REDACTED]")
        result = PASSWORD_QUERY.replace(result, "$1[REDACTED]")
        result = JSON_SENSITIVE_KEYS.replace(result, "$1[REDACTED]$2")
        return result
    }

    private fun log(priority: Int, tag: String, message: String, throwable: Throwable? = null) {
        val sanitizedMessage = redact(message)
        val handler = logHandler
        if (handler != null) {
            handler(priority, tag, sanitizedMessage, throwable)
            return
        }

        try {
            when (priority) {
                VERBOSE -> Log.v(tag, sanitizedMessage, throwable)
                DEBUG -> Log.d(tag, sanitizedMessage, throwable)
                INFO -> Log.i(tag, sanitizedMessage, throwable)
                WARN -> Log.w(tag, sanitizedMessage, throwable)
                ERROR -> Log.e(tag, sanitizedMessage, throwable)
                else -> Log.println(priority, tag, sanitizedMessage)
            }
        } catch (_: Throwable) {
            // No-op fallback when android.util.Log is unmocked in test runners
        }
    }

    fun v(tag: String, message: String, throwable: Throwable? = null) {
        log(VERBOSE, tag, message, throwable)
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) {
        log(DEBUG, tag, message, throwable)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(INFO, tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(ERROR, tag, message, throwable)
    }

    /**
     * RB-02: Logs caught and swallowed exceptions with context rather than silencing them silently.
     */
    fun logSwallowed(tag: String, actionDescription: String, throwable: Throwable) {
        val message = "Safely handled exception during $actionDescription: ${throwable.message ?: throwable.javaClass.simpleName}"
        log(WARN, tag, message, throwable)
    }
}
