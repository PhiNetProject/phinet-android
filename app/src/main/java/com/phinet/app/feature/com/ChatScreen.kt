package com.phinet.app.feature.com

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import com.phinet.app.PhinetApp
import com.phinet.app.ui.components.ChatBackground
import com.phinet.app.ui.theme.ThemeMode
import com.phinet.app.vault.PhinetVaultBridge
import com.phinet.app.vault.VaultQuickSave
import com.phinet.app.vault.FileTransfer
import com.phinet.app.vault.FileReceiver
import com.phinet.app.vault.VaultSession
import com.phinet.app.vault.VaultStore
import java.io.File
import android.widget.Toast
import androidx.compose.material3.TextButton
import kotlinx.coroutines.flow.first
import com.phinet.app.data.ComMessage
import com.phinet.app.ui.components.Avatar
import com.phinet.app.ui.components.PhinetTopBar
import com.phinet.app.ui.theme.LocalChatColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peer: String,
    onBack: () -> Unit,
    vm: ComViewModel = viewModel(),
) {
    val messages by vm.messages.collectAsState()
    val hidden by vm.hidden.collectAsState()
    val ctx = LocalContext.current
    val receiver = remember { FileReceiver(ctx.cacheDir) }
    var receivedFiles by remember { mutableStateOf<List<Pair<com.phinet.app.feature.vault.VaultItem, File>>>(emptyList()) }
    LaunchedEffect(messages) {
        // Feed only inbound chunk messages; reassembly is idempotent.
        val done = mutableListOf<Pair<com.phinet.app.feature.vault.VaultItem, File>>()
        messages.filter { !it.outgoing && FileTransfer.isChunk(it.body) }.forEach { m ->
            receiver.offer(m.body)?.let { done += it }
        }
        if (done.isNotEmpty()) receivedFiles = (receivedFiles + done)
            .distinctBy { it.first.id }
    }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Load once, then poll for new messages while the thread is open.
    LaunchedEffect(peer) {
        while (true) { vm.loadThread(peer); delay(3000) }
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            PhinetTopBar(
                title = peer.take(14) + "…",
                subtitle = "com · sealed",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { pad ->
      val settings = remember { (ctx.applicationContext as PhinetApp).settings }
      val bg by settings.chatBackground.collectAsState(initial = "aurora")
      val mode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
      val dark = when (mode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; else -> isSystemInDarkTheme() }
      ChatBackground(spec = bg, dark = dark) {
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages.filter { !FileTransfer.isChunk(it.body) && !hidden.contains(vm.msgKey(it)) }) { m ->
                    MessageBubble(m, onLongPress = { vm.deleteMessage(peer, m) })
                }
                items(receivedFiles, key = { it.first.id }) { (item, file) ->
                    ReceivedFileCard(item.fileName, item.mime, file, ctx)
                }
            }
            Composer(
                value = draft,
                onValue = { draft = it },
                onSend = {
                    val t = draft.trim()
                    if (t.isNotEmpty()) { vm.send(peer, t); draft = "" }
                },
            )
        }
      }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(m: ComMessage, onLongPress: () -> Unit = {}) {
    val shared = PhinetVaultBridge.decodeShared(m.body)
    if (shared != null) { SharedVaultBubble(m.outgoing, shared); return }
    val align = if (m.outgoing) Alignment.End else Alignment.Start
    val chat = LocalChatColors.current
    val bg = if (m.outgoing) chat.bubbleOut else chat.bubbleIn
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 14.dp, vertical = 9.dp),
        ) {
            Text(m.body, color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Composer(value: String, onValue: (String) -> Unit, onSend: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = value,
                onValueChange = onValue,
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
            )
            Spacer(Modifier.width(8.dp))
            FilledIconButton(onClick = onSend) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}

@Composable
private fun SharedVaultBubble(outgoing: Boolean, item: com.phinet.app.feature.vault.VaultItem) {
    val align = if (outgoing) Alignment.End else Alignment.Start
    val chat = LocalChatColors.current
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (outgoing) chat.bubbleOut else chat.bubbleIn)
                .padding(14.dp),
        ) {
            Column {
                Text("◈ Shared from Vault · ${item.kind.name.lowercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(4.dp))
                Text(item.title.ifEmpty { "(untitled)" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(item.content, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!outgoing) TextButton(onClick = {
                    val ok = VaultQuickSave.save(ctx, item)
                    Toast.makeText(ctx, if (ok) "Saved to Vault" else "Unlock your Vault first",
                        Toast.LENGTH_SHORT).show()
                }) { Text("Save to Vault") }
            }
        }
    }
}

@Composable
private fun ReceivedFileCard(name: String, mime: String, file: java.io.File, ctx: android.content.Context) {
    val chat = LocalChatColors.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Box(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp))
            .background(chat.bubbleIn).padding(14.dp)) {
            Column {
                Text("\u25C8 Received file over \u03A6NET", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(4.dp))
                Text(name.ifEmpty { "file" }, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = {
                    val master = VaultSession.masterKey
                    if (master == null) { Toast.makeText(ctx, "Unlock your Vault first", Toast.LENGTH_SHORT).show() }
                    else {
                        val store = VaultStore(ctx)
                        val items = runCatching { store.loadIndex(master) }.getOrDefault(emptyList())
                        runCatching { store.importPlainFile(file, name, name, mime, items, master) }
                        Toast.makeText(ctx, "Saved to Vault", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Save to Vault") }
            }
        }
    }
}
