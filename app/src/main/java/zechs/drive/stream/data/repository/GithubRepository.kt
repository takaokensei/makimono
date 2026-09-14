package zechs.drive.stream.data.repository

import android.util.Log
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zechs.drive.stream.BuildConfig
import zechs.drive.stream.data.model.LatestRelease
import zechs.drive.stream.data.remote.GithubApi
import zechs.drive.stream.utils.state.Resource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GithubRepository @Inject constructor(
    private val githubApi: Lazy<GithubApi>
) {

    companion object {
        private const val TAG = "GithubRepository"
    }

    suspend fun getLatestRelease(): Resource<LatestRelease> = withContext(Dispatchers.IO) {
        val token = BuildConfig.GITHUB_API_TOKEN.trim()
        if (token.isBlank()) {
            return@withContext Resource.Error(
                "Atualizações privadas não configuradas: defina github.apiToken em local.properties"
            )
        }

        try {
            val latest = githubApi.get().getLatestRelease("Bearer $token")
            Log.d(TAG, "Fetched latest release from API: ${latest.tagName}")
            return@withContext Resource.Success(latest)
        } catch (e: Exception) {
            Log.e(TAG, "Private GitHub release API failed", e)
            return@withContext Resource.Error(e.message ?: "Erro ao verificar atualizações privadas")
        }
    }

}
