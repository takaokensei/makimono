package zechs.drive.stream.data.repository

import zechs.drive.stream.utils.AppLog
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zechs.drive.stream.BuildConfig
import zechs.drive.stream.data.model.LatestRelease
import zechs.drive.stream.data.remote.GithubApi
import zechs.drive.stream.data.remote.GithubAuth
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
        try {
            val authorization = GithubAuth.bearerFor(BuildConfig.GITHUB_API_TOKEN)
            val latest = githubApi.get().getLatestRelease(authorization)
            AppLog.d(TAG, "Fetched latest release from API: ${latest.tagName}")
            return@withContext Resource.Success(latest)
        } catch (e: Exception) {
            AppLog.e(TAG, "GitHub release API failed", e)
            val msg = when (e) {
                is java.net.UnknownHostException -> "Sem conexão com o GitHub"
                is retrofit2.HttpException -> "GitHub retornou erro HTTP ${e.code()}"
                else -> e.localizedMessage ?: e.message ?: "Erro ao verificar atualizações"
            }
            return@withContext Resource.Error(msg)
        }
    }

}
