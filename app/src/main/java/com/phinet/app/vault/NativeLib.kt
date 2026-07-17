package com.phinet.app.vault

import java.nio.ByteBuffer

object NativeLib {
    init { System.loadLibrary("lockr") }

    /**
     * Encrypts a file using XChaCha20-Poly1305.
     * Throws IOException if file operations or encryption fail.
     */
    external fun encryptFile(inPath: String, outPath: String, key: ByteArray, aad: ByteArray?): Int

    /**
     * Decrypts a file using XChaCha20-Poly1305.
     * Throws IOException if authentication fails or file operations fail.
     */
    external fun decryptFile(inPath: String, outPath: String, key: ByteArray, aad: ByteArray?): Int

    /**
     * Securely wipes a Direct ByteBuffer by filling it with zeros in native memory.
     */
    external fun secureClear(buffer: ByteBuffer)

    /**
     * Derives a cryptographic key from a password using Argon2id.
     */
    external fun argon2id(
        password: ByteArray,
        salt: ByteArray,
        t_cost: Int,
        m_cost: Int,
        parallelism: Int,
        tagLength: Int
    ): ByteArray?

    /**
     * Starts a SPAKE2 handshake.
     * @param passphrase The short shared secret
     * @param isAlice True if this node is the initiator
     * @return The 32-byte public message to send to the peer
     */
    external fun spake2Init(passphrase: String, isAlice: Boolean): ByteArray?

    /**
     * Finishes a SPAKE2 handshake.
     * @param peerMsg The 32-byte public message received from the peer
     * @return The 32-byte shared session key
     */
    external fun spake2Finish(peerMsg: ByteArray): ByteArray?
}