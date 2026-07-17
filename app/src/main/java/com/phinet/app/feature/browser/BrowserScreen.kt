package com.phinet.app.feature.browser

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.phinet.app.node.CtlClient
import kotlinx.coroutines.launch

/**
 * ΦNET browser. The home screen is a modern search page (logo + centered
 * search). Open-web URLs load in a WebView; a `<id>.phinet` address is
 * fetched through the local node over a circuit. A Tor-style circuit panel
 * shows identity + relays and can rotate to a new identity.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(onOpenSettings: () -> Unit = {}) {
    var bar by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf<Mode>(Mode.Start) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progress by remember { mutableStateOf(0) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fsCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var showCircuit by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // Session history of .phinet pages; `hi` is where we are within it.
    val hist = remember { mutableStateListOf<Site>() }
    var hi by remember { mutableStateOf(-1) }
    val marks = remember { mutableStateListOf<Mark>().also { it.addAll(loadMarks(ctx)) } }
    val here: Site? = hist.getOrNull(hi)

    // Fetching a .phinet page means a descriptor lookup, two circuits and a
    // rendezvous — tens of seconds. Naming the stage keeps that from reading
    // as a hang.
    fun load(site: Site, push: Boolean) {
        mode = Mode.Loading()
        scope.launch {
            val slow = launch {
                delay(2500)
                mode = Mode.Loading("Building circuits and rendezvousing… this can take ~30s")
            }
            val page = CtlClient.hsFetch(site.hsId, site.path)
            slow.cancel()
            if (page == null) {
                mode = Mode.Error("Couldn't reach ${site.hsId.take(12)}….phinet — is the node " +
                                  "connected, and are there enough relays for a circuit?")
                return@launch
            }
            if (push) {
                while (hist.size > hi + 1) hist.removeAt(hist.size - 1)
                hist.add(site); hi = hist.size - 1
            }
            bar = site.hsId + ".phinet" + (if (site.path == "/") "" else site.path)
            mode = Mode.Phinet(site.hsId, String(page.body), page.contentType)
        }
    }

    fun go(input: String) {
        val s = input.trim()
        when {
            s.isEmpty() || s == "about:phinet" -> mode = Mode.Start
            s.contains(".phinet") -> {
                val (hs, path) = splitPhinet(s)
                load(Site(hs, path), push = true)
            }
            else -> mode = Mode.Web(normalizeWeb(s))
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val m = mode) {
            is Mode.Start -> HomePage(
                query = bar, onQuery = { bar = it }, onGo = { go(bar) },
                onSettings = onOpenSettings, onCircuit = { showCircuit = true },
                marks = marks,
                onOpenMark = { m -> load(Site(m.hsId, m.path), push = true) },
                onDropMark = { m -> marks.removeAll { it.hsId == m.hsId && it.path == m.path }
                                    saveMarks(ctx, marks) },
            )
            else -> Column(Modifier.fillMaxSize()) {
                // Two rows: the address and its network badge on top, the
                // controls beneath. Six icon buttons plus a badge plus a text
                // field will not fit across a phone — everything ends up
                // squeezed to unreadable slivers.
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                    Column(Modifier.fillMaxWidth().statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            // The two networks look identical in an address
                            // bar, but only one of them is anonymous.
                            when (mode) {
                                is Mode.Phinet -> NetBadge("ΦNET · HS", MaterialTheme.colorScheme.tertiary)
                                is Mode.Web    -> NetBadge("clearnet", MaterialTheme.colorScheme.error)
                                else -> {}
                            }
                            OutlinedTextField(
                                value = bar, onValueChange = { bar = it },
                                placeholder = { Text("Search or <id>.phinet") },
                                singleLine = true, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { go(bar) }),
                            )
                        }
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (hi > 0) { hi -= 1; load(hist[hi], push = false) } },
                                enabled = hi > 0) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                            IconButton(onClick = { if (hi < hist.size - 1) { hi += 1; load(hist[hi], push = false) } },
                                enabled = hi < hist.size - 1) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward")
                            }
                            IconButton(onClick = { here?.let { load(it, push = false) } }, enabled = here != null) {
                                Icon(Icons.Default.Refresh, "Reload")
                            }
                            IconButton(onClick = { bar = ""; mode = Mode.Start }) {
                                Icon(Icons.Default.Home, "Home")
                            }
                            IconButton(onClick = {
                                here?.let { cur ->
                                    val marked = marks.any { it.hsId == cur.hsId && it.path == cur.path }
                                    if (marked) marks.removeAll { it.hsId == cur.hsId && it.path == cur.path }
                                    else marks.add(Mark(cur.hsId, cur.path, cur.hsId.take(12) + "…"))
                                    saveMarks(ctx, marks)
                                }
                            }, enabled = here != null) {
                                val marked = here?.let { cur ->
                                    marks.any { it.hsId == cur.hsId && it.path == cur.path }
                                } ?: false
                                Icon(if (marked) Icons.Default.Star else Icons.Default.StarBorder,
                                    if (marked) "Remove bookmark" else "Bookmark",
                                    tint = if (marked) MaterialTheme.colorScheme.tertiary
                                           else LocalContentColor.current)
                            }
                            IconButton(onClick = { showCircuit = true }) {
                                Icon(Icons.Default.Hub, "Circuit")
                            }
                        }
                    }
                }
                if (mode is Mode.Loading || progress in 1..99)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (m) {
                        is Mode.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text(m.stage, style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 40.dp))
                            }
                        }
                        is Mode.Error -> ErrorPage(m.message)
                        is Mode.Phinet -> AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                webViewClient = PhinetWebViewClient(
                                    hsId = { (mode as? Mode.Phinet)?.hsId ?: m.hsId },
                                    onNavigate = { hs, path -> load(Site(hs, path), push = true) },
                                )
                                loadDataWithBaseURL("phinet://site/", m.html, m.contentType, "UTF-8", null)
                                webView = this
                            }
                        }, update = { it.loadDataWithBaseURL("phinet://site/", m.html, m.contentType, "UTF-8", null) })
                        is Mode.Web -> AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = false
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                webViewClient = WebViewClient()
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(v: WebView?, p: Int) { progress = p }
                                    override fun onShowCustomView(view: View?, cb: CustomViewCallback?) {
                                        fullscreenView = view; fsCallback = cb
                                    }
                                    override fun onHideCustomView() {
                                        fullscreenView = null; fsCallback?.onCustomViewHidden(); fsCallback = null
                                    }
                                }
                                webView = this
                                loadUrl(m.url)
                            }
                        }, update = { if (it.url != m.url) it.loadUrl(m.url) })
                        else -> {}
                    }
                    fullscreenView?.let { fv -> AndroidView(modifier = Modifier.fillMaxSize(), factory = { fv }) }
                }
            }
        }
    }

    if (showCircuit) CircuitSheet(onDismiss = { showCircuit = false })
}

@Composable
private fun HomePage(
    query: String, onQuery: (String) -> Unit, onGo: () -> Unit,
    onSettings: () -> Unit, onCircuit: () -> Unit,
    marks: List<Mark> = emptyList(),
    onOpenMark: (Mark) -> Unit = {},
    onDropMark: (Mark) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // top-right utilities
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onCircuit) {
                Icon(Icons.Default.Hub, "Circuit", tint = MaterialTheme.colorScheme.onBackground)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp).padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Logo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Φ", fontSize = 64.sp, fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary)
                Text("NET", fontSize = 56.sp, fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground)
            }
            Text("private overlay browser",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(36.dp))
            // Search bar — pill, elevated
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(30.dp),
                tonalElevation = 3.dp, shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextField(
                        value = query, onValueChange = onQuery,
                        placeholder = { Text("Search the web or open <id>.phinet") },
                        singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onGo() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("duckduckgo.com" to "Search", "news.ycombinator.com" to "HN").forEach { (u, label) ->
                    AssistChip(onClick = { onQuery(u); onGo() }, label = { Text(label) })
                }
            }
            // Saved sites. A .phinet address is 64 hex characters — nobody is
            // typing one of those twice.
            if (marks.isNotEmpty()) {
                Spacer(Modifier.height(28.dp))
                Text("SAVED .PHINET SITES", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                marks.forEach { m ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp), tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    ) {
                        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { onOpenMark(m) }) {
                                Text(m.title, style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(m.hsId.take(16) + "…phinet" + (if (m.path == "/") "" else m.path),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { onDropMark(m) }) {
                                Icon(Icons.Default.Close, "Remove",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CircuitSheet(onDismiss: () -> Unit) {
    var info by remember { mutableStateOf<CtlClient.CircuitInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var rotating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() { loading = true; info = CtlClient.circuitInfo(); loading = false }
    LaunchedEffect(Unit) { load() }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp).fillMaxWidth()) {
            Text("Your ΦNET node", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text("Your traffic always enters the network through your pinned entry " +
                 "guards. From a guard, each circuit is built onward through a middle " +
                 "and exit relay. Request a new identity to re-pin fresh guards.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            when {
                loading -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
                info == null -> Text("Couldn't read circuit — is the node running?",
                    color = MaterialTheme.colorScheme.error)
                else -> {
                    val ci = info!!
                    Hop("You", ci.nodeId, "this device", isYou = true)
                    // The actual circuit path, if one is built: You → Guard → Middle → Exit.
                    if (ci.path.isNotEmpty()) {
                        ci.path.forEach { h ->
                            Connector()
                            Hop(roleLabel(h.role), h.nodeId, h.host, indent = true)
                        }
                    } else {
                        Connector()
                        Text("No circuit built yet — need more independent relays to form a path.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 24.dp))
                    }
                    // Just the live path: the guard set and the full relay
                    // list were noise next to the circuit actually in use.
                    if (ci.relays.isNotEmpty() && ci.relays.size <= 3) {
                        Spacer(Modifier.height(14.dp))
                        Text("Only ${ci.relays.size} relays are online. Anonymity grows " +
                             "with the network — run a relay, or ask others to.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    scope.launch {
                        rotating = true
                        CtlClient.newIdentity()
                        kotlinx.coroutines.delay(700)
                        load(); rotating = false
                    }
                },
                enabled = !rotating && !loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (rotating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("New identity") }
            }
        }
    }
}

private fun roleLabel(role: String): String = when (role) {
    "guard"  -> "Guard (entry)"
    "middle" -> "Middle"
    "exit"   -> "Exit"
    "single" -> "Relay (single hop)"
    else     -> role
}

@Composable
private fun Hop(label: String, nodeId: String, host: String, isYou: Boolean = false, indent: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).padding(start = if (indent) 24.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(
            Brush.linearGradient(
                if (isYou) listOf(Color(0xFF22C55E), Color(0xFF16A34A))
                else listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(if (host.isNotEmpty()) "$host · ${nodeId.take(12)}…" else "${nodeId.take(16)}…",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Connector() {
    Box(Modifier.padding(start = 5.dp).width(2.dp).height(14.dp)
        .background(MaterialTheme.colorScheme.outline))
}

/** A page on a hidden service: which service, and which path within it. */
private data class Site(val hsId: String, val path: String)

