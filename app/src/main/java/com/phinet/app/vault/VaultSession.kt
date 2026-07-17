package com.phinet.app.vault

/** Holds the unlocked master key in memory for the session. Cleared on lock. */
object VaultSession {
    @Volatile var masterKey: ByteArray? = null
        private set
    val isUnlocked get() = masterKey != null
    fun unlock(key: ByteArray) { masterKey = key }
    fun lock() { masterKey?.fill(0); masterKey = null }
}
