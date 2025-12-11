package com.example.netflix.util

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.InetAddress
import java.util.Collections

class PeerDiscovery(context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "_netflix_torrent._tcp."
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun register(port: Int) {
        if (registrationListener != null) return // Already registered

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                Log.d("PeerDiscovery", "Service registered: ${NsdServiceInfo.serviceName}")
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("PeerDiscovery", "Registration failed: $errorCode")
                registrationListener = null
            }
            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d("PeerDiscovery", "Service unregistered")
                registrationListener = null
            }
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("PeerDiscovery", "Unregistration failed: $errorCode")
                registrationListener = null
            }
        }

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "NetflixPeer-${System.currentTimeMillis()}"
            this.serviceType = this@PeerDiscovery.serviceType
            this.port = port
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
            registrationListener = listener
        } catch (e: Exception) {
            Log.e("PeerDiscovery", "Error registering service", e)
        }
    }

    fun unregister() {
        registrationListener?.let {
            try {
                nsdManager.unregisterService(it)
            } catch (e: Exception) {
                Log.e("PeerDiscovery", "Error unregistering service", e)
            }
        }
        registrationListener = null
    }

    fun discoverPeers(): Flow<List<InetAddress>> = callbackFlow {
        val peers = Collections.synchronizedSet(mutableSetOf<InetAddress>())
        
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("PeerDiscovery", "Discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d("PeerDiscovery", "Service found: ${service.serviceName}")
                // Warning: The NsdServiceInfo passed here doesn't have the port/host yet. We must resolve it.
                // Also need to check if it matches our service type, though discoverServices filters for it.
                
                try {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e("PeerDiscovery", "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            Log.d("PeerDiscovery", "Service resolved: ${serviceInfo.host}")
                            val host = serviceInfo.host
                            // Filter out loopback or self if possible, but simplest is just add it.
                            if (host != null && !host.isLoopbackAddress) {
                                if (peers.add(host)) {
                                    val currentList = synchronized(peers) { peers.toList() }
                                    trySend(currentList)
                                }
                            }
                        }
                    })
                } catch (e: Exception) {
                    // limit resolve calls or handle busy state
                    Log.e("PeerDiscovery", "Error resolving service", e)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d("PeerDiscovery", "Service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d("PeerDiscovery", "Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("PeerDiscovery", "Start discovery failed: $errorCode")
                close(Exception("Start discovery failed: $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("PeerDiscovery", "Stop discovery failed: $errorCode")
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
             Log.e("PeerDiscovery", "Error starting discovery", e)
             close(e)
        }

        awaitClose {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                 Log.e("PeerDiscovery", "Error stopping discovery", e)
            }
        }
    }
}
