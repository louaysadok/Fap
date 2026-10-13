package com.fap.app.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fap.app.di.Graph
import com.fap.app.di.ViewerItem
import com.fap.core.FapelloClient
import com.fap.core.MediaKind
import java.io.File

/**
 * Full screen media viewer. Swipe left/right to move through the set, pinch to
 * zoom (double tap to reset), and download whatever is on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ViewerScreen(
    onBack: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onDownload: (ViewerItem) -> Unit
) {
    val items = Graph.viewer.items
    var index by remember { mutableIntStateOf(Graph.viewer.index.coerceIn(0, (items.size - 1).coerceAtLeast(0))) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val title = Graph.viewer.title

    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Nothing to show", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    fun go(delta: Int) {
        val next = (index + delta).coerceIn(0, items.size - 1)
        if (next != index) {
            index = next
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Zoomed in: pinch / pan. Zoomed out: swipe between items.
            .pointerInput(scale > 1.01f) {
                if (scale > 1.01f) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.5f, 8f)
                        offset += pan
                    }
                } else {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (dragAmount < -60f) go(1) else if (dragAmount > 60f) go(-1)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    scale = 1f
                    offset = Offset.Zero
                })
            }
    ) {
        val item = items[index]
        ViewerImage(
            item = item,
            scale = scale,
            offset = offset,
            modifier = Modifier.fillMaxSize()
        )

        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            color = Color.Black.copy(alpha = 0.45f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, maxLines = 1, style = MaterialTheme.typography.titleSmall)
                    Text("${index + 1} / ${items.size}", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { onDownload(item) }) {
                    Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
                }
            }
        }

        if (item.kind == MediaKind.VIDEO) {
            Button(modifier = Modifier.align(Alignment.Center), onClick = { onPlayVideo(item.source) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text(" Play video")
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { go(-1) }, enabled = index > 0) { Text("Prev") }
            Text(item.label, color = Color.White, style = MaterialTheme.typography.labelSmall)
            Button(onClick = { go(1) }, enabled = index < items.size - 1) { Text("Next") }
        }
    }
}

@Composable
private fun ViewerImage(
    item: ViewerItem,
    scale: Float,
    offset: Offset,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val model = remember(item.source) {
        if (item.source.startsWith("/") || item.source.startsWith("file://")) {
            ImageRequest.Builder(context)
                .data(File(item.source.removePrefix("file://")))
                .crossfade(true)
                .build()
        } else {
            ImageRequest.Builder(context)
                .data(item.source)
                .crossfade(true)
                .addHeader("User-Agent", FapelloClient.DEFAULT_USER_AGENT)
                .addHeader("Referer", "${Graph.client().base}/")
                .build()
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = model,
            contentDescription = item.label,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
        )
    }
}
