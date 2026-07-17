package com.phinet.app.feature.com

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phinet.app.PhinetApp
import com.phinet.app.data.ComMessage
import com.phinet.app.data.ComRepository
import com.phinet.app.data.Group
import com.phinet.app.data.Peer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GroupsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: ComRepository = (app as PhinetApp).repo

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    private val _messages = MutableStateFlow<List<ComMessage>>(emptyList())
    val messages: StateFlow<List<ComMessage>> = _messages.asStateFlow()

    private val _contacts = MutableStateFlow<List<Peer>>(emptyList())
    val contacts: StateFlow<List<Peer>> = _contacts.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun clearToast() { _toast.value = null }

    fun refresh() = viewModelScope.launch {
        _groups.value = runCatching { repo.groups() }.getOrDefault(emptyList())
    }

    fun create(name: String, channel: Boolean) = viewModelScope.launch {
        val r = runCatching { repo.createGroup(name, channel) }.getOrNull()
        if (r?.error != null) _toast.value = r.error else refresh()
    }

    fun loadThread(threadId: String) = viewModelScope.launch {
        _messages.value = runCatching { repo.thread(threadId) }.getOrDefault(emptyList())
    }

    fun send(groupId: String, threadId: String, text: String) = viewModelScope.launch {
        val r = runCatching { repo.sendGroup(groupId, text) }.getOrNull()
        if (r?.error != null) _toast.value = r.error
        loadThread(threadId)
    }

    fun loadContacts() = viewModelScope.launch {
        _contacts.value = runCatching { repo.peers() }.getOrDefault(emptyList())
    }

    fun invite(groupId: String, peerNodeId: String) = viewModelScope.launch {
        val r = runCatching { repo.invite(groupId, peerNodeId) }.getOrNull()
        _toast.value = if (r?.error == null) "Invite sent" else "Invite failed: ${r.error}"
    }
}
