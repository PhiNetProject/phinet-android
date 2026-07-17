package com.phinet.app.vault

import android.content.Context
import android.net.Uri
import com.phinet.app.feature.vault.VaultItem
import com.phinet.app.feature.vault.VaultKind
import com.phinet.app.feature.vault.VaultState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Encrypted vault storage with **constant-memory streaming** for files.
 *
 * - A tiny metadata index (item list, small text items) is sealed as one
 *   blob under the master key — safe to hold in memory (it's small).
 * - FILE items are NEVER loaded whole into memory. They're encrypted
 *   file→file by the native layer (64 KiB streaming) into `blobs/<id>.enc`,
 *   each under its own random key wrapped under the master key in
 *   `keys/<id>.key`. Viewing decrypts file→file into a private cache temp
 *   which the viewer reads by path, then the temp is wiped.
 *
 * This is why large videos/PDFs work: at no point does a whole file sit in
 * RAM — the only in-memory bytes are the small index and 32-byte keys.
 */
class VaultStore(private val context: Context) {
    private val dir   = File(context.filesDir, "vault").apply { mkdirs() }
    private val blobs = File(dir, "blobs").apply { mkdirs() }
    private val keys  = File(dir, "keys").apply { mkdirs() }
    private val cache = context.cacheDir
    private val index = File(dir, "index.enc")
    private val saltF = File(dir, "vault.salt")
    private val json  = Json { ignoreUnknownKeys = true }
    private val aad   = "phinet_vault_v1".toByteArray()

    fun exists(): Boolean = saltF.exists()

    fun create(passphrase: String): ByteArray {
        val salt = VaultCrypto.randomSalt(); saltF.writeBytes(salt)
        val master = VaultCrypto.deriveMasterKey(passphrase, salt)
        saveIndex(emptyList(), master)
        return master
    }

    fun unlock(passphrase: String): ByteArray? {
        if (!exists()) return null
        val master = VaultCrypto.deriveMasterKey(passphrase, saltF.readBytes())
        return try { loadIndex(master); master } catch (e: Exception) { null }
    }

    // ── index (small, in-memory is fine) ──
    fun loadIndex(master: ByteArray): List<VaultItem> {
        if (!index.exists()) return emptyList()
        val plain = VaultCrypto.decryptBytes(cache, index, master, aad)
        return json.decodeFromString<VaultState>(String(plain)).items
    }
    private fun saveIndex(items: List<VaultItem>, master: ByteArray) {
        val plain = json.encodeToString(VaultState(items)).toByteArray()
        VaultCrypto.encryptBytes(cache, plain, master, aad, index)
    }

    fun addText(items: List<VaultItem>, item: VaultItem, master: ByteArray): List<VaultItem> {
        val next = listOf(item) + items; saveIndex(next, master); return next
    }

    fun delete(items: List<VaultItem>, id: String, master: ByteArray): List<VaultItem> {
        File(blobs, "$id.enc").delete()
        File(keys, "$id.key").delete()
        val next = items.filterNot { it.id == id }; saveIndex(next, master); return next
    }

    // ── FILE items: streamed file→file, never whole-in-memory ──

    /** Import a file from a SAF uri: copy → streaming-encrypt → store. Returns the new item. */
    fun importFile(uri: Uri, title: String, fileName: String, mime: String,
                   items: List<VaultItem>, master: ByteArray): Pair<VaultItem, List<VaultItem>> {
        val id = java.util.UUID.randomUUID().toString()
        val tmpIn = File(cache, "imp_$id")
        // Stream the source into a private temp (constant memory).
        context.contentResolver.openInputStream(uri)!!.use { input ->
            tmpIn.outputStream().use { input.copyTo(it, bufferSize = 1 shl 16) }
        }
        val size = tmpIn.length()
        val fileKey = VaultCrypto.randomKey()
        val dst = File(blobs, "$id.enc")
        val rc = NativeLib.encryptFile(tmpIn.absolutePath, dst.absolutePath, fileKey, aad)
        wipe(tmpIn)
        check(rc == 0) { "encryptFile rc=$rc" }
        // Wrap the file key under the master key and persist it.
        File(keys, "$id.key").writeBytes(VaultCrypto.wrapKey(cache, fileKey, master, aad))
        fileKey.fill(0)
        val item = VaultItem(id, VaultKind.FILE, title.ifEmpty { fileName }, "", fileName, mime, size)
        val next = listOf(item) + items; saveIndex(next, master)
        return item to next
    }

    /** Encrypt an already-plaintext File into the vault (e.g. a received transfer). */
    fun importPlainFile(src: File, title: String, fileName: String, mime: String,
                        items: List<VaultItem>, master: ByteArray): Pair<VaultItem, List<VaultItem>> {
        val id = java.util.UUID.randomUUID().toString()
        val fileKey = VaultCrypto.randomKey()
        val dst = File(blobs, "$id.enc")
        val rc = NativeLib.encryptFile(src.absolutePath, dst.absolutePath, fileKey, aad)
        check(rc == 0) { "encryptFile rc=$rc" }
        File(keys, "$id.key").writeBytes(VaultCrypto.wrapKey(cache, fileKey, master, aad))
        fileKey.fill(0)
        val item = VaultItem(id, VaultKind.FILE, title.ifEmpty { fileName }, "", fileName, mime, src.length())
        val next = listOf(item) + items; saveIndex(next, master)
        return item to next
    }

    /** Decrypt a FILE item into a private cache temp for viewing (streamed). Caller wipes it. */
    fun decryptToTemp(item: VaultItem, master: ByteArray): File {
        val wrapped = File(keys, "${item.id}.key").readBytes()
        val fileKey = VaultCrypto.unwrapKey(cache, wrapped, master, aad)
        val out = File(cache, "view_${item.id}_${item.fileName.ifEmpty { "file" }}")
        val rc = NativeLib.decryptFile(File(blobs, "${item.id}.enc").absolutePath, out.absolutePath, fileKey, aad)
        fileKey.fill(0)
        check(rc == 0) { "decryptFile rc=$rc" }
        return out
    }

    fun wipe(f: File) {
        if (!f.exists()) return
        runCatching {
            // Overwrite in 1 MiB chunks (don't allocate file-length in RAM).
            f.outputStream().use { os ->
                val z = ByteArray(1 shl 20); var left = f.length()
                while (left > 0) { val n = minOf(left, z.size.toLong()).toInt(); os.write(z, 0, n); left -= n }
            }
        }
        f.delete()
    }
}