/** A saved .phinet location. */
private data class Mark(val hsId: String, val path: String, val title: String)

private const val MARKS_PREFS = "phinet.browser"
private const val MARKS_KEY   = "bookmarks"

private fun loadMarks(ctx: android.content.Context): List<Mark> =
    ctx.getSharedPreferences(MARKS_PREFS, android.content.Context.MODE_PRIVATE)
        .getStringSet(MARKS_KEY, emptySet())!!
        .mapNotNull { line ->
            // "hsId\u0000path\u0000title"
            val p = line.split('\u0000')
            if (p.size == 3) Mark(p[0], p[1], p[2]) else null
        }
        .sortedBy { it.title }

private fun saveMarks(ctx: android.content.Context, marks: List<Mark>) {
    ctx.getSharedPreferences(MARKS_PREFS, android.content.Context.MODE_PRIVATE)
        .edit()
        .putStringSet(MARKS_KEY, marks.map { "${it.hsId}\u0000${it.path}\u0000${it.title}" }.toSet())
        .apply()
}

private sealed interface Mode {
    data object Start : Mode
    data class Loading(val stage: String = "Resolving the address…") : Mode
    data class Web(val url: String) : Mode
    data class Phinet(val hsId: String, val html: String, val contentType: String) : Mode
    data class Error(val message: String) : Mode
}

