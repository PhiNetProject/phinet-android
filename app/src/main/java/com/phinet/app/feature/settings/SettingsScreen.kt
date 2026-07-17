package com.phinet.app.feature.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.phinet.app.PhinetApp
import com.phinet.app.data.Settings
import com.phinet.app.ui.components.BrandMark
import com.phinet.app.ui.components.PhinetTopBar
import com.phinet.app.ui.components.BUILTIN_BACKGROUNDS
import com.phinet.app.ui.components.builtin
import com.phinet.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { (ctx.applicationContext as PhinetApp).settings }
    val scope = rememberCoroutineScope()

    val mode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val bg by settings.chatBackground.collectAsState(initial = Settings.DEFAULT_BG)
    val appLock by settings.appLockEnabled.collectAsState(initial = false)
    var comUrl by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { comUrl = settings.comBaseUrl.first() }

    // Photo picker for a custom chat background — copies into app storage.
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            val dest = File(ctx.filesDir, "chat_bg.jpg")
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
                settings.setChatBackground("custom:${dest.absolutePath}")
            }
        }
    }

    Scaffold(
        topBar = {
            PhinetTopBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = "ΦNET",
                subtitle = "settings",
                )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).verticalScrollCompat(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Appearance ──
            SettingsCard("Appearance") {
                Text("Theme", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { scope.launch { settings.setThemeMode(m) } },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Chat background", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        // Upload tile
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    pickImage.launch(androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Default.Add, "Upload") }
                    }
                    items(BUILTIN_BACKGROUNDS) { id ->
                        val selected = bg == id
                        Box(
                            Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                                .background(builtin(id, dark = true))
                                .then(if (selected) Modifier.border(
                                    2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                                .clickable { scope.launch { settings.setChatBackground(id) } },
                        )
                    }
                }
                if (bg.startsWith("custom:")) {
                    Spacer(Modifier.height(6.dp))
                    Text("Using a custom background", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ── Node / com API ──
            SettingsCard("com daemon API") {
                Text("Where the on-device node serves its com API. Leave as the " +
                     "loopback default unless you're pointing at a remote daemon.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(comUrl, { comUrl = it }, label = { Text("Base URL") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch { settings.setComBaseUrl(comUrl) } }) { Text("Save") }
                    TextButton(onClick = {
                        comUrl = com.phinet.app.node.NodeConfig.COM_BASE_URL
                        scope.launch { settings.setComBaseUrl(comUrl) }
                    }) { Text("Reset") }
                }
            }

            SettingsCard("Security") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("App lock", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text("Require biometrics/PIN to open the app. The node keeps " +
                             "running behind the lock, so messages still arrive.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = appLock, onCheckedChange = { scope.launch { settings.setAppLock(it) } })
                }
                Spacer(Modifier.height(6.dp))
                Text("The Vault is separately encrypted with its own passphrase " +
                     "(that passphrase derives the decryption key — the app lock does not).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SettingsCard("About") {
                Text("ΦNET — a metadata-private overlay network. This device runs a " +
                     "full client node: com (sealed sender, add-by-key), a browser for " +
                     "the open web and .phinet hidden services, and a Vault whose items " +
                     "you can share to contacts over ΦNET.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

// Small helpers to avoid extra imports.
@Composable
private fun Modifier.verticalScrollCompat(): Modifier =
    this.then(Modifier.verticalScroll(rememberScrollState()))
