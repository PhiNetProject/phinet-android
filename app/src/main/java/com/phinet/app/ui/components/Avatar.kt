package com.phinet.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Deterministic identicon-ish avatar derived from a node-id hex string. */
@Composable
fun Avatar(id: String, size: Int = 40) {
    val h = abs(id.hashCode())
    val c1 = Color(0xFF000000L or (0x00FFFFFFL and (h * 2654435761L)))
    val c2 = Color(0xFF000000L or (0x00FFFFFFL and (h.toLong() * 40503L + 0x9E3779B9L)))
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(c1.copy(alpha = 0.9f), c2.copy(alpha = 0.9f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = id.take(2).uppercase(),
            color = Color.White,
            fontSize = (size / 2.8).sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}
