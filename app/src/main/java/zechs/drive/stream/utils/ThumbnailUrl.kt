package zechs.drive.stream.utils

/**
 * Drive thumbnail URLs are often returned with a `=s220` suffix. Keep the
 * original URL when its shape is unknown instead of appending an invalid query
 * string to it.
 */
object ThumbnailUrl {
    fun medium(url: String?): String? = resize(url, 480)

    fun large(url: String?): String? = resize(url, 720)

    fun poster(url: String?): String? = resize(url, 640)

    private fun resize(url: String?, size: Int): String? {
        if (url.isNullOrBlank()) return null

        return url
            .replace(Regex("=s\\d+(?:-[^?&]*)?"), "=s$size")
            .replace(Regex("=w\\d+-h\\d+"), "=w$size-h$size")
    }
}
