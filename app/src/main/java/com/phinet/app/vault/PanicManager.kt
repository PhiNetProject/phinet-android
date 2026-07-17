package com.phinet.app.vault

import android.content.Context
import java.io.File

/** Emergency wipe. `panicLock` clears the session + cache; `duressWipe`
 *  destroys the entire vault irrecoverably. Ported/adapted from Lockr. */
object PanicManager {
    fun panicLock(context: Context) {
        VaultSession.lock()
        context.cacheDir.deleteRecursively()
    }
    /** Irrecoverable: deletes all vault blobs, keys, index, and salt. */
    fun duressWipe(context: Context) {
        VaultSession.lock()
        File(context.filesDir, "vault").deleteRecursively()
        context.cacheDir.deleteRecursively()
    }
}
