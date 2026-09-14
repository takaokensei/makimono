package zechs.drive.stream.data.repository

import android.net.Uri
import android.util.Log
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zechs.drive.stream.data.model.MalAnimeNode
import zechs.drive.stream.data.model.MalTokenResponse
import zechs.drive.stream.data.model.MalUserProfile
import zechs.drive.stream.data.model.MalUserListStatus
import zechs.drive.stream.data.remote.AnimePosterResolver
import zechs.drive.stream.data.remote.MalApi
import zechs.drive.stream.utils.MalSessionManager
import zechs.drive.stream.utils.state.Resource
import zechs.drive.stream.utils.util.Constants
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MalRepository @Inject constructor(
    private val malApi: Lazy<MalApi>,
    private val sessionManager: MalSessionManager,
    private val posterResolver: AnimePosterResolver
) {

    companion object {
        private const val TAG = "MalRepository"
    }

    fun getAuthorizationUrl(codeVerifier: String): String {
        return Uri.parse(Constants.MAL_OAUTH_BASE_URL + "v1/oauth2/authorize")
            .buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", sessionManager.getClientId())
            .appendQueryParameter("code_challenge", codeVerifier)
            .appendQueryParameter("code_challenge_method", "plain")
            .appendQueryParameter("redirect_uri", Constants.MAL_REDIRECT_URI)
            .build()
            .toString()
    }

    suspend fun exchangeToken(code: String, codeVerifier: String): Resource<MalTokenResponse> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Exchanging code for MAL token with verifier")
            val response = malApi.get().exchangeToken(
                clientId = sessionManager.getClientId(),
                clientSecret = sessionManager.getClientSecret(),
                code = code,
                codeVerifier = codeVerifier,
                redirectUri = Constants.MAL_REDIRECT_URI
            )
            if (response.isSuccessful && response.body() != null) {
                val token = response.body()!!
                sessionManager.saveTokens(token)
                sessionManager.setSyncEnabled(true)
                // Fetch profile to personalize
                fetchUserProfile()
                Resource.Success(token)
            } else {
                val err = response.errorBody()?.string() ?: "Erro desconhecido HTTP ${response.code()}"
                Log.e(TAG, "Token exchange failed: $err")
                Resource.Error("Falha na autenticação do MAL: $err")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange exception", e)
            Resource.Error(e.localizedMessage ?: "Erro de conexão ao autenticar no MAL")
        }
    }

    suspend fun ensureValidToken(): String? = withContext(Dispatchers.IO) {
        val currentToken = sessionManager.getAccessToken() ?: return@withContext null
        if (!sessionManager.isTokenExpired()) {
            return@withContext currentToken
        }

        val refreshToken = sessionManager.getRefreshToken() ?: return@withContext null
        try {
            Log.d(TAG, "Refreshing expired MAL token...")
            val response = malApi.get().refreshToken(
                clientId = sessionManager.getClientId(),
                clientSecret = sessionManager.getClientSecret(),
                refreshToken = refreshToken
            )
            if (response.isSuccessful && response.body() != null) {
                val newToken = response.body()!!
                sessionManager.saveTokens(newToken)
                Log.d(TAG, "MAL token refreshed successfully")
                return@withContext newToken.accessToken
            } else {
                Log.w(TAG, "Failed to refresh MAL token: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing MAL token", e)
        }
        return@withContext currentToken
    }

    suspend fun fetchUserProfile(): MalUserProfile? = withContext(Dispatchers.IO) {
        val token = ensureValidToken() ?: return@withContext null
        try {
            val response = malApi.get().getCurrentUser("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                val user = response.body()!!
                sessionManager.saveUserProfile(user)
                return@withContext user
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user profile", e)
        }
        null
    }

    suspend fun searchAnime(query: String): Resource<List<MalAnimeNode>> = withContext(Dispatchers.IO) {
        try {
            val clean = posterResolver.cleanAnimeTitle(query)
            if (clean.isBlank()) return@withContext Resource.Error("Título de busca vazio")

            val token = ensureValidToken()
            val authHeader = if (token != null) "Bearer $token" else null
            val clientIdHeader = if (authHeader == null) sessionManager.getClientId() else null

            Log.d(TAG, "Searching MAL for: '$clean' (auth=${authHeader != null})")
            val response = malApi.get().searchAnime(
                query = clean,
                limit = 5,
                authHeader = authHeader,
                clientIdHeader = clientIdHeader
            )
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!.data.map { it.node }
                Resource.Success(list)
            } else {
                Resource.Error("Erro na busca do MAL: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching MAL", e)
            Resource.Error(e.localizedMessage ?: "Erro ao buscar anime no MAL")
        }
    }

    suspend fun matchAnime(titleOrFolder: String): MalAnimeNode? {
        val result = searchAnime(titleOrFolder)
        if (result is Resource.Success && !result.data.isNullOrEmpty()) {
            return result.data[0]
        }
        return null
    }

    suspend fun updateEpisodeProgress(
        animeId: Long,
        episodeNumber: Int
    ): Resource<MalUserListStatus> = withContext(Dispatchers.IO) {
        val token = ensureValidToken() ?: return@withContext Resource.Error("Não autenticado no MAL")
        try {
            Log.d(TAG, "Scrobbling MAL: animeId=$animeId, episode=$episodeNumber")
            val response = malApi.get().updateListStatus(
                animeId = animeId,
                authHeader = "Bearer $token",
                status = "watching",
                numWatchedEpisodes = episodeNumber
            )
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "Successfully scrobbled episode $episodeNumber to MAL!")
                Resource.Success(response.body()!!)
            } else {
                val err = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Log.w(TAG, "Failed to scrobble: $err")
                Resource.Error("Falha ao atualizar MAL: $err")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception scrobbling episode", e)
            Resource.Error(e.localizedMessage ?: "Erro ao sincronizar com MAL")
        }
    }

    suspend fun completeAnimeWithScore(
        animeId: Long,
        totalEpisodes: Int,
        score: Int
    ): Resource<MalUserListStatus> = withContext(Dispatchers.IO) {
        val token = ensureValidToken() ?: return@withContext Resource.Error("Não autenticado no MAL")
        try {
            Log.d(TAG, "Completing anime on MAL: animeId=$animeId, score=$score, episodes=$totalEpisodes")
            val response = malApi.get().updateListStatus(
                animeId = animeId,
                authHeader = "Bearer $token",
                status = "completed",
                numWatchedEpisodes = if (totalEpisodes > 0) totalEpisodes else null,
                score = score
            )
            if (response.isSuccessful && response.body() != null) {
                Log.d(TAG, "Successfully completed anime with score $score on MAL!")
                Resource.Success(response.body()!!)
            } else {
                val err = response.errorBody()?.string() ?: "HTTP ${response.code()}"
                Resource.Error("Falha ao finalizar anime no MAL: $err")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception completing anime", e)
            Resource.Error(e.localizedMessage ?: "Erro ao salvar avaliação no MAL")
        }
    }
}
