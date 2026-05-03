package dev.vitalcc.stukay.runtime.hostbridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class AndroidNetworkMonitor(
    context: Context,
    private val onNetworkChanged: () -> Unit,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private var started = false
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onNetworkChanged()
        }

        override fun onLost(network: Network) {
            onNetworkChanged()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            onNetworkChanged()
        }
    }

    fun start() {
        if (started || connectivityManager == null) {
            return
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        started = true
    }

    fun stop() {
        val manager = connectivityManager ?: return
        if (!started) {
            return
        }
        manager.unregisterNetworkCallback(callback)
        started = false
    }
}
