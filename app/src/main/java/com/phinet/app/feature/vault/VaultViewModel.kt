package com.phinet.app.feature.vault

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.phinet.app.PhinetApp
import com.phinet.app.data.ComRepository
import com.phinet.app.data.Peer
import com.phinet.app.vault.PhinetVaultBridge
import com.phinet.app.vault.VaultSession
import com.phinet.app.vault.VaultStore
import com.phinet.app.vault.FailedAttemptTracker
import com.phinet.app.vault.PanicManager
import com.phinet.app.vault.FileTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class VaultUi(
    val exists: Boolean = false,
    val unlocked: Boolean = false,
    val items: List<VaultItem> = emptyList(),
    val error: String? = null,
    val busy: Boolean = false,
)

/** A decrypted file ready to view (path in private cache) — wiped on close. */
data class OpenFile(val item: VaultItem, val file: File)

class VaultViewModel(app: Application) : AndroidViewModel(app) {
    private val store = VaultStore(app)
    private val com: ComRepository = (app as PhinetApp).repo

    private val _ui = MutableStateFlow(VaultUi(exists = store.exists(), unlocked = VaultSession.isUnlocked))
    val ui: StateFlow<VaultUi> = _ui.asStateFlow()

    // People you can share to = your com contacts (threads you've opened /
    // addresses you've added), NOT the relay list.
    private val _shareTargets = MutableStateFlow<List<String>>(emptyList())
    val shareTargets: StateFlow<List<String>> = _shareTargets.asStateFlow()

    private val _open = MutableStateFlow<OpenFile?>(null)
    val open: StateFlow<OpenFile?> = _open.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun clearToast() { _toast.value = null }

    init { if (VaultSession.isUnlocked) refreshItems() }

    fun create(passphrase: String) = auth(passphrase, creating = true)
    fun unlock(passphrase: String) = auth(passphrase, creating = false)

    private fun auth(passphrase: String, creating: Boolean) = viewModelScope.launch {
        if (passphrase.length < 6) { _ui.value = _ui.value.copy(error = "Use at least 6 characters"); return@launch }
        if (!creating && FailedAttemptTracker.isLockedOut()) {
            _ui.value = _ui.value.copy(error = FailedAttemptTracker.message()); return@launch
        }
        _ui.value = _ui.value.copy(busy = true, error = null)
        if (!creating) FailedAttemptTracker.awaitBackoff()
        val master = withContext(Dispatchers.Default) {
            runCatching { if (creating) store.create(passphrase) else store.unlock(passphrase) }.getOrNull()
        }
        if (master == null) {
            if (!creating) FailedAttemptTracker.recordFailure()
            _ui.value = _ui.value.copy(busy = false,
                error = if (creating) "Couldn't create vault"
                        else "Wrong passphrase. " + FailedAttemptTracker.message())
            return@launch
        }
        FailedAttemptTracker.recordSuccess()
        VaultSession.unlock(master)
        _ui.value = _ui.value.copy(busy = false, exists = true, unlocked = true)
        refreshItems()
    }

    /** Irrecoverable emergency wipe of the entire vault. */
    fun panicWipe() = viewModelScope.launch {
        PanicManager.duressWipe(getApplication())
        _ui.value = VaultUi(exists = false, unlocked = false)
        _toast.value = "Vault wiped"
    }

    fun lock() { VaultSession.lock(); _ui.value = _ui.value.copy(unlocked = false, items = emptyList()) }

    private fun refreshItems() = viewModelScope.launch {
        val master = VaultSession.masterKey ?: return@launch
        val items = withContext(Dispatchers.IO) { runCatching { store.loadIndex(master) }.getOrDefault(emptyList()) }
        _ui.value = _ui.value.copy(items = items)
    }

    fun addText(kind: VaultKind, title: String, content: String) = viewModelScope.launch {
        val master = VaultSession.masterKey ?: return@launch
        val item = VaultItem(UUID.randomUUID().toString(), kind, title.trim(), content.trim())
        val next = withContext(Dispatchers.IO) { store.addText(_ui.value.items, item, master) }
        _ui.value = _ui.value.copy(items = next)
    }

    fun importFile(uri: Uri) = viewModelScope.launch {
        val master = VaultSession.masterKey ?: return@launch
        _ui.value = _ui.value.copy(busy = true)
        val (name, mime) = queryMeta(uri)
        val next = withContext(Dispatchers.IO) {
            runCatching { store.importFile(uri, name, name, mime, _ui.value.items, master).second }.getOrNull()
        }
        _ui.value = _ui.value.copy(busy = false, items = next ?: _ui.value.items,
            error = if (next == null) "Import failed" else null)
    }

    fun openFile(item: VaultItem) = viewModelScope.launch {
        val master = VaultSession.masterKey ?: return@launch
        val f = withContext(Dispatchers.IO) { runCatching { store.decryptToTemp(item, master) }.getOrNull() }
        if (f != null) _open.value = OpenFile(item, f) else _toast.value = "Couldn't open file"
    }

    fun closeFile() {
        _open.value?.let { of -> viewModelScope.launch(Dispatchers.IO) { store.wipe(of.file) } }
        _open.value = null
    }

    fun delete(id: String) = viewModelScope.launch {
        val master = VaultSession.masterKey ?: return@launch
        val next = withContext(Dispatchers.IO) { store.delete(_ui.value.items, id, master) }
        _ui.value = _ui.value.copy(items = next)
    }

    fun loadShareTargets() = viewModelScope.launch {
        _shareTargets.value = runCatching { com.threads() }.getOrDefault(emptyList())
    }

    /** Share a small item over ΦNET (com seals it). Files aren't inlined —
     *  they'd need chunked transfer; text/links/secrets share directly. */
    fun share(item: VaultItem, peerNodeId: String) = viewModelScope.launch {
        if (item.kind == VaultKind.FILE) {
            val master = VaultSession.masterKey ?: return@launch
            _toast.value = "Sending file over ΦNET…"
            val temp = withContext(Dispatchers.IO) { runCatching { store.decryptToTemp(item, master) }.getOrNull() }
            if (temp == null) { _toast.value = "Couldn't read file"; return@launch }
            val r = FileTransfer.send(com, peerNodeId, item.id, item.fileName, item.mime, temp) { i, n ->
                _toast.value = "Sending $i/$n"
            }
            withContext(Dispatchers.IO) { store.wipe(temp) }
            _toast.value = if (r.isSuccess) "File shared over ΦNET" else "Share failed: ${r.exceptionOrNull()?.message}"
            return@launch
        }
        val r = PhinetVaultBridge.shareItem(com, peerNodeId, item)
        _toast.value = if (r.isSuccess) "Shared over ΦNET" else "Share failed: ${r.exceptionOrNull()?.message}"
    }

    private fun queryMeta(uri: Uri): Pair<String, String> {
        val cr = getApplication<Application>().contentResolver
        var name = "file"
        cr.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0 && c.moveToFirst()) name = c.getString(i) ?: name
        }
        val mime = cr.getType(uri) ?: "application/octet-stream"
        return name to mime
    }
}
