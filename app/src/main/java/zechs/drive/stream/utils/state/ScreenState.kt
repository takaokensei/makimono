package zechs.drive.stream.utils.state

sealed interface ScreenState<out T> {

    /**
     * Initial loading state (e.g. cold start / spinner).
     */
    data object Loading : ScreenState<Nothing>

    /**
     * Successfully fetched content.
     * @param data The loaded data payload.
     * @param isRefreshing True if a background refresh is currently taking place.
     */
    data class Content<out T>(
        val data: T,
        val isRefreshing: Boolean = false
    ) : ScreenState<T>

    /**
     * Content is empty (e.g. empty folder or 0 search results).
     */
    data class Empty(
        val message: String? = null
    ) : ScreenState<Nothing>

    /**
     * Offline state showing cached data when available.
     */
    data class Offline<out T>(
        val cachedData: T? = null,
        val message: String? = null
    ) : ScreenState<T>

    /**
     * Error state with actionable diagnostics.
     */
    data class Error(
        val message: String,
        val statusCode: Int? = null,
        val isAuthError: Boolean = false,
        val isRateLimit: Boolean = false
    ) : ScreenState<Nothing> {
        val isSessionExpired: Boolean
            get() = isAuthError || statusCode == 401
    }
}
