package com.phinet.app.vault

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class IntegrityReport(
    val totalFiles: Int,
    val healthy: List<String>,
    val missingKey: List<String>,
    val orphanedKeys: List<String>,
    val partialFiles: List<String>,
) {
    val isHealthy get() = missingKey.isEmpty() && orphanedKeys.isEmpty() && partialFiles.isEmpty()
    val issueCount get() = missingKey.size + orphanedKeys.size + partialFiles.size
}

/** Scans blobs/ + keys/ for missing keys, orphaned keys, and partial writes
 *  (auto-wiped). Ported from Lockr, adapted to the ΦNET vault layout. */
object VaultIntegrityChecker {
    private const val MIN_VALID_BYTES = 28L  // nonce + tag floor

    suspend fun check(blobs: File, keys: File): IntegrityReport = withContext(Dispatchers.IO) {
        val blobFiles = blobs.listFiles()?.filter { it.name.endsWith(".enc") } ?: emptyList()
        val keyNames  = (keys.listFiles()?.map { it.name } ?: emptyList()).toSet()

        val healthy = mutableListOf<String>(); val missingKey = mutableListOf<String>()
        val partial = mutableListOf<String>()
        blobFiles.forEach { f ->
            val id = f.name.removeSuffix(".enc")
            when {
                f.length() < MIN_VALID_BYTES -> {
                    f.delete(); File(keys, "$id.key").delete(); partial.add(id)
                }
                keyNames.contains("$id.key") -> healthy.add(id)
                else -> missingKey.add(id)
            }
        }
        val blobIds = blobFiles.map { it.name.removeSuffix(".enc") }.toSet()
        val orphaned = (keys.listFiles()?.filter { it.name.endsWith(".key") } ?: emptyList())
            .map { it.name.removeSuffix(".key") }.filter { it !in blobIds }

        IntegrityReport(blobFiles.size, healthy, missingKey, orphaned, partial)
    }

    suspend fun removeOrphans(keys: File, ids: List<String>) = withContext(Dispatchers.IO) {
        ids.forEach { File(keys, "$it.key").delete() }
    }
}
