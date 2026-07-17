package com.phinet.app.feature.vault

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.ExperimentalMaterial3Api
import java.io.File

/**
 * Views a decrypted vault file by PATH (never the whole file in memory):
 * images are downsampled, video/audio stream from disk via the platform
 * player, and PDFs render one page at a time. The temp file is wiped by the
 * ViewModel when this closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureViewer(open: OpenFile, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(open.item.fileName.ifEmpty { open.item.title }, maxLines = 1) },
                    navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val mime = open.item.mime
                    when {
                        mime.startsWith("image/") -> ImageView(open.file)
                        mime.startsWith("video/") || mime.startsWith("audio/") -> MediaView(open.file)
                        mime == "application/pdf" -> PdfView(open.file)
                        else -> Text("No preview for ${mime.ifEmpty { "this file type" }}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageView(file: File) {
    // Downsample so huge images don't OOM.
    val bmp: ImageBitmap? = remember(file.path) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        val target = 2048
        while (bounds.outWidth / sample > target || bounds.outHeight / sample > target) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap()
    }
    if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    else Text("Couldn't decode image")
}

@Composable
private fun MediaView(file: File) {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoPath(file.absolutePath)
                setMediaController(android.widget.MediaController(ctx).also { it.setAnchorView(this) })
                setOnPreparedListener { it.isLooping = false; start() }
            }
        },
    )
}

@Composable
private fun PdfView(file: File) {
    // Render page-by-page; only the current page's bitmap is in memory.
    val pages = remember(file.path) {
        runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd)
        }.getOrNull()
    }
    if (pages == null) { Text("Couldn't open PDF"); return }
    LazyColumn(Modifier.fillMaxSize()) {
        items(pages.pageCount) { i ->
            val bmp = remember(i) {
                pages.openPage(i).use { pg ->
                    val bm = android.graphics.Bitmap.createBitmap(pg.width, pg.height, android.graphics.Bitmap.Config.ARGB_8888)
                    pg.render(bm, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bm.asImageBitmap()
                }
            }
            Image(bmp, null, Modifier.fillMaxWidth().padding(4.dp), contentScale = ContentScale.FillWidth)
        }
    }
    DisposableEffect(Unit) { onDispose { runCatching { pages.close() } } }
}
