package io.cabinos.one.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** A captive portal or a Wi-Fi link alone does not establish internet access. */
class NetworkMonitor(context:Context) {
    private val manager=context.applicationContext.getSystemService(ConnectivityManager::class.java)
    val available=callbackFlow {
        fun publish(capabilities:NetworkCapabilities?) {
            trySend(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        }
        fun refresh()=publish(manager.getNetworkCapabilities(manager.activeNetwork))
        val callback=object:ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network:Network)=refresh()
            override fun onCapabilitiesChanged(network:Network,capabilities:NetworkCapabilities) {
                if(network==manager.activeNetwork)publish(capabilities)
            }
            override fun onLost(network:Network) {
                if(manager.activeNetwork==network)publish(null) else refresh()
            }
        }
        var registered=false
        try {
            manager.registerDefaultNetworkCallback(callback)
            registered=true
            refresh()
        } catch (_:SecurityException) {trySend(false)}
        awaitClose {if(registered)manager.unregisterNetworkCallback(callback)}
    }.distinctUntilChanged()
}
