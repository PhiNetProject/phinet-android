package com.phinet.app.ui

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.phinet.app.ui.components.BrandMark

/**
 * Optional biometric gate over the whole app (so com history + contacts
 * aren't visible if someone picks up your unlocked phone). This is an
 * *access* lock — distinct from the Vault's passphrase, which is a
 * *cryptographic* key. The node keeps running behind the lock, so messages
 * still arrive; only the UI is gated.
 */
@Composable
fun AppLockGate(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) { content(); return }

    var unlocked by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    if (unlocked) { content(); return }

    val prompt: () -> Unit = prompt@{
        val activity = ctx as? FragmentActivity ?: run { unlocked = true; return@prompt }
        val can = BiometricManager.from(ctx).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        if (can != BiometricManager.BIOMETRIC_SUCCESS) { unlocked = true; return@prompt } // no biometrics enrolled → don't lock out
        val bp = BiometricPrompt(activity, ContextCompat.getMainExecutor(ctx),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) { unlocked = true }
                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                }
            })
        bp.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock ΦNET")
                .setSubtitle("Authenticate to open com, browser, and vault")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build())
    }

    LaunchedEffect(Unit) { prompt() }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            BrandMark(title = "ΦNET")
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(16.dp))
            Text("Locked", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(16.dp))
            Button(onClick = prompt) { Text("Unlock") }
        }
    }
}
