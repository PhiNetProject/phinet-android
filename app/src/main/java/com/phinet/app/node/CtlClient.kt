package com.phinet.app.node

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Minimal client for the embedded daemon's control socket (127.0.0.1:7799).
 * One JSON request per line, one JSON response line back. Used for control
 * commands the com HTTP API doesn't cover — notably hidden-service fetch.
 */
object CtlClient {
    private val json = Json { ignoreUnknownKeys = true }

    /** Set once at startup to the daemon's data dir (this app's filesDir). */
    @Volatile var dataDir: java.io.File? = null

    /**
     * The daemon's control cookie.
     *
     * Localhost is not a boundary on Android: every installed app with the
     * INTERNET permission can open 127.0.0.1:7799, and the control socket can
     * read com threads, send messages as this node, and reveal its address.
     * The cookie lives in this app's private filesDir, which the OS keeps
     * other apps out of — so holding it is what distinguishes us from them.
     */
    private fun cookie(): String? = runCatching {
        java.io.File(dataDir, ".phinet/control.cookie").readText().trim()
    }.getOrNull()

    private suspend fun call(req: JsonObject): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            val authed = buildJsonObject {
                req.forEach { (k, v) -> put(k, v) }
                cookie()?.let { put("cookie", it) }
            }
            Socket().use { sock ->
                sock.connect(InetSocketAddress("127.0.0.1", NodeConfig.CTL_PORT), 4000)
                val w = OutputStreamWriter(sock.getOutputStream())
                w.write(authed.toString()); w.write("\n"); w.flush()
                val r = BufferedReader(InputStreamReader(sock.getInputStream()))
                val line = r.readLine() ?: return@use null
                json.parseToJsonElement(line).jsonObject
            }
        }.getOrNull()
    }

    data class HsPage(val status: Int, val contentType: String, val body: ByteArray)

    /** Fetch a ΦNET hidden-service page: `<hs_id>.phinet<path>`. */
    suspend fun hsFetch(hsId: String, path: String): HsPage? {
        val resp = call(buildJsonObject {
            put("cmd", "hs_fetch"); put("hs_id", hsId)
            put("path", path.ifEmpty { "/" }); put("method", "GET")
        }) ?: return null
        if (resp["ok"]?.jsonPrimitive?.content != "true") return null
        val status = resp["status"]?.jsonPrimitive?.content?.toIntOrNull() ?: 200
        val ct = resp["headers"]?.jsonObject?.get("Content-Type")?.jsonPrimitive?.content
            ?: "text/html; charset=utf-8"
        val hexBody = resp["body_b64"]?.jsonPrimitive?.content ?: ""
        // The daemon hex-encodes the body (despite the field name). Decode hex.
        val body = runCatching {
            ByteArray(hexBody.length / 2) { i ->
                hexBody.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }.getOrDefault(ByteArray(0))
        return HsPage(status, ct, body)
    }

    suspend fun whoami(): String? =
        call(buildJsonObject { put("cmd", "whoami") })
            ?.get("node_id")?.jsonPrimitive?.content

    data class Relay(val nodeId: String, val host: String, val port: Int)
    data class Hop(val role: String, val nodeId: String, val host: String, val port: Int)
    data class CircuitInfo(val nodeId: String, val staticPub: String,
                           val path: List<Hop>,
                           val guards: List<Relay>, val relays: List<Relay>)

    private fun parseRelays(arr: kotlinx.serialization.json.JsonElement?): List<Relay> =
        (arr as? kotlinx.serialization.json.JsonArray)?.mapNotNull { el ->
            val o = el.jsonObject
            Relay(
                o["node_id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                o["host"]?.jsonPrimitive?.content ?: "",
                o["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            )
        } ?: emptyList()

    private fun parsePath(arr: kotlinx.serialization.json.JsonElement?): List<Hop> =
        (arr as? kotlinx.serialization.json.JsonArray)?.mapNotNull { el ->
            val o = el.jsonObject
            Hop(
                o["role"]?.jsonPrimitive?.content ?: "middle",
                o["node_id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                o["host"]?.jsonPrimitive?.content ?: "",
                o["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            )
        } ?: emptyList()

    /** Node identity + guards (entry hops) + connected relays — Tor-style view. */
    suspend fun circuitInfo(): CircuitInfo? {
        val r = call(buildJsonObject { put("cmd", "circuit_info") }) ?: return null
        val nid = r["node_id"]?.jsonPrimitive?.content ?: return null
        return CircuitInfo(
            nid,
            r["static_pub"]?.jsonPrimitive?.content ?: "",
            parsePath(r["path"]),
            parseRelays(r["guards"]),
            parseRelays(r["relays"]),
        )
    }

    /** Tor NEWNYM: retire guards + drop circuits so new traffic uses fresh relays. */
    suspend fun newIdentity(): Boolean =
        call(buildJsonObject { put("cmd", "new_identity") })
            ?.get("ok")?.jsonPrimitive?.content == "true"
}
