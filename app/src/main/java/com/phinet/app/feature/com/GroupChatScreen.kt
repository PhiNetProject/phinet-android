package com.phinet.app.feature.com

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.phinet.app.data.ComMessage
import com.phinet.app.ui.components.Avatar
import com.phinet.app.ui.components.PhinetTopBar
import com.phinet.app.ui.theme.LocalChatColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: String, threadId: String, name: String,
    onBack: () -> Unit,
    vm: GroupsViewModel = viewModel(),
) {
    val messages by vm.messages.collectAsState()
    val contacts by vm.contacts.collectAsState()
    var draft by remember { mutableStateOf("") }
    var showInvite by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(threadId) { while (true) { vm.loadThread(threadId); delay(3000) } }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Scaffold(
        topBar = {
            PhinetTopBar(
                title = name,
                subtitle = "group",
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { vm.loadContacts(); showInvite = true }) {
                        Icon(Icons.Default.PersonAdd, "Invite")
                    }
                },
            )
        },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(messages) { m -> Bubble(m) }
            }
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField(value = draft, onValueChange = { draft = it },
                        placeholder = { Text("Message") }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp), maxLines = 4,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(onClick = {
                        val t = draft.trim(); if (t.isNotEmpty()) { vm.send(groupId, threadId, t); draft = "" }
                    }) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
                }
            }
        }
    }

    if (showInvite) ModalBottomSheet(onDismissRequest = { showInvite = false },
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Text("Invite to $name", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(12.dp))
            if (contacts.isEmpty()) Text("No contacts to invite.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else contacts.forEach { p ->
                Row(Modifier.fillMaxWidth().clickable { vm.invite(groupId, p.node_id); showInvite = false }
                    .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p.node_id, 36); Spacer(Modifier.width(12.dp))
                    Text(p.host.ifEmpty { p.node_id.take(16) + "…" }, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun Bubble(m: ComMessage) {
    val align = if (m.outgoing) Alignment.End else Alignment.Start
    val chat = LocalChatColors.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Box(Modifier.widthIn(max = 300.dp).clip(RoundedCornerShape(16.dp))
            .background(if (m.outgoing) chat.bubbleOut else chat.bubbleIn)
            .padding(horizontal = 14.dp, vertical = 9.dp)) {
            Text(m.body, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
