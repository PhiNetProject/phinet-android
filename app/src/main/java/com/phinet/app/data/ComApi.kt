package com.phinet.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

/**
 * com daemon HTTP client (plain OkHttp + kotlinx.serialization). All calls
 * are GET with query params, matching the daemon's serve_com_http handler.
 */
class ComApi(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val json: Json,
) {
    private inline fun <reified T> get(path: String, vararg q: Pair<String, String>): T {
        val url = (baseUrl.trimEnd('/') + "/" + path).toHttpUrl().newBuilder().apply {
            q.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            val body = resp.body?.string() ?: "{}"
            return json.decodeFromString(body)
        }
    }

    suspend fun whoami(): WhoAmI = withContext(Dispatchers.IO) { get("api/whoami") }
    suspend fun myAddress(): MyAddress = withContext(Dispatchers.IO) { get("api/my_address") }
    suspend fun peers(): PeersResponse = withContext(Dispatchers.IO) { get("api/peers") }
    suspend fun threads(): ThreadsResponse = withContext(Dispatchers.IO) { get("api/threads") }
    suspend fun thread(peer: String): ThreadResponse =
        withContext(Dispatchers.IO) { get("api/thread", "peer" to peer) }
    suspend fun send(peer: String, text: String): SendResponse =
        withContext(Dispatchers.IO) { get("api/send", "peer" to peer, "text" to text) }
    suspend fun addContact(address: String): AddContactResponse =
        withContext(Dispatchers.IO) { get("api/add_contact", "address" to address) }
    suspend fun delete(peer: String, msgId: String): SendResponse =
        withContext(Dispatchers.IO) { get("api/delete", "peer" to peer, "msg_id" to msgId) }
    suspend fun groups(): GroupsResponse = withContext(Dispatchers.IO) { get("api/groups") }
    suspend fun createGroup(name: String, channel: Boolean): SendResponse =
        withContext(Dispatchers.IO) { get("api/create_group", "name" to name, "channel" to if (channel) "1" else "0") }
    suspend fun sendGroup(group: String, text: String): SendResponse =
        withContext(Dispatchers.IO) { get("api/send_group", "group" to group, "text" to text) }
    suspend fun invite(group: String, peer: String): SendResponse =
        withContext(Dispatchers.IO) { get("api/invite", "group" to group, "peer" to peer) }
}
