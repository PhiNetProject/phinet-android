package com.phinet.app.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File

/**
 * The chat wallpaper behind the message list (Telegram/WhatsApp style).
 * Built-in procedural options render as gradients; "custom:<path>" loads an
 * uploaded image from app storage.
 */
@Composable
fun ChatBackground(spec: String, dark: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        when {
            spec.startsWith("custom:") -> {
                val path = spec.removePrefix("custom:")
                val bmp = remember(path) {
                    runCatching { BitmapFactory.decodeFile(File(path).absolutePath) }.getOrNull()
                }
                if (bmp != null) {
                    Image(bmp.asImageBitmap(), null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize().background(builtin("aurora", dark)))
                }
            }
            else -> Box(Modifier.fillMaxSize().background(builtin(spec, dark)))
        }
        content()
    }
}

/** Named procedural gradient backgrounds. */
fun builtin(id: String, dark: Boolean): Brush = when (id) {
    "mesh" -> if (dark) Brush.linearGradient(
        listOf(Color(0xFF0B1220), Color(0xFF15213A), Color(0xFF0E1B2E)),
        start = Offset(0f, 0f), end = Offset(900f, 1600f),
    ) else Brush.linearGradient(
        listOf(Color(0xFFEFF3FF), Color(0xFFF7F0FF), Color(0xFFEAF6FF)),
    )
    "plain" -> Brush.linearGradient(
        if (dark) listOf(Color(0xFF0B1220), Color(0xFF0B1220))
        else listOf(Color(0xFFF6F7FB), Color(0xFFF6F7FB)),
    )
    // "aurora" default
    else -> if (dark) Brush.linearGradient(
        listOf(Color(0xFF0B1220), Color(0xFF1E1B4B), Color(0xFF0B2A3A)),
        start = Offset(0f, 0f), end = Offset(1000f, 1800f),
    ) else Brush.linearGradient(
        listOf(Color(0xFFEDE9FE), Color(0xFFDBEAFE), Color(0xFFCFFAFE)),
        start = Offset(0f, 0f), end = Offset(1000f, 1800f),
    )
}

val BUILTIN_BACKGROUNDS = listOf("aurora", "mesh", "plain")
