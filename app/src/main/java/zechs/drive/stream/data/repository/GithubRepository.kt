package zechs.drive.stream.data.repository

import android.util.Log
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import zechs.drive.stream.data.model.LatestRelease
import zechs.drive.stream.data.model.ReleaseAsset
import zechs.drive.stream.data.remote.GithubApi
import zechs.drive.stream.utils.state.Resource
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class GithubRepository @Inject constructor(
    private val githubApi: Lazy<GithubApi>,
    @Named("OkHttpClient") private val okHttpClient: OkHttpClient
) {

    companion object {
        private const val TAG = "GithubRepository"
        private const val REPO_NAME = "takaokensei/makimono"
    }

    suspend fun getLatestRelease(): Resource<LatestRelease> = withContext(Dispatchers.IO) {
        // 1. Try standard Github API first
        try {
            val latest = githubApi.get().getLatestRelease()
            Log.d(TAG, "Fetched latest release from API: ${latest.tagName}")
            return@withContext Resource.Success(latest)
        } catch (e: Exception) {
            Log.w(TAG, "API call failed (${e.message}), attempting web release resolver fallback...", e)
        }

        // 2. Resilient Web Redirect Fallback (completely immune to 403 rate limits on api.github.com)
        try {
            val redirectClient = okHttpClient.newBuilder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val request = Request.Builder()
                .url("https://github.com/$REPO_NAME/releases/latest")
                .header("User-Agent", "Makimono-App/1.4")
                .header("Accept", "text/html,application/xhtml+xml")
                .build()

            val response = redirectClient.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            val tag = finalUrl.substringAfterLast("/").trim()

            if (tag.startsWith("v")) {
                Log.d(TAG, "Resolved latest release via web redirect: $tag ($finalUrl)")
                val abis = listOf("arm64-v8a", "armeabi-v7a", "universal", "x86", "x86_64")
                val assets = abis.map { abi ->
                    ReleaseAsset(
                        name = "makimono-$tag-$abi-release.apk",
                        browserDownloadUrl = "https://github.com/$REPO_NAME/releases/download/$tag/makimono-$tag-$abi-release.apk"
                    )
                }

                val release = LatestRelease(
                    name = "Makimono $tag",
                    tagName = tag,
                    htmlUrl = finalUrl,
                    assets = assets
                )
                return@withContext Resource.Success(release)
            } else {
                Log.e(TAG, "Invalid tag resolved from URL: $finalUrl")
                return@withContext Resource.Error("Não foi possível verificar a versão mais recente ($finalUrl)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Web redirect fallback also failed", e)
            return@withContext Resource.Error(e.message ?: "Erro ao verificar atualizações")
        }
    }

}
