package com.phinet.app.vault

import java.io.File
import java.security.SecureRandom

/**
 * Vault crypto over Lockr's native primitives: Argon2id for the master key,
 * XChaCha20-Poly1305 (streaming, file→file) for content. Small helpers here
 * handle tiny buffers (the index, 32-byte keys); large files go through
 * VaultStore's file→file path and never touch these ByteArray helpers.
 */
object VaultCrypto {
    private const val T_COST = 3
    private const val M_COST = 65536   // KiB
    private const val PAR = 2
    private const val TAG = 32

    fun deriveMasterKey(passphrase: String, salt: ByteArray): ByteArray =
        NativeLib.argon2id(passphrase.toByteArray(), salt, T_COST, M_COST, PAR, TAG)
            ?: error("argon2id failed")

    fun randomKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
    fun randomSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    /** Encrypt small [plain] bytes under [key] into [outFile]. */
    fun encryptBytes(cache: File, plain: ByteArray, key: ByteArray, aad: ByteArray, outFile: File) {
        val id = System.nanoTime()
        val inF = File(cache, "eb_$id").apply { writeBytes(plain) }
        val rc = NativeLib.encryptFile(inF.absolutePath, outFile.absolutePath, key, aad)
        inF.delete(); check(rc == 0) { "encryptBytes rc=$rc" }
    }

    /** Decrypt a small [inFile] under [key] into memory. */
    fun decryptBytes(cache: File, inFile: File, key: ByteArray, aad: ByteArray): ByteArray {
        val out = File(cache, "db_${System.nanoTime()}")
        val rc = NativeLib.decryptFile(inFile.absolutePath, out.absolutePath, key, aad)
        check(rc == 0) { "decryptBytes rc=$rc" }
        return out.readBytes().also { out.delete() }
    }

    /** Wrap a 32-byte file key under the master key → ciphertext bytes. */
    fun wrapKey(cache: File, fileKey: ByteArray, master: ByteArray, aad: ByteArray): ByteArray {
        val out = File(cache, "wk_${System.nanoTime()}")
        encryptBytes(cache, fileKey, master, aad, out)
        return out.readBytes().also { out.delete() }
    }
    fun unwrapKey(cache: File, wrapped: ByteArray, master: ByteArray, aad: ByteArray): ByteArray {
        val inF = File(cache, "uk_${System.nanoTime()}").apply { writeBytes(wrapped) }
        return decryptBytes(cache, inF, master, aad).also { inF.delete() }
    }
}
