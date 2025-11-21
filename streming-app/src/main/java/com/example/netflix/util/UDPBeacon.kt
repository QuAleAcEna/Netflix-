package com.example.netflix.util

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.*
import java.util.concurrent.ConcurrentHashMap

class UDPBeacon(private val context: Context, private val port: Int = 8889, private val filesDir: File) {
    private var socket: DatagramSocket? = null
    private var listenJob: Job? = null
    private var broadcastJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredIps = MutableStateFlow<Set<String>>(emptySet())
    val discoveredIps = _discoveredIps.asStateFlow()
    
    private val _peerFiles = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val peerFiles = _peerFiles.asStateFlow()

    private val foundIps = ConcurrentHashMap.newKeySet<String>()
    private val peerFilesMap = ConcurrentHashMap<String, Set<String>>()
    
    private val availableFiles = ConcurrentHashMap.newKeySet<String>()

    fun start() {
        stop() // Ensure clean start
        
        // Acquire Multicast Lock to allow receiving broadcasts
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifiManager.createMulticastLock("netflix_p2p_lock").apply {
                setReferenceCounted(true)
                acquire()
            }
        } catch (e: Exception) {
            Log.e("UDPBeacon", "Failed to acquire multicast lock", e)
        }

        try {
            socket = DatagramSocket(port)
            socket?.broadcast = true
            Log.d("UDPBeacon", "UDP Beacon started on port $port")
        } catch (e: Exception) {
            Log.e("UDPBeacon", "Failed to create socket: ${e.message}")
            return
        }
        
        updateAvailableFiles()
        startListening()
        startBroadcasting()
    }

    fun stop() {
        listenJob?.cancel()
        broadcastJob?.cancel()
        socket?.close()
        socket = null
        
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            Log.e("UDPBeacon", "Error releasing lock", e)
        }
        multicastLock = null
        
        foundIps.clear()
        peerFilesMap.clear()
        _discoveredIps.value = emptySet()
        _peerFiles.value = emptyMap()
    }
    
    fun updateAvailableFiles() {
        scope.launch {
            try {
                if (filesDir.exists()) {
                    // List all files, as VideoDownloader might save without extension
                    val files = filesDir.listFiles { file -> 
                        file.isFile && !file.name.startsWith(".")
                    }
                    availableFiles.clear()
                    files?.forEach { 
                        val name = it.name.removeSuffix(".tmp").removeSuffix(".mp4")
                        availableFiles.add(name)
                    }
                }
            } catch (e: Exception) {
                Log.e("UDPBeacon", "Error listing files", e)
            }
        }
    }

    private fun startListening() {
        listenJob = scope.launch {
            val buffer = ByteArray(4096)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    // Message format: "NETFLIX_P2P:<IP>:<FILES>"
                    if (message.startsWith("NETFLIX_P2P:")) {
                        val parts = message.split(":")
                        if (parts.size >= 2) {
                             val ip = parts[1]
                             val myIp = getLocalIpAddress()
                             // Don't add ourselves
                             if (ip != myIp && ip.isNotEmpty()) {
                                // Parse files
                                if (parts.size >= 3) {
                                     val filesStr = parts[2]
                                     val files = filesStr.split(",").filter { it.isNotEmpty() }.toSet()
                                     if (peerFilesMap[ip] != files) {
                                         peerFilesMap[ip] = files
                                         _peerFiles.value = peerFilesMap.toMap()
                                     }
                                }
                                
                                if (foundIps.add(ip)) {
                                    _discoveredIps.value = foundIps.toSet()
                                    Log.d("UDPBeacon", "Discovered peer: $ip")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive && e !is SocketException) Log.e("UDPBeacon", "Listen error", e)
                }
            }
        }
    }

    private fun startBroadcasting() {
        broadcastJob = scope.launch {
            while (isActive) {
                try {
                    updateAvailableFiles() // Refresh file list periodically
                    val myIp = getLocalIpAddress()
                    if (myIp != null) {
                        // Broadcast format: NETFLIX_P2P:IP:File1,File2,...
                        val filesStr = availableFiles.joinToString(",")
                        val msg = "NETFLIX_P2P:$myIp:$filesStr"
                        val data = msg.toByteArray()
                        val packet = DatagramPacket(
                            data, data.size,
                            InetAddress.getByName("255.255.255.255"),
                            port
                        )
                        socket?.send(packet)
                    }
                } catch (e: Exception) {
                    if (isActive) Log.e("UDPBeacon", "Broadcast error", e)
                }
                delay(2000) // Broadcast every 2 seconds
            }
        }
    }

    private fun getLocalIpAddress(): String? {
        var bestIp: String? = null
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val name = iface.name.lowercase()
                val isWifi = name.contains("wlan") || name.contains("p2p") || name.contains("ap")
                
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val sAddr = addr.hostAddress
                        // Prioritize Wi-Fi Direct
                        if (sAddr.startsWith("192.168.49.")) {
                            return sAddr
                        }
                        // Return immediately if it's a known wifi interface
                        if (isWifi) {
                            return sAddr
                        }
                        // Keep first valid IP as fallback
                        if (bestIp == null) {
                            bestIp = sAddr
                        }
                    }
                }
            }
        } catch (e: Exception) { }
        return bestIp
    }
}