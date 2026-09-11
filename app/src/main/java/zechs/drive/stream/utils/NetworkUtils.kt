package zechs.drive.stream.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

object NetworkUtils {

    private const val TAG = "NetworkUtils"

    /**
     * Resolves the primary local IPv4 address (e.g. 192.168.x.x or 10.x.x.x)
     * of the device connected to the local Wi-Fi or Ethernet LAN.
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())

            // Sort interfaces so that wlan and eth are prioritized
            val sortedInterfaces = interfaces.sortedWith(compareByDescending { networkInterface ->
                val name = networkInterface.name.lowercase()
                when {
                    name.startsWith("wlan") -> 3
                    name.startsWith("eth") -> 2
                    else -> 1
                }
            })

            for (networkInterface in sortedInterfaces) {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue

                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        val hostAddress = address.hostAddress ?: continue
                        // Exclude docker/vpn dummy addresses if any
                        if (!hostAddress.startsWith("127.")) {
                            Log.d(TAG, "Found local IPv4 address: $hostAddress on ${networkInterface.name}")
                            return hostAddress
                        }
                    }
                }
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
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
