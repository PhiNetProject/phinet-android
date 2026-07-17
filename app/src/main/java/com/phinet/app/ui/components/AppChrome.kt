package com.phinet.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phinet.app.R

// White floating bars (same in light & dark, per design) with dark content.
private val BarWhite = Color(0xFFFFFFFF)
private val BarContent = Color(0xFF0F172A)
private val BarMuted = Color(0xFF64748B)

/** Theme-aware full-screen background image (light vs dark). When [enabled]
 *  is false (e.g. the browser), falls back to the plain theme background. */
@Composable
fun AppBackground(dark: Boolean, enabled: Boolean = true, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (enabled) {
            Image(
                painter = painterResource(if (dark) R.drawable.bg_dark else R.drawable.bg_light),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        }
        content()
    }
}

/** A white, rounded, shadowed top bar that floats over the background. */
@Composable
fun PhinetTopBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Surface(
        color = BarWhite,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(10.dp, RoundedCornerShape(22.dp), clip = false)
            .clip(RoundedCornerShape(22.dp)),
    ) {
        CompositionLocalProvider(LocalContentColor provides BarContent) {
        Row(
            Modifier.height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigationIcon != null) { navigationIcon() } else Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f).padding(start = 6.dp)) {
                Text(title, color = BarContent, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge, maxLines = 1)
                if (subtitle != null) Text(subtitle, color = BarMuted,
                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            if (actions != null) Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        }
    }
}

data class BarTab(val route: String, val label: String, val icon: ImageVector)

/** A white, rounded, shadowed bottom navigation bar that floats. */
@Composable
fun PhinetBottomBar(tabs: List<BarTab>, current: String?, onSelect: (String) -> Unit) {
    Surface(
        color = BarWhite,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .shadow(14.dp, RoundedCornerShape(28.dp), clip = false)
            .clip(RoundedCornerShape(28.dp)),
    ) {
        Row(
            Modifier.height(64.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { t ->
                val selected = current == t.route
                val tint = if (selected) MaterialTheme.colorScheme.primary else BarMuted
                Column(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable { onSelect(t.route) }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, contentDescription = t.label, tint = tint)
                    Text(t.label, color = tint, style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
