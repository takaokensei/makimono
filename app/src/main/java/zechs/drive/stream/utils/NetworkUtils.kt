package zechs.drive.stream.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    private const val TAG = "NetworkUtils"

    fun isEmulatorAddress(ip: String?): Boolean {
        if (ip == null) return false
        return ip == "10.0.2.15" || ip.startsWith("10.0.2.")
    }

    /**
     * Resolves the primary local IPv4 address (e.g. 192.168.x.x or 10.x.x.x)
     * of the device connected to the local Wi-Fi or Ethernet LAN.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

            // Collect all valid IPv4 candidates with score
            val candidates = mutableListOf<Pair<String, Int>>()

            for (networkInterface in interfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                val name = networkInterface.name.lowercase()

                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val hostAddress = address.hostAddress ?: continue
                        if (hostAddress.startsWith("127.")) continue

                        var score = 10
                        if (name.startsWith("wlan")) score += 30
                        else if (name.startsWith("eth")) score += 20

                        // Private LAN standard subnets get priority over emulator
                        if (hostAddress.startsWith("192.168.")) score += 50
                        else if (hostAddress.startsWith("172.")) score += 25
                        else if (hostAddress.startsWith("10.") && !isEmulatorAddress(hostAddress)) score += 25
                        else if (isEmulatorAddress(hostAddress)) score -= 40 // deprioritize emulator

                        candidates.add(hostAddress to score)
                    }
                }
            }

            candidates.sortByDescending { it.second }
            val best = candidates.firstOrNull()?.first
            if (best != null) {
                Log.d(TAG, "Selected local IPv4 address: $best (candidates: $candidates)")
                return best
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving local IP address", e)
        }
        return null
    }

    /**
     * Checks if device is currently connected to a network.
     */
    fun isConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            networkInfo.isConnected
        }
    }
}
