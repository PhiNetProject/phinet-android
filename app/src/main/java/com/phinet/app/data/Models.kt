package com.phinet.app.data

import kotlinx.serialization.Serializable

// DTOs matching the daemon's com HTTP API (served on ctl_port + 2, e.g. 7801).

@Serializable
data class WhoAmI(
    val node_id: String = "",
    val static_pub: String = "",
)

@Serializable
data class MyAddress(val address: String = "")

@Serializable
data class Peer(
    val node_id: String,
    val host: String = "",
    val port: Int = 0,
    val static_pub: String = "",
)

@Serializable
data class PeersResponse(val peers: List<Peer> = emptyList())

@Serializable
data class ThreadsResponse(val threads: List<String> = emptyList())

@Serializable
data class ComMessage(
    val outgoing: Boolean = false,
    val timestamp: Long = 0,
    val body: String = "",
    val msg_id: String = "",
)

@Serializable
data class ThreadResponse(val messages: List<ComMessage> = emptyList())

@Serializable
data class AddContactResponse(
    val ok: Boolean = false,
    val node_id: String? = null,
    val error: String? = null,
)

@Serializable
data class Group(
    val group_id: String,
    val name: String,
    val is_channel: Boolean = false,
    val thread_id: String = "",
)

@Serializable
data class GroupsResponse(val groups: List<Group> = emptyList())

@Serializable
data class SendResponse(
    val ok: Boolean = false,
    val msg_id: String? = null,
    val group_id: String? = null,
    val thread_id: String? = null,
    val error: String? = null,
)
