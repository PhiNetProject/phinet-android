package com.phinet.app.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS         = "lockr_master"
private const val AES_GCM           = "AES/GCM/NoPadding"
private const val GCM_TAG_BITS      = 128
private const val IV_SIZE           = 12

private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    if (!keyStore.containsAlias(KEY_ALIAS)) {
        KeyGenerator.getInstance("AES", KEYSTORE_PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    // Require biometric/device credential confirmation before key use.
                    // Without this, the Keystore key can be used without biometric at OS level.
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(
                        0, // 0 = require auth for every use (not time-based)
                        KeyProperties.AUTH_BIOMETRIC_STRONG
                    )
                    .build()
            )
            generateKey()
        }
    }
    return keyStore.getKey(KEY_ALIAS, null) as SecretKey
}

fun encryptWithKeystore(data: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(AES_GCM).apply {
        init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    }
    return cipher.iv + cipher.doFinal(data)
}

fun decryptWithKeystore(enc: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(AES_GCM).apply {
        init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_BITS, enc, 0, IV_SIZE)
        )
    }
    return cipher.doFinal(enc, IV_SIZE, enc.size - IV_SIZE)
}