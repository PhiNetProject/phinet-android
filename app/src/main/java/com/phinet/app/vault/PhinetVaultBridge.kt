package com.phinet.app.vault

import com.phinet.app.data.ComRepository
import com.phinet.app.feature.vault.VaultItem
import com.phinet.app.feature.vault.VAULT_SHARE_PREFIX
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Shares vault items over ΦNET — the replacement for Lockr's Nearby/Bluetooth
 * mesh. The crucial simplification: com already seals every message to the
 * recipient's key (sealed sender + blinded address), so we don't need Lockr's
 * SPAKE2 pairing or transit-key exchange. We just send the item to a contact
 * and the ΦNET com layer provides the confidential, authenticated transport.
 *
 * The item travels as a normal com message tagged with VAULT_SHARE_PREFIX so
 * the receiver renders it as a vault card (and can save it into their vault).
 */
object PhinetVaultBridge {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun shareItem(com: ComRepository, peerNodeId: String, item: VaultItem): Result<Unit> {
        val body = VAULT_SHARE_PREFIX + json.encodeToString(item.copy(id = "shared"))
        val r = runCatching { com.send(peerNodeId, body) }.getOrNull()
        return if (r?.error == null) Result.success(Unit)
               else Result.failure(IllegalStateException(r.error))
    }

    fun decodeShared(body: String): VaultItem? {
        if (!body.startsWith(VAULT_SHARE_PREFIX)) return null
        return runCatching {
            json.decodeFromString<VaultItem>(body.removePrefix(VAULT_SHARE_PREFIX))
        }.getOrNull()
    }
}
