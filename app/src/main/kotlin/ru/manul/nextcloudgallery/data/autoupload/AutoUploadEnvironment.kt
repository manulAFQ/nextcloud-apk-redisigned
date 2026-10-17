package ru.manul.nextcloudgallery.data.autoupload

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build

data class DeviceConditions(
    val wifiConnected: Boolean,
    val ssid: String?,
    val charging: Boolean,
    val batteryPercent: Int?,
)

fun interface AutoUploadEnvironment {
    fun current(): DeviceConditions
}

class AndroidAutoUploadEnvironment(private val context: Context) : AutoUploadEnvironment {
    override fun current(): DeviceConditions {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork
        val capabilities = network?.let(connectivity::getNetworkCapabilities)
        val wifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        return DeviceConditions(
            wifiConnected = wifi,
            ssid = if (wifi) currentSsid(capabilities) else null,
            charging = context.getSystemService(BatteryManager::class.java).isCharging,
            batteryPercent = context.getSystemService(BatteryManager::class.java)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                .takeIf { it in 0..100 },
        )
    }

    private fun currentSsid(capabilities: NetworkCapabilities?): String? = try {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (capabilities?.transportInfo as? WifiInfo)?.ssid
        } else {
            @Suppress("DEPRECATION")
            context.applicationContext.getSystemService(WifiManager::class.java).connectionInfo?.ssid
        }
        raw?.trim('"')?.takeUnless { it.isBlank() || it == WifiManager.UNKNOWN_SSID }
    } catch (_: SecurityException) {
        null
    }
}
