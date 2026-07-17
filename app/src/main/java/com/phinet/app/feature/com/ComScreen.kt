package com.phinet.app.feature.com

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phinet.app.data.Peer
import com.phinet.app.ui.components.Avatar
import com.phinet.app.ui.components.BrandMark
import com.phinet.app.ui.components.PhinetTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComScreen(
    onOpenThread: (String) -> Unit,
    onOpenGroups: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: ComViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var showNew by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    Scaffold(
        topBar = {
            PhinetTopBar(
                title = "ΦNET",
                subtitle = "com",
                actions = {
                    IconButton(onClick = onOpenGroups) {
                        Icon(Icons.Default.Groups, contentDescription = "Groups")
                    }
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNew = true },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("New chat") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.threads.isEmpty() && !state.loading) {
                EmptyState()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.threads, key = { it }) { peer ->
                        ThreadCard(peer = peer, onClick = { onOpenThread(peer) })
                    }
                }
            }
        }
    }

    if (showNew) {
        NewChatSheet(
            peers = state.peers,
            myAddress = state.myAddress,
            onDismiss = { showNew = false },
            onPickPeer = { showNew = false; onOpenThread(it) },
            onAddAddress = { addr ->
                vm.addContact(addr) { nid -> showNew = false; onOpenThread(nid) }
            },
        )
    }
}

@Composable
private fun ThreadCard(peer: String, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(peer, size = 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    peer.take(16) + "…",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "tap to open conversation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No conversations yet", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap New chat to add a contact by the address they shared with you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewChatSheet(
    peers: List<Peer>,
    myAddress: String,
    onDismiss: () -> Unit,
    onPickPeer: (String) -> Unit,
    onAddAddress: (String) -> Unit,
) {
    val clip = LocalClipboardManager.current
    var addr by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("New chat", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))

            if (peers.isNotEmpty()) {
                Text("Linked peers", style = MaterialTheme.typography.labelSmall)
                peers.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPickPeer(p.node_id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(p.node_id, size = 36)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(p.host.ifEmpty { p.node_id.take(12) },
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(p.node_id.take(24) + "…",
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = addr,
                onValueChange = { addr = it },
                label = { Text("Paste a phi: address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions.Default,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { if (addr.isNotBlank()) onAddAddress(addr.trim()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add contact") }

            Spacer(Modifier.height(16.dp))
            Text(
                "There is no public list of users — you can only reach people whose address you hold.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (myAddress.isNotEmpty()) {
                Text("Your address", style = MaterialTheme.typography.labelSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        myAddress, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { clip.setText(AnnotatedString(myAddress)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy address")
                    }
                }
            }
        }
    }
}
