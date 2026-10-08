package zechs.drive.stream.data.remote

import java.net.URI

/** Adds the private-repository token only to trusted GitHub hosts. */
internal object GithubAuth {

    fun bearerFor(token: String): String? =
        token.trim().takeIf(String::isNotEmpty)?.let { "Bearer $it" }

    fun authorizationFor(url: String, token: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val path = uri.path ?: return null
        val isGithubWeb = host == "github.com" && path.startsWith("/takaokensei/makimono/releases/download/")
        val isGithubApi = host == "api.github.com" && path.startsWith("/repos/takaokensei/makimono/releases/assets/")
        if (!isGithubWeb && !isGithubApi) return null
        return bearerFor(token)
    }
}
