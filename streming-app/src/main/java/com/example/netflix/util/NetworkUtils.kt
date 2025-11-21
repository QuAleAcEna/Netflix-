package com.example.netflix.util

import android.util.Log
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.net.InetAddress

object NetworkUtils {
    fun getPeerIpAddresses(): List<String> {
        val ips = mutableListOf<String>()
        try {
            val br = BufferedReader(FileReader("/proc/net/arp"))
            var line: String?
            while (br.readLine().also { line = it } != null) {
                val parts = line!!.split("\\s+".toRegex())
                if (parts.size >= 4) {
                    val ip = parts[0]
                    val mac = parts[3]
                    if (mac != "00:00:00:00:00:00" && ip != "IP") {
                        ips.add(ip)
                    }
                }
            }
            br.close()
        } catch (e: IOException) {
            Log.e("NetworkUtils", "Error reading ARP table", e)
        }
        return ips
    }
}