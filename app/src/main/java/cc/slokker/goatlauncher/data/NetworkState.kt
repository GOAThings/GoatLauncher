package cc.slokker.goatlauncher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Como a box está ligada à rede, para o ícone no canto do ecrã inicial. */
enum class NetworkKind { WIFI, ETHERNET, NONE }

fun networkKind(context: Context): Flow<NetworkKind> = callbackFlow {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    fun current(): NetworkKind = runCatching {
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching NetworkKind.NONE
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkKind.WIFI
            else -> NetworkKind.NONE
        }
    }.getOrDefault(NetworkKind.NONE)
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { trySend(current()) }
        override fun onLost(network: Network) { trySend(current()) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { trySend(current()) }
    }
    trySend(current())
    runCatching { cm.registerDefaultNetworkCallback(callback) }
    awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
}.distinctUntilChanged()
