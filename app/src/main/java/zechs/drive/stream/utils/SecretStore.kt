package zechs.drive.stream.utils

/**
 * SEC-02: Contract for storing sensitive OAuth credentials backed by Android Keystore.
 */
interface SecretStore {
    suspend fun put(key: String, value: String)
    suspend fun get(key: String): String?
    suspend fun remove(key: String)
    suspend fun clear()
}
