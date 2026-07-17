package com.phinet.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.phinet.app.node.PhinetService
import com.phinet.app.ui.PhinetRoot
import com.phinet.app.ui.AppLockGate
import com.phinet.app.ui.theme.PhinetTheme
import com.phinet.app.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* best effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)

        requestBatteryExemption()
        PhinetService.start(this)

        val settings = (application as PhinetApp).settings
        setContent {
            val mode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val locked by settings.appLockEnabled.collectAsState(initial = false)
            PhinetTheme(mode = mode) {
                AppLockGate(enabled = locked) { PhinetRoot() }
            }
        }
    }

    /** Ask the user to exempt ΦNET from Doze so the node survives backgrounding. */
    private fun requestBatteryExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            runCatching {
                startActivity(Intent(
                    AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName"),
                ))
            }
        }
    }
}