private fun splitPhinet(input: String): Pair<String, String> {
    val s = input.removePrefix("http://").removePrefix("https://").removePrefix("phinet://")
    val host = s.substringBefore('/')
    val path = s.substring(host.length).ifEmpty { "/" }
    return host.removeSuffix(".phinet") to path
}

private fun normalizeWeb(s: String): String = when {
    s.startsWith("http://") || s.startsWith("https://") -> s
    s.contains(".") && !s.contains(" ") -> "https://$s"
    else -> "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(s, "UTF-8")
}

@Composable
private fun ErrorPage(message: String) {
    Column(Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Couldn't load", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/**
 * Serves a hidden service's sub-resources to the WebView.
 *
 * The page is loaded with a `phinet://site/` base URL, so the HTML's relative
 * references (`/style.css`, `/logo.png`, `<a href="/how.html">`) resolve to
 * `phinet://site/...`. Nothing answered those before, which is why pages
 * rendered unstyled and links dead-ended. Rather than rewriting the HTML, we
 * intercept each request and fetch it through the local node, so the WebView
 * loads the site much as it would any ordinary website — except every byte
 * arrives over an anonymous circuit.
 */
private class PhinetWebViewClient(
    private val hsId: () -> String,
    private val onNavigate: (String, String) -> Unit,
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?, request: WebResourceRequest?,
    ): WebResourceResponse? {
        val url = request?.url ?: return null
        // Returning null means "WebView, fetch this yourself" — a direct
        // clearnet connection from a page the reader loaded over an anonymous
        // circuit. A hidden service could fetch("https://evil/?ip") and learn
        // exactly who its visitor is. Nothing on a .phinet page has any
        // business leaving the network: refuse it.
        if (url.scheme != "phinet") {
            android.util.Log.w("phinet", "blocked off-network request from a .phinet page: ${url.scheme}://${url.host}")
            return WebResourceResponse(
                "text/plain", "utf-8", 403, "Blocked",
                emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
        // Document navigations are handled by shouldOverrideUrlLoading so the
        // address bar and history stay in step; only sub-resources here.
        if (request.isForMainFrame) return null

        val path = (url.path ?: "/").ifEmpty { "/" }
        // shouldInterceptRequest runs off the UI thread, so blocking here is
        // correct — the WebView expects a response, not a promise.
        val page = runBlocking { runCatching { CtlClient.hsFetch(hsId(), path) }.getOrNull() }
            ?: return WebResourceResponse(
                "text/plain", "utf-8", 504, "Unreachable", emptyMap(), ByteArrayInputStream(ByteArray(0)))

        val mime = page.contentType.substringBefore(';').trim().ifEmpty { "application/octet-stream" }
        val charset = page.contentType.substringAfter("charset=", "").ifEmpty { "utf-8" }
        return WebResourceResponse(
            mime, charset, page.status, if (page.status == 200) "OK" else "Error",
            mapOf("Access-Control-Allow-Origin" to "*"),
            ByteArrayInputStream(page.body),
        )
    }

    override fun shouldOverrideUrlLoading(
        view: WebView?, request: WebResourceRequest?,
    ): Boolean {
        val url = request?.url ?: return false
        return when (url.scheme) {
            // In-site link: re-enter through go() so the address bar updates
            // and the fetch goes over a fresh circuit.
            "phinet" -> { onNavigate(hsId(), (url.path ?: "/").ifEmpty { "/" }); true }
            // A .phinet site linking to the clearnet would silently leave the
            // network; refuse rather than deanonymise the reader by surprise.
            "http", "https" -> true
            else -> false
        }
    }
}


/** Address-bar pill naming the network the current page came from. */
@Composable
private fun NetBadge(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.padding(end = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
