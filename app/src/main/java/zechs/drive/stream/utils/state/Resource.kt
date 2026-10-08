package zechs.drive.stream.utils.state

enum class ResourceErrorKind {
    AUTHENTICATION,
    ACCESS_DENIED,
    RATE_LIMITED,
    NOT_FOUND,
    NETWORK,
    SERVER,
    UNKNOWN
}

sealed class Resource<T>(
    open val data: T? = null,
    open val message: String? = null,
    open val errorKind: ResourceErrorKind? = null,
) {
    // `data` is redeclared here with a non-null type. Kotlin lets a subclass
    // narrow a nullable `open` property to non-null, so every call site that
    // does `is Resource.Success -> ... result.data` gets a smart-cast to a
    // non-null value from the compiler instead of needing `!!`.
    class Success<T>(override val data: T) : Resource<T>(data)

    // Same idea for `message`: every Error is guaranteed to carry a
    // human-readable message, so callers can rely on it without `!!`.
    class Error<T>(
        override val message: String,
        data: T? = null,
        override val errorKind: ResourceErrorKind = ResourceErrorKind.UNKNOWN
    ) : Resource<T>(data, message, errorKind)

    class Loading<T> : Resource<T>()
}
