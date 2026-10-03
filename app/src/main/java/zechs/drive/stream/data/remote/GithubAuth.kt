package zechs.drive.stream.data.remote

import java.net.URI

/** Adds the private-repository token only to trusted GitHub hosts. */
internal object GithubAuth {

    fun bearerFor(token: String): String? =
        token.trim().takeIf(String::isNotEmpty)?.let { "Bearer $it" }

    fun authorizationFor(url: String, token: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host?.lowercase() != "github.com") return null
        if (!uri.path.startsWith("/takaokensei/makimono/releases/download/")) return null
        return bearerFor(token)
    }
}
