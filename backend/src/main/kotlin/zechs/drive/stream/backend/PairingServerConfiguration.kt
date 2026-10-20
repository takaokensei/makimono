package zechs.drive.stream.backend

import java.net.URI

internal data class PairingServerConfiguration(
    val host: String,
    val port: Int,
    val publicBaseUrl: String,
    val databasePoolSize: Int
) {
    companion object {
        private const val LOCAL_HOST = "127.0.0.1"
        private const val CLOUD_RUN_HOST = "0.0.0.0"
        private const val LOCAL_PORT = 8097
        private const val CLOUD_RUN_PORT = 8080
        private const val DEFAULT_LOCAL_BASE_URL = "https://pairing.invalid"
        private const val LOCAL_POOL_SIZE = 8
        private const val CLOUD_RUN_POOL_SIZE = 2

        fun from(environment: Map<String, String>): PairingServerConfiguration {
            val isCloudRun = !environment["K_SERVICE"].isNullOrBlank()
            val host = environment["MAKIMONO_PAIRING_HOST"]
                ?: if (isCloudRun) CLOUD_RUN_HOST else LOCAL_HOST
            val port = environment["PORT"]?.toIntOrNull()
                ?: environment["MAKIMONO_PAIRING_DEV_PORT"]?.toIntOrNull()
                ?: if (isCloudRun) CLOUD_RUN_PORT else LOCAL_PORT
            require(port in 1..65535) { "Server port must be between 1 and 65535" }
            if (!isCloudRun && environment["PORT"] == null) {
                require(port >= 1024) { "Development port must be between 1024 and 65535" }
            }

            val configuredBaseUrl = environment["MAKIMONO_PAIRING_PUBLIC_BASE_URL"]
            require(!isCloudRun || !configuredBaseUrl.isNullOrBlank()) {
                "Set MAKIMONO_PAIRING_PUBLIC_BASE_URL to the HTTPS Cloud Run service URL"
            }
            val publicBaseUrl = configuredBaseUrl ?: DEFAULT_LOCAL_BASE_URL
            validatePublicBaseUrl(publicBaseUrl)

            val databaseVariables = listOf(
                environment["MAKIMONO_DATABASE_URL"],
                environment["MAKIMONO_DATABASE_USER"],
                environment["MAKIMONO_DATABASE_PASSWORD"]
            )
            val hasAnyDatabaseSetting = databaseVariables.any { !it.isNullOrBlank() }
            val hasCompleteDatabaseSettings = databaseVariables.all { !it.isNullOrBlank() }
            require(!hasAnyDatabaseSetting || hasCompleteDatabaseSettings) {
                "Set MAKIMONO_DATABASE_URL, MAKIMONO_DATABASE_USER, and MAKIMONO_DATABASE_PASSWORD together"
            }
            require(!isCloudRun || hasCompleteDatabaseSettings) {
                "Cloud Run requires persistent PostgreSQL configuration; in-memory storage is development-only"
            }

            val poolSize = environment["MAKIMONO_DATABASE_POOL_SIZE"]?.toIntOrNull()
                ?: if (isCloudRun) CLOUD_RUN_POOL_SIZE else LOCAL_POOL_SIZE
            require(poolSize in 1..50) { "Database pool size must be between 1 and 50" }

            return PairingServerConfiguration(
                host = host,
                port = port,
                publicBaseUrl = publicBaseUrl.trimEnd('/'),
                databasePoolSize = poolSize
            )
        }

        private fun validatePublicBaseUrl(value: String) {
            val uri = try {
                URI(value)
            } catch (_: Exception) {
                throw IllegalArgumentException("Pairing public base URL must be a valid HTTPS origin")
            }
            require(
                uri.scheme.equals("https", ignoreCase = true) &&
                    !uri.host.isNullOrBlank() &&
                    uri.rawUserInfo == null &&
                    uri.rawQuery == null &&
                    uri.rawFragment == null &&
                    (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/")
            ) {
                "Pairing public base URL must be an HTTPS origin without credentials, path, query, or fragment"
            }
        }
    }
}
