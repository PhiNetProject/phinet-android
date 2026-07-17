package com.phinet.app.feature.com

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phinet.app.PhinetApp
import com.phinet.app.data.ComMessage
import com.phinet.app.data.ComRepository
import com.phinet.app.data.Peer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ComUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val myAddress: String = "",
    val nodeId: String = "",
    val threads: List<String> = emptyList(),
    val peers: List<Peer> = emptyList(),
)

class ComViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: ComRepository = (app as PhinetApp).repo

    private val _state = MutableStateFlow(ComUiState())
    val state: StateFlow<ComUiState> = _state.asStateFlow()

    // Per-thread messages, keyed by peer node-id.
    private val _messages = MutableStateFlow<List<ComMessage>>(emptyList())
    val messages: StateFlow<List<ComMessage>> = _messages.asStateFlow()

    // Locally hidden messages (delete-from-my-view). Keyed by timestamp+body.
    private val _hidden = MutableStateFlow<Set<String>>(emptySet())
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()
    fun hide(m: ComMessage) { _hidden.value = _hidden.value + msgKey(m) }

    /** Real unsend: remove on the daemon (gone from the thread store, and a
     *  delete marker is sent to the peer) + hide locally for instant feedback. */
    fun deleteMessage(peer: String, m: ComMessage) {
        _hidden.value = _hidden.value + msgKey(m)
        viewModelScope.launch {
            if (m.msg_id.isNotEmpty()) runCatching { repo.deleteMessage(peer, m.msg_id) }
        }
    }
    fun msgKey(m: ComMessage) = "${m.timestamp}:${m.body.hashCode()}"

    fun refresh() = viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        try {
            val who = repo.whoami()
            val addr = runCatching { repo.myAddress() }.getOrDefault("")
            val threads = repo.threads()
            val peers = runCatching { repo.peers() }.getOrDefault(emptyList())
            _state.value = ComUiState(
                loading = false, myAddress = addr, nodeId = who.node_id,
                threads = threads, peers = peers,
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(loading = false, error = friendly(e))
        }
    }

    fun loadThread(peer: String) = viewModelScope.launch {
        _messages.value = runCatching { repo.thread(peer) }.getOrDefault(emptyList())
    }

    fun send(peer: String, text: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        val r = runCatching { repo.send(peer, text) }.getOrNull()
        if (r?.error != null) _state.value = _state.value.copy(error = r.error)
        loadThread(peer)
        onDone()
    }

    fun addContact(address: String, onOpen: (String) -> Unit) = viewModelScope.launch {
        val r = runCatching { repo.addContact(address) }.getOrNull()
        when {
            r == null -> _state.value = _state.value.copy(error = "Couldn't reach the daemon")
            r.node_id != null -> { refresh(); onOpen(r.node_id) }
            else -> _state.value = _state.value.copy(error = r.error ?: "Invalid address")
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }

    private fun friendly(e: Exception): String =
        "Can't reach the com daemon. Check the API address in Settings.\n(${e.message ?: e.javaClass.simpleName})"
}
