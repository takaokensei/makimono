package zechs.drive.stream.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zechs.drive.stream.data.model.SeedboxCreateJobRequest
import zechs.drive.stream.data.model.SeedboxJobItem
import zechs.drive.stream.data.model.SeedboxStatusResponse
import zechs.drive.stream.data.remote.PairingApi
import zechs.drive.stream.utils.AppLog
import zechs.drive.stream.utils.SessionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeedboxRepository @Inject constructor(
    private val pairingApi: dagger.Lazy<PairingApi>,
    private val sessionManager: dagger.Lazy<SessionManager>
) {

    companion object {
        private const val TAG = "SeedboxRepository"
    }

    private suspend fun requireAuthHeader(): String {
        val token = sessionManager.get().fetchPairingSessionToken()
            ?: throw IllegalStateException("Sessão do Makimono não autenticada")
        return "Bearer $token"
    }

    suspend fun getStatus(): Result<SeedboxStatusResponse> = withContext(Dispatchers.IO) {
        try {
            val auth = requireAuthHeader()
            val resp = pairingApi.get().getSeedboxStatus(auth)
            Result.success(resp)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed fetching Seedbox status", e)
            Result.failure(e)
        }
    }

    suspend fun enqueueJob(
        query: String,
        episodesFilter: String = "all",
        preferPtBr: Boolean = true,
        magnetUri: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val auth = requireAuthHeader()
            val req = SeedboxCreateJobRequest(
                animeQuery = query,
                episodesFilter = episodesFilter,
                preferPtbr = preferPtBr,
                magnetUri = magnetUri
            )
            val resp = pairingApi.get().createSeedboxJob(auth, req)
            Result.success(resp.jobId)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed enqueuing Seedbox job for query='$query'", e)
            Result.failure(e)
        }
    }

    suspend fun getJobs(): Result<List<SeedboxJobItem>> = withContext(Dispatchers.IO) {
        try {
            val auth = requireAuthHeader()
            val resp = pairingApi.get().getSeedboxJobs(auth)
            Result.success(resp.jobs)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed fetching Seedbox jobs", e)
            Result.failure(e)
        }
    }

    suspend fun generateDaemonToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val auth = requireAuthHeader()
            val resp = pairingApi.get().createSeedboxToken(auth)
            Result.success(resp.daemonToken)
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed generating Seedbox daemon token", e)
            Result.failure(e)
        }
    }

    suspend fun cancelJob(jobId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val auth = requireAuthHeader()
            val resp = pairingApi.get().cancelSeedboxJob(auth, jobId)
            if (resp.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP ${resp.code()}: ${resp.message()}"))
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed cancelling Seedbox job $jobId", e)
            Result.failure(e)
        }
    }

    suspend fun cancelAllJobs(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val auth = requireAuthHeader()
            val resp = pairingApi.get().cancelAllSeedboxJobs(auth)
            if (resp.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("HTTP ${resp.code()}: ${resp.message()}"))
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed cancelling all Seedbox jobs", e)
            Result.failure(e)
        }
    }
}
