package com.kavir.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class Found(val name: String, val host: String, val id: String)

/** Finds the PC server on the local Wi-Fi: UDP broadcast first, then a /24 subnet scan as fallback. */
object Discovery {
    private const val UDP_PORT = 8766
    private const val HTTP_PORT = 8765
    private val http = OkHttpClient.Builder()
        .connectTimeout(400, TimeUnit.MILLISECONDS).readTimeout(600, TimeUnit.MILLISECONDS).build()

    private fun localAddrs(): List<Inet4Address> = try {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }
    } catch (e: Exception) { emptyList() }

    private fun udp(out: MutableMap<String, Found>, waitMs: Long) {
        try {
            DatagramSocket().use { s ->
                s.broadcast = true; s.soTimeout = 300
                val msg = "KAVIR_DISCOVER".toByteArray()
                val targets = (listOf("255.255.255.255") +
                    localAddrs().map { it.hostAddress!!.substringBeforeLast('.') + ".255" }).distinct()
                repeat(2) {
                    targets.forEach { t ->
                        try { s.send(DatagramPacket(msg, msg.size, InetAddress.getByName(t), UDP_PORT)) } catch (e: Exception) { }
                    }
                }
                val end = System.currentTimeMillis() + waitMs
                val buf = ByteArray(1024)
                while (System.currentTimeMillis() < end) {
                    try {
                        val p = DatagramPacket(buf, buf.size); s.receive(p)
                        val j = JSONObject(String(p.data, 0, p.length))
                        if (j.optString("app") == "kavir-remote") {
                            val host = p.address.hostAddress + ":" + j.optInt("port", HTTP_PORT)
                            out[host] = Found(j.optString("name"), host, j.optString("id"))
                        }
                    } catch (e: SocketTimeoutException) {
                    } catch (e: Exception) { }
                }
            }
        } catch (e: Exception) { }
    }

    private fun probe(host: String): Found? = try {
        http.newCall(Request.Builder().url("http://$host/api/info").build()).execute().use { r ->
            if (!r.isSuccessful) null else {
                val j = JSONObject(r.body?.string().orEmpty())
                if (j.optString("app") == "kavir-remote") Found(j.optString("name"), host, j.optString("id")) else null
            }
        }
    } catch (e: Exception) { null }

    private suspend fun scan(out: MutableMap<String, Found>) {
        val prefixes = localAddrs().map { it.hostAddress!!.substringBeforeLast('.') }.distinct()
        val sem = Semaphore(64)
        coroutineScope {
            prefixes.flatMap { p -> (1..254).map { p to it } }.map { (p, i) ->
                async(Dispatchers.IO) { sem.withPermit { probe("$p.$i:$HTTP_PORT") }?.let { out[it.host] = it } }
            }.awaitAll()
        }
    }

    suspend fun find(): List<Found> = withContext(Dispatchers.IO) {
        val out = ConcurrentHashMap<String, Found>()
        udp(out, 1500)
        if (out.isEmpty()) scan(out)
        out.values.toList().sortedBy { it.name }
    }
}
