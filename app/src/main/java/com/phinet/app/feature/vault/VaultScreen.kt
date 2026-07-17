package com.phinet.app.feature.vault

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.UploadFile
import com.phinet.app.ui.components.Avatar
import com.phinet.app.ui.components.BrandMark
import com.phinet.app.ui.components.PhinetTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(onOpenSettings: () -> Unit, vm: VaultViewModel = viewModel()) {
    val ui by vm.ui.collectAsState()
    val shareTargets by vm.shareTargets.collectAsState()
    val toast by vm.toast.collectAsState()
    val openFile by vm.open.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }
    var shareItem by remember { mutableStateOf<VaultItem?>(null) }
    var showPanic by remember { mutableStateOf(false) }
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) vm.importFile(uri) }

    LaunchedEffect(toast) { toast?.let { snackbar.showSnackbar(it); vm.clearToast() } }

    Scaffold(
        topBar = {
            PhinetTopBar(
                title = "ΦNET",
                subtitle = "vault",
                actions = {
                    if (ui.unlocked) {
                        IconButton(onClick = { pickFile.launch("*/*") }) {
                            Icon(Icons.Default.UploadFile, contentDescription = "Import file")
                        }
                        IconButton(onClick = { vm.lock() }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock")
                        }
                    }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
                )
        },
        floatingActionButton = {
            if (ui.unlocked) ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Add") })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                !ui.unlocked -> UnlockGate(ui, onCreate = vm::create, onUnlock = vm::unlock, onPanic = { showPanic = true })
                ui.items.isEmpty() -> EmptyVault()
                else -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ui.items, key = { it.id }) { item ->
                        VaultCard(item, onOpen = { if (item.kind == VaultKind.FILE) vm.openFile(item) },
                            onShare = { vm.loadShareTargets(); shareItem = item }, onDelete = { vm.delete(item.id) })
                    }
                }
            }
        }
    }

    if (showAdd) AddItemSheet(onDismiss = { showAdd = false }) { k, t, c -> vm.addText(k, t, c); showAdd = false }
    if (showPanic) AlertDialog(
        onDismissRequest = { showPanic = false },
        title = { Text("Wipe the vault?") },
        text = { Text("This permanently destroys all vault contents on this device. It cannot be undone.") },
        confirmButton = { TextButton(onClick = { showPanic = false; vm.panicWipe() }) {
            Text("Wipe", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { showPanic = false }) { Text("Cancel") } },
    )
    openFile?.let { of -> SecureViewer(of, onClose = { vm.closeFile() }) }
    shareItem?.let { item ->
        ShareSheet(item, shareTargets, onDismiss = { shareItem = null }) { nodeId -> vm.share(item, nodeId); shareItem = null }
    }
}

@Composable
private fun UnlockGate(ui: VaultUi, onCreate: (String) -> Unit, onUnlock: (String) -> Unit, onPanic: () -> Unit) {
    var pass by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(if (ui.exists) "Unlock your vault" else "Create your vault",
            style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text("Encrypted with XChaCha20-Poly1305; the key is derived from your passphrase (Argon2id) and never leaves this device.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text("Passphrase") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            isError = ui.error != null, supportingText = { ui.error?.let { Text(it) } },
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (ui.exists) onUnlock(pass) else onCreate(pass) },
            enabled = !ui.busy, modifier = Modifier.fillMaxWidth(),
        ) {
            if (ui.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(if (ui.exists) "Unlock" else "Create vault")
        }
        if (ui.exists) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onPanic) {
                Text("Emergency wipe", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun EmptyVault() = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Text("Vault unlocked", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(6.dp))
        Text("Add links, notes, and secrets — then share any item to a com contact over ΦNET.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun iconFor(kind: VaultKind): ImageVector = when (kind) {
    VaultKind.LINK -> Icons.Default.Link
    VaultKind.NOTE -> Icons.Default.Notes
    VaultKind.SECRET -> Icons.Default.Key
    VaultKind.FILE -> Icons.Default.InsertDriveFile
}

@Composable
private fun VaultCard(item: VaultItem, onOpen: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    ElevatedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(iconFor(item.kind), null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title.ifEmpty { "(untitled)" }, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(if (item.kind == VaultKind.SECRET) "••••••••" else item.content,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Share over ΦNET") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemSheet(onDismiss: () -> Unit, onAdd: (VaultKind, String, String) -> Unit) {
    val kinds = listOf(VaultKind.LINK, VaultKind.NOTE, VaultKind.SECRET)
    var kind by remember { mutableStateOf(VaultKind.LINK) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text("Add to vault", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow {
                kinds.forEachIndexed { i, k ->
                    SegmentedButton(selected = kind == k, onClick = { kind = k },
                        shape = SegmentedButtonDefaults.itemShape(i, kinds.size)) {
                        Text(k.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(content, { content = it },
                label = { Text(when (kind) { VaultKind.LINK -> "URL"; VaultKind.NOTE -> "Note"; else -> "Secret" }) },
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Button(onClick = { if (content.isNotBlank()) onAdd(kind, title, content) }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(item: VaultItem, targets: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text("Share “${item.title.ifEmpty { item.kind.name.lowercase() }}”",
                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text("over ΦNET — sealed to the recipient by com", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (targets.isEmpty()) Text("No contacts yet. Open a chat in com first (add someone by their address), then share.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else targets.forEach { nodeId ->
                Row(Modifier.fillMaxWidth().clickable { onPick(nodeId) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Avatar(nodeId, 36); Spacer(Modifier.width(12.dp))
                    Text(nodeId.take(16) + "…", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
