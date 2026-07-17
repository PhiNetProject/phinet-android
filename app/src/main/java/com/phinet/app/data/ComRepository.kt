package com.phinet.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow

/**
 * Thin repository over [ComApi] that rebinds the API whenever the configured
 * base URL changes, and exposes suspend calls the ViewModels use.
 */
class ComRepository(private val settings: Settings) {

    private suspend fun api(): ComApi = Network.comApi(settings.comBaseUrl.first())

    suspend fun whoami(): WhoAmI = api().whoami()
    suspend fun myAddress(): String = api().myAddress().address
    suspend fun peers(): List<Peer> = api().peers().peers
    suspend fun threads(): List<String> = api().threads().threads
    suspend fun thread(peer: String): List<ComMessage> = api().thread(peer).messages
    suspend fun deleteMessage(peer: String, msgId: String) = api().delete(peer, msgId)
    suspend fun send(peer: String, text: String): SendResponse = api().send(peer, text)
    suspend fun addContact(address: String): AddContactResponse = api().addContact(address)
    suspend fun groups(): List<Group> = api().groups().groups
    suspend fun createGroup(name: String, channel: Boolean) = api().createGroup(name, channel)
    suspend fun sendGroup(group: String, text: String) = api().sendGroup(group, text)
    suspend fun invite(group: String, peer: String) = api().invite(group, peer)

    val comBaseUrl: Flow<String> = settings.comBaseUrl
}
