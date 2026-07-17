package com.phinet.app.vault

import android.content.Context
import com.phinet.app.feature.vault.VaultItem
import java.util.UUID

/** Save a received (shared) item into the local vault, if it's unlocked.
 *  Returns false if the vault is locked (caller should prompt to unlock). */
object VaultQuickSave {
    fun save(context: Context, item: VaultItem): Boolean {
        val master = VaultSession.masterKey ?: return false
        val store = VaultStore(context)
        val items = runCatching { store.loadIndex(master) }.getOrDefault(emptyList())
        store.addText(items, item.copy(id = UUID.randomUUID().toString()), master)
        return true
    }
}
