package com.phinet.app.feature.vault

import kotlinx.serialization.Serializable

enum class VaultKind { LINK, NOTE, SECRET, FILE }

@Serializable
data class VaultItem(
    val id: String,
    val kind: VaultKind,
    val title: String,
    val content: String = "",          // URL / note / secret (small text items)
    val fileName: String = "",         // for FILE items
    val mime: String = "",             // for FILE items
    val size: Long = 0,                // plaintext size (FILE)
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class VaultState(val items: List<VaultItem> = emptyList())

// Envelope tag so a shared item is rendered as a card in com rather than text.
const val VAULT_SHARE_PREFIX = "\u0001phinet-vault\u0001"
