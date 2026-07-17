package com.phinet.app.node

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Runs the embedded `phinet-daemon` as a child process.
 *
 * Android forbids executing binaries from the app data dir (W^X on API 29+),
 * but native libraries extracted to `nativeLibraryDir` are executable. So we
 * ship the cross-compiled daemon as `libphinetdaemon.so` in `jniLibs/<abi>/`
 * — Android extracts it, and we exec it from there. This is the same pattern
 * Tor's Android app uses.
 */
class NativeDaemon(private val ctx: Context) {

    @Volatile private var process: Process? = null

    val phinetHome: File get() = File(ctx.filesDir, ".phinet").apply { mkdirs() }

    private fun binary(): File =
        File(ctx.applicationInfo.nativeLibraryDir, "libphinetdaemon.so")

    fun isPackaged(): Boolean = binary().exists()

    fun isRunning(): Boolean = process?.isAlive == true

    @Synchronized
    fun start() {
        if (isRunning()) return
        // The control client needs to find the daemon's cookie to authenticate
        // to it; both live under filesDir, which only this app can read.
        CtlClient.dataDir = ctx.filesDir
        val bin = binary()
        if (!bin.exists()) {
            Log.e(TAG, "daemon binary missing: ${bin.absolutePath} — did you run build-android.sh?")
            return
        }

        val args = buildList {
            add(bin.absolutePath)
            add("--port"); add(NodeConfig.LINK_PORT.toString())
            add("--host"); add("127.0.0.1")
            add("--ctl-port"); add(NodeConfig.CTL_PORT.toString())
            add("--cert-bits"); add("1024")
            add("--consensus-url"); add(NodeConfig.CONSENSUS_URL)
            NodeConfig.BOOTSTRAP.forEach { add("--bootstrap"); add(it) }
            NodeConfig.TRUSTED_AUTHORITIES.forEach { add("--trusted-authority"); add(it) }
        }

        Log.i(TAG, "starting embedded daemon: ${args.joinToString(" ")}")
        process = ProcessBuilder(args)
            .directory(ctx.filesDir)
            .redirectErrorStream(true)
            .apply {
                // The daemon reads $HOME/.phinet for its identity + consensus.
                environment()["HOME"] = ctx.filesDir.absolutePath
            }
            .start()

        // Drain output to logcat so the node is observable.
        Thread {
            process?.inputStream?.bufferedReader()?.forEachLine { Log.i(TAG_NODE, it) }
        }.apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        process?.destroy()
        process = null
    }

    companion object {
        private const val TAG = "PhinetNative"
        private const val TAG_NODE = "phinet-daemon"
    }
}
