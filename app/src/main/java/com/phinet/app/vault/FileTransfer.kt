package com.phinet.app.vault

import android.util.Base64
import com.phinet.app.data.ComRepository
import com.phinet.app.feature.vault.VaultItem
import com.phinet.app.feature.vault.VaultKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Sharing FILE items over ΦNET. com messages are size-capped, so a file is
 * split into base64 chunks, each sent as one sealed com message with a small
 * header. The receiver reassembles by fileId. com provides the confidential
 * transport (sealed sender + blinded address) — no extra pairing needed.
 *
 * Wire format per chunk message body:
 *   \u0001phinet-file\u0001 <base64(headerJson)> | <base64(chunkBytes)>
 */
object FileTransfer {
    const val PREFIX = "\u0001phinet-file\u0001"
    const val CHUNK = 12 * 1024   // 12 KiB binary → ~16 KiB base64
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Header(val fileId: String, val name: String, val mime: String,
                      val total: Int, val index: Int)

    fun isChunk(body: String) = body.startsWith(PREFIX)

    fun parse(body: String): Pair<Header, ByteArray>? {
        if (!isChunk(body)) return null
        val rest = body.removePrefix(PREFIX)
        val sep = rest.indexOf('|'); if (sep < 0) return null
        val header = runCatching {
            json.decodeFromString<Header>(String(Base64.decode(rest.substring(0, sep), Base64.NO_WRAP)))
        }.getOrNull() ?: return null
        val bytes = runCatching { Base64.decode(rest.substring(sep + 1), Base64.NO_WRAP) }.getOrNull() ?: return null
        return header to bytes
    }

    /** Send a decrypted file to a peer as chunked com messages. */
    suspend fun send(com: ComRepository, peerNodeId: String, fileId: String,
                     name: String, mime: String, plaintext: File,
                     onProgress: (Int, Int) -> Unit = { _, _ -> }): Result<Unit> {
        val total = ((plaintext.length() + CHUNK - 1) / CHUNK).toInt().coerceAtLeast(1)
        plaintext.inputStream().use { input ->
            val buf = ByteArray(CHUNK)
            var index = 0; var n: Int
            while (input.read(buf).also { n = it } > 0) {
                val head = Base64.encodeToString(
                    json.encodeToString(Header(fileId, name, mime, total, index)).toByteArray(), Base64.NO_WRAP)
                val data = Base64.encodeToString(buf.copyOf(n), Base64.NO_WRAP)
                val r = runCatching { com.send(peerNodeId, "$PREFIX$head|$data") }.getOrNull()
                if (r?.error != null) return Result.failure(IllegalStateException(r.error))
                index++; onProgress(index, total)
            }
        }
        return Result.success(Unit)
    }
}

/**
 * Idempotent receiver: writes each chunk at its correct offset in a per-file
 * temp, tracking which indices have arrived. Safe to re-feed the same chunk
 * (e.g. when the thread is re-polled) — it just rewrites the same offset.
 * Memory-light: only one chunk is in RAM at a time.
 */
class FileReceiver(private val cacheDir: File) {
    private val seen = HashMap<String, MutableSet<Int>>()
    private val done = HashSet<String>()

    fun offer(body: String): Pair<com.phinet.app.feature.vault.VaultItem, File>? {
        val (h, bytes) = FileTransfer.parse(body) ?: return null
        if (h.fileId in done) return null
        val out = File(cacheDir, "recv_${h.fileId}")
        java.io.RandomAccessFile(out, "rw").use { raf ->
            raf.seek(h.index.toLong() * FileTransfer.CHUNK)
            raf.write(bytes)
        }
        val set = seen.getOrPut(h.fileId) { mutableSetOf() }
        set.add(h.index)
        if (set.size < h.total) return null
        done.add(h.fileId); seen.remove(h.fileId)
        val item = com.phinet.app.feature.vault.VaultItem(
            h.fileId, com.phinet.app.feature.vault.VaultKind.FILE, h.name, "", h.name, h.mime, out.length())
        return item to out
    }
}
