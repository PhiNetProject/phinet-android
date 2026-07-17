package com.phinet.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.net.Uri
import com.phinet.app.PhinetApp
import com.phinet.app.feature.browser.BrowserScreen
import com.phinet.app.feature.com.ChatScreen
import com.phinet.app.feature.com.ComScreen
import com.phinet.app.feature.com.GroupChatScreen
import com.phinet.app.feature.com.GroupsScreen
import com.phinet.app.feature.settings.SettingsScreen
import com.phinet.app.feature.vault.VaultScreen
import com.phinet.app.ui.components.AppBackground
import com.phinet.app.ui.components.BarTab
import com.phinet.app.ui.components.PhinetBottomBar
import com.phinet.app.ui.theme.ThemeMode

private val tabs = listOf(
    BarTab("com", "Com", Icons.Outlined.Chat),
    BarTab("browser", "Browser", Icons.Outlined.Public),
    BarTab("vault", "Vault", Icons.Outlined.Lock),
)

@Composable
fun PhinetRoot() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = current in tabs.map { it.route }

    val ctx = LocalContext.current
    val settings = remember { (ctx.applicationContext as PhinetApp).settings }
    val mode by settings.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val dark = when (mode) { ThemeMode.DARK -> true; ThemeMode.LIGHT -> false; else -> isSystemInDarkTheme() }

    // The browser has its own homepage look; everyone else gets the wallpaper.
    val onBrowser = current == "browser"
    val openSettings: () -> Unit = { nav.navigate("settings") }

    AppBackground(dark = dark, enabled = !onBrowser) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                if (showBar) PhinetBottomBar(tabs = tabs, current = current) { route ->
                    nav.navigate(route) { popUpTo("com"); launchSingleTop = true }
                }
            },
        ) { pad ->
            NavHost(nav, startDestination = "com", modifier = Modifier.padding(pad)) {
                composable("com") {
                    ComScreen(
                        onOpenThread = { peer -> nav.navigate("chat/$peer") },
                        onOpenGroups = { nav.navigate("groups") },
                        onOpenSettings = openSettings,
                    )
                }
                composable("chat/{peer}") { e ->
                    ChatScreen(peer = e.arguments?.getString("peer").orEmpty(), onBack = { nav.popBackStack() })
                }
                composable("browser") { BrowserScreen(onOpenSettings = openSettings) }
                composable("vault") { VaultScreen(onOpenSettings = openSettings) }
                composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                composable("groups") {
                    GroupsScreen(
                        onBack = { nav.popBackStack() },
                        onOpenGroup = { g ->
                            nav.navigate("group/${g.group_id}/${g.thread_id}/${Uri.encode(g.name)}")
                        },
                    )
                }
                composable("group/{gid}/{tid}/{name}") { e ->
                    GroupChatScreen(
                        groupId = e.arguments?.getString("gid").orEmpty(),
                        threadId = e.arguments?.getString("tid").orEmpty(),
                        name = Uri.decode(e.arguments?.getString("name").orEmpty()),
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}
