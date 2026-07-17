package com.phinet.app.vault

import kotlinx.coroutines.delay

private const val MAX_ATTEMPTS  = 5
private const val BASE_DELAY_MS = 1_000L
private const val MAX_DELAY_MS  = 30_000L

/** Exponential-backoff lockout for the vault passphrase (in-memory; resets on
 *  process death, which is intentional). Ported from Lockr. */
object FailedAttemptTracker {
    private var attempts = 0
    fun recordFailure() { attempts++ }
    fun recordSuccess() { attempts = 0 }
    fun count() = attempts
    fun isLockedOut() = attempts >= MAX_ATTEMPTS
    suspend fun awaitBackoff() {
        if (attempts == 0) return
        delay(minOf(BASE_DELAY_MS * (1L shl (attempts - 1)), MAX_DELAY_MS))
    }
    fun message(): String = when {
        attempts >= MAX_ATTEMPTS -> "Too many attempts. Wait up to 30 seconds."
        attempts > 0 -> "$attempts failed. ${MAX_ATTEMPTS - attempts} left before lockout."
        else -> ""
    }
}
