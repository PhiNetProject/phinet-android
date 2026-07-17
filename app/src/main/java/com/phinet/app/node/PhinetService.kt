package com.phinet.app.node

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.phinet.app.R

/**
 * Foreground service that owns the embedded ΦNET node's lifecycle. Keeping
 * it foreground (with a persistent notification) is what lets the node stay
 * connected — building circuits, pulling the consensus, and receiving com —
 * while the app is backgrounded. A partial wakelock keeps the CPU alive for
 * the network loops.
 */
class PhinetService : Service() {

    private lateinit var daemon: NativeDaemon
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        daemon = NativeDaemon(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = notification("Running — ΦNET node online")
        // Android 10+ accepts a foreground-service type; Android 14 requires it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "phinet:node").apply {
            setReferenceCounted(false); acquire()
        }
        daemon.start()
        return START_STICKY
    }

    override fun onDestroy() {
        daemon.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL, "ΦNET node", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Keeps the ΦNET node connected" }
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(ch)
        }
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("ΦNET")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_phi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    companion object {
        private const val CHANNEL = "phinet_node"
        private const val NOTIF_ID = 1

        fun start(ctx: Context) {
            val i = Intent(ctx, PhinetService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
            else ctx.startService(i)
        }
        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, PhinetService::class.java))
        }
    }
}
